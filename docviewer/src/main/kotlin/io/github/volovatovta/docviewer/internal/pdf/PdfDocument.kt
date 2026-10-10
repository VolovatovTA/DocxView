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
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

internal data class PdfPageSize(val width: Float, val height: Float)

// Every PdfRenderer call of every document runs on one shared thread, so platform pdfium is never used concurrently.
internal class PdfDocument private constructor(
    private val descriptor: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
    val pageCount: Int,
    // Opening reads only the first page: measuring thousands of pages up front took up to 25 s.
    val firstPageSize: PdfPageSize,
) : Closeable {

    @Volatile
    private var closed = false

    @get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.VANILLA_ICE_CREAM)
    val isSearchSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM

    // Renders the region [left, top, left + width, top + height] of the page scaled to pageWidthPx x pageHeightPx.
    // Out of memory, reclaim() frees what it can before one more try; still failing, the page stays blank for now.
    suspend fun render(
        index: Int,
        pageWidthPx: Int,
        pageHeightPx: Int,
        left: Int = 0,
        top: Int = 0,
        width: Int = pageWidthPx,
        height: Int = pageHeightPx,
        reclaim: () -> Unit = {},
    ): Bitmap? = onPdfThread {
        if (width <= 0 || height <= 0 || width.toLong() * height > MAX_BITMAP_PIXELS) return@onPdfThread null
        val bitmap = allocate(width, height) ?: reclaim().let { allocate(width, height) } ?: return@onPdfThread null
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

    // Before Android 8 bitmap pixels live in the app's heap, so a large page can exhaust it on older phones.
    private fun allocate(width: Int, height: Int): Bitmap? = try {
        createBitmap(width, height)
    } catch (ignored: OutOfMemoryError) {
        null
    }

    // Real sizes of the given pages; a page pdfium cannot measure keeps the first page's size.
    suspend fun measurePages(pages: IntRange): List<PdfPageSize>? = onPdfThread {
        pages.map { index -> renderer.pageSizeOrNull(index) ?: firstPageSize }
    }

    // Match rectangles in PDF points; empty below Android 15.
    suspend fun search(index: Int, query: String): List<List<RectF>> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return emptyList()
        return onPdfThread { searchOnPage(index, query) }.orEmpty()
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun searchOnPage(index: Int, query: String): List<List<RectF>> =
        renderer.openPage(index).use { page -> page.searchText(query).map { match -> match.bounds } }

    // Thrown when the document was closed meanwhile, or for a page pdfium cannot load: that page stays blank.
    @Suppress("SwallowedException")
    private suspend fun <T> onPdfThread(block: () -> T): T? {
        if (closed) return null
        return try {
            withContext(PdfThread.dispatcher) { if (closed) null else block() }
        } catch (failure: IllegalStateException) {
            null
        } catch (failure: IllegalArgumentException) {
            null
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        PdfThread.executor.execute {
            runCatching { renderer.close() }
            runCatching { descriptor.close() }
        }
    }

    companion object {
        // pdfium corrupts native memory on huge bitmaps; the viewer never needs more than a screen.
        private const val MAX_BITMAP_PIXELS = 4096L * 4096

        private const val OPEN_FAILED = "Cannot open PDF"
        private const val READ_FAILED = "Cannot read PDF pages"
        private const val NO_PAGES = "PDF has no pages"
        private const val INCOMPLETE = "PDF is truncated or not a PDF"

        // Blocks the caller until the shared PDF thread has opened the document.
        fun open(file: File, password: String?): PdfDocument = try {
            PdfThread.executor.submit<PdfDocument> { openOnPdfThread(file, password) }.get()
        } catch (failure: ExecutionException) {
            throw failure.cause ?: failure
        }

        private fun openOnPdfThread(file: File, password: String?): PdfDocument {
            if (PdfPreflight.isNeeded) preflight(file)
            val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = descriptor.closeOnFailure { openRenderer(descriptor, password) }
            return descriptor.closeOnFailure {
                renderer.closeOnFailure {
                    PdfDocument(
                        descriptor,
                        renderer,
                        renderer.pageCount,
                        readFirstPage(renderer)
                    )
                }
            }
        }

        private fun preflight(file: File) {
            if (!PdfPreflight.looksComplete(file)) throw CorruptedDocumentException(INCOMPLETE)
            if (PdfPreflight.isEncrypted(file)) throw PasswordUnsupportedException(isPdf = true)
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

        private fun readFirstPage(renderer: PdfRenderer): PdfPageSize {
            if (renderer.pageCount == 0) throw CorruptedDocumentException(NO_PAGES)
            return renderer.pageSizeOrNull(0) ?: throw CorruptedDocumentException(READ_FAILED)
        }

        private fun PdfRenderer.pageSizeOrNull(index: Int): PdfPageSize? = try {
            openPage(index).use { page ->
                PdfPageSize(page.width.toFloat().coerceAtLeast(1f), page.height.toFloat().coerceAtLeast(1f))
            }
        } catch (expected: IllegalStateException) {
            null
        } catch (expected: IllegalArgumentException) {
            null
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

private object PdfThread {
    private const val NAME = "docviewer-pdf"

    val executor: ExecutorService = Executors.newSingleThreadExecutor { task ->
        Thread(task, NAME).apply {
            isDaemon = true
            priority = Thread.NORM_PRIORITY - 1
        }
    }
    val dispatcher = executor.asCoroutineDispatcher()
}
