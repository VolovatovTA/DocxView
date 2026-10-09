package io.github.volovatovta.docviewer.internal.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.pdf.LoadParams
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.annotation.RequiresApi
import io.github.volovatovta.docviewer.core.CorruptedDocumentException
import io.github.volovatovta.docviewer.core.PasswordProtectedException
import io.github.volovatovta.docviewer.internal.PasswordUnsupportedException
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors

internal class PdfPageSize(val width: Float, val height: Float)

// PdfRenderer allows one open page at a time and is not thread-safe, so every call runs on one thread.
internal class PdfDocument private constructor(
    private val fd: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
    val pageSizes: List<PdfPageSize>,
) : Closeable {

    private val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "docviewer-pdf").apply { priority = Thread.NORM_PRIORITY - 1 } }
    private val dispatcher = executor.asCoroutineDispatcher()

    @Volatile
    private var closed = false

    val pageCount: Int get() = pageSizes.size

    val isSearchSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM

    // Renders the region [left, top, left+width, top+height] of the page scaled to pageWidthPx x pageHeightPx.
    suspend fun render(index: Int, pageWidthPx: Int, pageHeightPx: Int, left: Int = 0, top: Int = 0, width: Int = pageWidthPx, height: Int = pageHeightPx): Bitmap? =
        onPdfThread {
            if (width <= 0 || height <= 0) return@onPdfThread null
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
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
    suspend fun search(index: Int, query: String): List<List<RectF>> =
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) emptyList() else onPdfThread { searchV(index, query) } ?: emptyList()

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun searchV(index: Int, query: String): List<List<RectF>> =
        renderer.openPage(index).use { page -> page.searchText(query).map { match -> match.bounds } }

    private suspend fun <T> onPdfThread(block: () -> T): T? {
        if (closed) return null
        return try {
            withContext(dispatcher) { if (closed) null else block() }
        } catch (e: java.util.concurrent.RejectedExecutionException) {
            null
        } catch (e: IllegalStateException) {
            // Thrown by PdfRenderer when the document was closed between the check and the call.
            if (closed) null else throw e
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        executor.execute {
            runCatching { renderer.close() }
            runCatching { fd.close() }
        }
        executor.shutdown()
    }

    companion object {
        fun open(file: File, password: String?): PdfDocument {
            val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM && password != null) {
                    PdfRenderer(fd, LoadParams.Builder().setPassword(password).build())
                } else {
                    PdfRenderer(fd)
                }
            } catch (e: SecurityException) {
                fd.close()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) throw PasswordProtectedException(wrongPassword = password != null)
                throw PasswordUnsupportedException(isPdf = true)
            } catch (e: IOException) {
                fd.close()
                throw CorruptedDocumentException("Cannot open PDF", e)
            } catch (e: RuntimeException) {
                fd.close()
                throw CorruptedDocumentException("Cannot open PDF", e)
            }
            try {
                val sizes = (0 until renderer.pageCount).map { i ->
                    renderer.openPage(i).use { page -> PdfPageSize(page.width.toFloat().coerceAtLeast(1f), page.height.toFloat().coerceAtLeast(1f)) }
                }
                return PdfDocument(fd, renderer, sizes)
            } catch (e: RuntimeException) {
                renderer.close()
                fd.close()
                throw CorruptedDocumentException("Cannot read PDF pages", e)
            }
        }
    }
}
