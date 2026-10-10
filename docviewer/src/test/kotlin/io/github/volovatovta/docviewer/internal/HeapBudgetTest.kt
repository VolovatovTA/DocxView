package io.github.volovatovta.docviewer.internal

import org.junit.Test
import kotlin.test.assertEquals

class HeapBudgetTest {

    private val megabyte = 1024L * 1024

    @Test
    fun `parsing may take half of the heap`() {
        assertEquals(128 * megabyte, HeapBudget.of(maxHeap = 256 * megabyte, usedHeap = 40 * megabyte))
    }

    @Test
    fun `a busy heap leaves less`() {
        assertEquals(
            44.8,
            HeapBudget.of(maxHeap = 256 * megabyte, usedHeap = 200 * megabyte).toDouble() / megabyte,
            0.1
        )
        assertEquals(0, HeapBudget.of(maxHeap = 256 * megabyte, usedHeap = 300 * megabyte))
    }
}
