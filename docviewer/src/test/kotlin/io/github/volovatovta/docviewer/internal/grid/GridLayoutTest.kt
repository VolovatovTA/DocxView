package io.github.volovatovta.docviewer.internal.grid

import org.junit.Test
import kotlin.test.assertEquals

class GridLayoutTest {

    private fun grid(columns: Int, width: Float = 100f): GridLayout {
        val widths = FloatArray(columns) { width }
        val columnX = FloatArray(columns + 1) { it * width }
        return GridLayout(widths, columnX, BooleanArray(columns), rowNumberWidth = 0f)
    }

    @Test
    fun `an ordinary sheet composes every column`() {
        assertEquals(0..11, grid(12).columnsNear(offset = 0f, viewportWidth = 1080f))
    }

    @Test
    fun `a sheet as wide as Excel allows composes only a screenful around the viewport`() {
        val wide = grid(16_384)

        val columns = wide.columnsNear(offset = 800_000f, viewportWidth = 1080f)

        assertEquals(8_000 - 24..8_010 + 24, columns)
        assertEquals(16_384 - 1, wide.columnsNear(offset = 1_638_400f, viewportWidth = 1080f).last)
    }
}
