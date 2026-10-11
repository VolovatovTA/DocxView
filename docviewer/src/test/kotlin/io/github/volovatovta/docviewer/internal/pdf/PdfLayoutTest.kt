package io.github.volovatovta.docviewer.internal.pdf

import androidx.compose.ui.unit.Constraints
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PdfLayoutTest {

    private val a4 = PdfPageSize(595f, 842f)
    private val landscape = PdfPageSize(842f, 595f)
    private val receipt = PdfPageSize(216f, 14_400f)

    @Test
    fun `item heights add up to absolute offsets at any zoom`() {
        val layout = PdfLayout(List(500) { if (it % 3 == 0) landscape else a4 }, viewportWidth = 1080f, spacing = 26f)
        for (scale in listOf(1f, 1.37f, 2.5f, 5.99f)) {
            val sum = (0 until layout.pageCount).sumOf { layout.itemHeight(it, scale) }
            assertEquals(layout.top(layout.pageCount, scale), sum, "drift at scale $scale")
        }
    }

    @Test
    fun `page lookup respects page boundaries`() {
        val layout = PdfLayout(List(3) { a4 }, viewportWidth = 1080f, spacing = 20f)
        assertEquals(0, layout.pageAt(0f))
        assertEquals(0, layout.pageAt(layout.top(1, 1f) - 1f))
        assertEquals(1, layout.pageAt(layout.top(1, 1f).toFloat()))
        assertEquals(2, layout.pageAt(Float.MAX_VALUE))
    }

    @Test
    fun `ordinary documents can be zoomed far beyond the default maximum`() {
        val layout = PdfLayout(List(10) { a4 }, viewportWidth = 1080f, spacing = 26f)
        assertTrue(layout.maxRepresentableScale(1080) > 6f)
    }

    @Test
    fun `a receipt is zoomed only as far as Compose can measure it`() {
        val layout = PdfLayout(listOf(receipt), viewportWidth = 1080f, spacing = 26f)
        val limit = layout.maxRepresentableScale(1080)
        assertTrue(limit in 1f..6f, "limit $limit")
        Constraints.fixed(layout.pageWidth(limit), layout.pageHeight(0, limit))
        Constraints.fixed(1080, layout.itemHeight(0, limit))
        val beyond = limit * 1.2f
        assertFailsWith<IllegalArgumentException> {
            Constraints.fixed(
                layout.pageWidth(beyond),
                layout.pageHeight(0, beyond)
            )
        }
    }

    @Test
    fun `base bitmaps stay within the pixel budget`() {
        val layout = PdfLayout(listOf(receipt), viewportWidth = 1080f, spacing = 26f)
        val height = layout.baseBitmapWidth * receipt.height / receipt.width
        assertTrue(layout.baseBitmapWidth * height <= 2_500_001f)
    }

    @Test
    fun `a page as thin as a ribbon still fits Compose constraints at zoom 1`() {
        val ribbon = PdfLayout(listOf(PdfPageSize(10f, 3_000f)), viewportWidth = 1080f, spacing = 26f)
        Constraints.fixed(ribbon.pageWidth(1f), ribbon.pageHeight(0, 1f))
        Constraints.fixed(1080, ribbon.itemHeight(0, 1f))
    }
}
