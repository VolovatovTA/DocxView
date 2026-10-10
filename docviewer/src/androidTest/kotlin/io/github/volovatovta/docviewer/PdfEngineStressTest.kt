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
        fun cases(): List<TestPdf> = TestPdf.own() + TestPdf.generated() + TestPdf.corpus()

        // Real-world files that are broken on purpose: failing cleanly is as good as rendering.
        private val MAY_FAIL = setOf("helloworld-bad", "bad-PageLabels")
    }

    @Test
    fun opensRendersAndSearches() {
        val file = pdf.file()
        val openStart = SystemClock.elapsedRealtime()
        val document = try {
            PdfDocument.open(file, null)
        } catch (e: CorruptedDocumentException) {
            if (pdf.name in MAY_FAIL) {
                report("failed-cleanly", 0, SystemClock.elapsedRealtime() - openStart, 0, 0, null, null)
                return
            }
            throw e
        } catch (e: PasswordProtectedException) {
            fail("${pdf.name} unexpectedly asks for a password")
        } catch (e: PasswordUnsupportedException) {
            fail("${pdf.name} unexpectedly asks for a password")
        }
        val openMs = SystemClock.elapsedRealtime() - openStart
        try {
            val count = document.pageCount
            assertTrue(count > 0)
            val pages = if (count <= 50) (0 until count).toList() else (0 until 30) + (count - 20 until count)
            var total = 0L
            var worst = 0L
            var nonBlank = 0
            for (index in pages) {
                val size = document.pageSizes[index]
                // Same budget as the viewer: fit a 1080 px wide screen, at most ~2.5 MP per page.
                val width = minOf(1080f, sqrt(2_500_000f * size.width / size.height)).toInt().coerceAtLeast(1)
                val height = (width * size.height / size.width).toInt().coerceAtLeast(1)
                val start = SystemClock.elapsedRealtime()
                val bitmap = runBlocking { document.render(index, width, height) }
                val ms = SystemClock.elapsedRealtime() - start
                total += ms
                worst = maxOf(worst, ms)
                assertNotNull(bitmap, "page $index of ${pdf.name} did not render")
                if (!isBlank(bitmap)) nonBlank++
                bitmap.recycle()
            }
            // A zoomed-in tile of the first page, as rendered for crisp text at 4x.
            val first = document.pageSizes[0]
            val pageWidth = (1080 * 4)
            val pageHeight = (pageWidth * first.height / first.width).toInt().coerceAtLeast(1)
            val tile = runBlocking {
                document.render(0, pageWidth, pageHeight, pageWidth / 3, (pageHeight / 3).coerceAtMost(pageHeight - 1), 1080, minOf(1920, pageHeight))
            }
            assertNotNull(tile, "zoomed tile of ${pdf.name} did not render")
            tile.recycle()

            var hits: Int? = null
            val word = pdf.searchWord
            if (word != null && document.isSearchSupported) {
                hits = runBlocking { (0 until count).sumOf { document.search(it, word).size } }
                assertTrue(hits >= pdf.minHits, "search for '$word' in ${pdf.name} found $hits, expected >= ${pdf.minHits}")
            }
            if (pdf.name !in MAY_FAIL) assertTrue(nonBlank > 0, "every rendered page of ${pdf.name} is blank")
            report("ok", count, openMs, total, worst, nonBlank, hits, pages.size)
        } finally {
            document.close()
        }
    }

    private fun report(result: String, pages: Int, openMs: Long, renderTotal: Long, renderWorst: Long, nonBlank: Int?, hits: Int?, rendered: Int = 0) {
        Log.i(
            "DVTEST",
            "RESULT|api=${Build.VERSION.SDK_INT}|pdf=${pdf.name}|result=$result|pages=$pages|openMs=$openMs|" +
                "rendered=$rendered|renderAvgMs=${if (rendered > 0) renderTotal / rendered else 0}|renderMaxMs=$renderWorst|nonBlank=$nonBlank|hits=$hits",
        )
    }

    private fun isBlank(bitmap: Bitmap): Boolean {
        val row = IntArray(bitmap.width)
        for (y in 0 until bitmap.height) {
            bitmap.getPixels(row, 0, bitmap.width, 0, y, bitmap.width, 1)
            if (row.any { it != Color.WHITE }) return false
        }
        return true
    }
}
