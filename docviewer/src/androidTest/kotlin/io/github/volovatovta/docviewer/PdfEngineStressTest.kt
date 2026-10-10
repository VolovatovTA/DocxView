package io.github.volovatovta.docviewer

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.os.SystemClock
import android.util.Log
import io.github.volovatovta.docviewer.core.CorruptedDocumentException
import io.github.volovatovta.docviewer.core.PasswordProtectedException
import io.github.volovatovta.docviewer.internal.PasswordUnsupportedException
import io.github.volovatovta.docviewer.internal.pdf.PdfDocument
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.math.sqrt
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

// Drives the PdfRenderer wrapper the way the viewer does, on every PDF of the stress set.
@RunWith(Parameterized::class)
class PdfEngineStressTest(private val pdf: TestPdf) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun cases(): List<TestPdf> = TestPdf.own() + GeneratedPdfs.all() + TestPdf.corpus()

        // Real-world files that are broken on purpose: failing cleanly is as good as rendering.
        private val MayFail = setOf("helloworld-bad", "bad-PageLabels")

        private val EncryptedWithoutPassword = setOf("encrypted-attachment")

        // Platform pdfium of Android 11-14 draws nothing for this shading; 9-10 and 15+ render it.
        private val BlankOnPlatform = mapOf("axial_shading_many_inputs" to 30..34)

        private const val SCREEN_WIDTH = 1080
        private const val BASE_BITMAP_PIXELS = 2_500_000f
        private const val ZOOM = 4
        private const val TILE_HEIGHT = 1920
        private const val ALL_PAGES_UP_TO = 50
        private const val FIRST_PAGES = 30
        private const val LAST_PAGES = 20
    }

    private class RenderStats(val rendered: Int, val totalMs: Long, val worstMs: Long, val nonBlank: Int)

    @Test
    fun opensRendersAndSearches() {
        val started = SystemClock.elapsedRealtime()
        val document = openOrNull()
        val openMs = SystemClock.elapsedRealtime() - started
        if (document == null) return log("result=failed-cleanly|openMs=$openMs")
        try {
            assertTrue(document.pageCount > 0)
            val stats = renderPages(document)
            renderZoomedTile(document)
            val hits = searchHits(document)
            if (mustShowContent()) assertTrue(stats.nonBlank > 0, "every rendered page of ${pdf.name} is blank")
            log(
                "result=ok|pages=${document.pageCount}|openMs=$openMs|rendered=${stats.rendered}|" +
                    "renderAvgMs=${stats.totalMs / stats.rendered}|renderMaxMs=${stats.worstMs}|" +
                    "nonBlank=${stats.nonBlank}|hits=$hits",
            )
        } finally {
            document.close()
        }
    }

    private fun openOrNull(): PdfDocument? = try {
        PdfDocument.open(pdf.file(), password = null)
    } catch (e: CorruptedDocumentException) {
        if (pdf.name in MayFail) null else throw e
    } catch (e: PasswordProtectedException) {
        fail("${pdf.name} unexpectedly asks for a password", e)
    } catch (e: PasswordUnsupportedException) {
        // Before Android 9 every encrypted file is refused up front, even one without a user password.
        if (pdf.name in EncryptedWithoutPassword && Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            null
        } else {
            fail("${pdf.name} unexpectedly asks for a password", e)
        }
    }

    private fun renderPages(document: PdfDocument): RenderStats {
        val count = document.pageCount
        val pages = if (count <= ALL_PAGES_UP_TO) {
            0 until count
        } else {
            (0 until FIRST_PAGES) + (count - LAST_PAGES until count)
        }
        var total = 0L
        var worst = 0L
        var nonBlank = 0
        for (page in pages) {
            val size = document.pageSizes[page]
            // Same budget as the viewer: fit the screen width, at most ~2.5 MP per page.
            val cappedWidth = sqrt(BASE_BITMAP_PIXELS * size.width / size.height)
            val width = minOf(SCREEN_WIDTH.toFloat(), cappedWidth).toInt().coerceAtLeast(1)
            val height = (width * size.height / size.width).toInt().coerceAtLeast(1)
            val started = SystemClock.elapsedRealtime()
            val rendered = runBlocking { document.render(page, width, height) }
            val bitmap = assertNotNull(rendered, "page $page did not render")
            val ms = SystemClock.elapsedRealtime() - started
            total += ms
            worst = maxOf(worst, ms)
            if (!bitmap.isBlank()) nonBlank++
            bitmap.recycle()
        }
        return RenderStats(pages.count(), total, worst, nonBlank)
    }

    // A tile of the first page at 4x, as the viewer renders for crisp text when zoomed in.
    private fun renderZoomedTile(document: PdfDocument) {
        val first = document.pageSizes[0]
        val pageWidth = SCREEN_WIDTH * ZOOM
        val pageHeight = (pageWidth * first.height / first.width).toInt().coerceAtLeast(1)
        val top = (pageHeight / 3).coerceAtMost(pageHeight - 1)
        val tile = runBlocking {
            document.render(0, pageWidth, pageHeight, pageWidth / 3, top, SCREEN_WIDTH, minOf(TILE_HEIGHT, pageHeight))
        }
        assertNotNull(tile, "zoomed tile of ${pdf.name} did not render").recycle()
    }

    private fun searchHits(document: PdfDocument): Int? {
        val word = pdf.searchWord ?: return null
        if (!document.isSearchSupported) return null
        val hits = runBlocking { (0 until document.pageCount).sumOf { document.search(it, word).size } }
        assertTrue(hits >= pdf.minHits, "search for '$word' in ${pdf.name} found $hits, expected >= ${pdf.minHits}")
        return hits
    }

    private fun mustShowContent(): Boolean =
        pdf.name !in MayFail && BlankOnPlatform[pdf.name]?.contains(Build.VERSION.SDK_INT) != true

    private fun Bitmap.isBlank(): Boolean {
        val row = IntArray(width)
        return (0 until height).all { y ->
            getPixels(row, 0, width, 0, y, width, 1)
            row.all { it == Color.WHITE }
        }
    }

    private fun log(fields: String) {
        Log.i("DVTEST", "RESULT|api=${Build.VERSION.SDK_INT}|pdf=${pdf.name}|$fields")
    }
}
