package io.github.volovatovta.docviewer.internal.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.pdf.LoadParams
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.annotation.RequiresApi
import androidx.core.graphics.createBitmap
import io.github.volovatovta.docviewer.core.CorruptedDocumentException
import io.github.volovatovta.docviewer.core.PasswordProtectedException
import io.github.volovatovta.docviewer.internal.PasswordUnsupportedException
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

internal class PdfPageSize(val width: Float, val height: Float)

// PdfRenderer allows one open page at a time and is not thread-safe, so every call runs on one thread.
internal class PdfDocument private constructor(
    private val descriptor: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
    val pageSizes: List<PdfPageSize>,
) : Closeable {

    private val executor = Executors.newSingleThreadExecutor { task ->
        Thread(task, THREAD_NAME).apply { priority = Thread.NORM_PRIORITY - 1 }
    }
    private val dispatcher = executor.asCoroutineDispatcher()

    @Volatile
    private var closed = false

    val pageCount: Int get() = pageSizes.size

    @get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.VANILLA_ICE_CREAM)
    val isSearchSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM

    // Renders the region [left, top, left + width, top + height] of the page scaled to pageWidthPx x pageHeightPx.
    suspend fun render(
        index: Int,
        pageWidthPx: Int,
        pageHeightPx: Int,
        left: Int = 0,
        top: Int = 0,
        width: Int = pageWidthPx,
        height: Int = pageHeightPx,
    ): Bitmap? = onPdfThread {
        if (width <= 0 || height <= 0) return@onPdfThread null
        val bitmap = createBitmap(width, height)
        bitmap.eraseColor(Color.WHITE)
        renderer.openPage(index).use { page ->
            val matrix = Matrix().apply {
                setScale(pageWidthPx.toFloat() / page.width, pageHeightPx.toFloat() / page.height)
                postTranslate(-left.toFloat(), -top.toFloat())
            }
            page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        }
        bitmap
    }

    // Match rectangles in PDF points; empty below Android 15.
    suspend fun search(index: Int, query: String): List<List<RectF>> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return emptyList()
        return onPdfThread { searchOnPage(index, query) }.orEmpty()
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun searchOnPage(index: Int, query: String): List<List<RectF>> =
        renderer.openPage(index).use { page -> page.searchText(query).map { match -> match.bounds } }

    private suspend fun <T> onPdfThread(block: () -> T): T? {
        if (closed) return null
        return try {
            withContext(dispatcher) { if (closed) null else block() }
        } catch (expected: RejectedExecutionException) {
            // The executor rejects work once the document has been closed.
            null
        } catch (expected: IllegalStateException) {
            // PdfRenderer throws this when the document was closed between the check and the call.
            if (closed) null else throw expected
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        executor.execute {
            runCatching { renderer.close() }
            runCatching { descriptor.close() }
        }
        executor.shutdown()
    }

    companion object {
        private const val THREAD_NAME = "docviewer-pdf"
        private const val OPEN_FAILED = "Cannot open PDF"
        private const val READ_FAILED = "Cannot read PDF pages"
        private const val NO_PAGES = "PDF has no pages"

        fun open(file: File, password: String?): PdfDocument {
            val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = descriptor.closeOnFailure { openRenderer(descriptor, password) }
            return descriptor.closeOnFailure {
                renderer.closeOnFailure { PdfDocument(descriptor, renderer, readPageSizes(renderer)) }
            }
        }

        private fun openRenderer(descriptor: ParcelFileDescriptor, password: String?): PdfRenderer =
            runCatching { createRenderer(descriptor, password) }
                .getOrElse { error -> throw openError(error, passwordGiven = password != null) }

        private fun createRenderer(descriptor: ParcelFileDescriptor, password: String?): PdfRenderer =
            if (password != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                PdfRenderer(descriptor, LoadParams.Builder().setPassword(password).build())
            } else {
                PdfRenderer(descriptor)
            }

        private fun openError(error: Throwable, passwordGiven: Boolean): Throwable = when (error) {
            is SecurityException -> passwordError(passwordGiven)
            is IOException, is RuntimeException -> CorruptedDocumentException(OPEN_FAILED, error)
            else -> error
        }

        private fun passwordError(passwordGiven: Boolean): Exception =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                PasswordProtectedException(wrongPassword = passwordGiven)
            } else {
                PasswordUnsupportedException(isPdf = true)
            }

        private fun readPageSizes(renderer: PdfRenderer): List<PdfPageSize> {
            if (renderer.pageCount == 0) throw CorruptedDocumentException(NO_PAGES)
            return runCatching { List(renderer.pageCount) { index -> renderer.pageSize(index) } }
                .getOrElse { error -> throw readError(error) }
        }

        private fun readError(error: Throwable): Throwable =
            if (error is RuntimeException) CorruptedDocumentException(READ_FAILED, error) else error

        private fun PdfRenderer.pageSize(index: Int): PdfPageSize = openPage(index).use { page ->
            PdfPageSize(page.width.toFloat().coerceAtLeast(1f), page.height.toFloat().coerceAtLeast(1f))
        }

        private inline fun <R> AutoCloseable.closeOnFailure(block: () -> R): R {
            var succeeded = false
            try {
                return block().also { succeeded = true }
            } finally {
                if (!succeeded) close()
            }
        }
    }
}
