package io.github.volovatovta.docviewer.internal.flow

import io.github.volovatovta.docviewer.core.model.Table
import org.junit.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TableColumnsTest {

    private val noAuthorWidths = Table(emptyList())

    @Test
    fun `columns that fit on one line share the spare space`() {
        val widths = columnWidths(
            noAuthorWidths,
            ColumnContent(floatArrayOf(40f, 40f), floatArrayOf(100f, 200f)),
            600f,
            10f
        )
        assertEquals(600f, widths.sum(), 0.01f)
        assertEquals(2f, widths[1] / widths[0], 0.01f)
    }

    @Test
    fun `narrow screens keep every word whole and give the rest to the columns that need it`() {
        val widths = columnWidths(
            noAuthorWidths,
            ColumnContent(floatArrayOf(50f, 50f), floatArrayOf(60f, 450f)),
            300f,
            10f
        )
        assertEquals(300f, widths.sum(), 0.01f)
        assertTrue(widths.indices.all { widths[it] >= 50f })
        assertTrue(widths[1] > widths[0])
    }

    @Test
    fun `tables wider than the screen fall back to their minimum and scroll`() {
        val widths = columnWidths(
            noAuthorWidths,
            ColumnContent(floatArrayOf(200f, 200f), floatArrayOf(400f, 400f)),
            300f,
            10f
        )
        assertContentEquals(floatArrayOf(200f, 200f), widths)
    }

    @Test
    fun `author proportions win when they leave room for the content`() {
        val table = Table(emptyList(), columnWidths = listOf(100f, 300f))
        val widths = columnWidths(table, ColumnContent(floatArrayOf(20f, 20f), floatArrayOf(50f, 50f)), 400f, 10f)
        assertContentEquals(floatArrayOf(100f, 300f), widths)
    }
}
