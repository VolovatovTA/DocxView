package io.github.volovatovta.docviewer.core.format

import io.github.volovatovta.docviewer.core.CsvOptions
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class ReadOptionsTest {

    @Test
    fun `the defaults set no budget and no limit`() {
        val options = ReadOptions()
        assertNull(options.memoryBudgetBytes)
        assertNull(options.maxDocumentSizeBytes)
        assertEquals(CsvOptions(), options.csv)
        assertEquals(options, ReadOptions {})
    }

    @Test
    fun `newBuilder keeps what it does not change, and equal options are equal`() {
        val options = ReadOptions { memoryBudgetBytes(64L shl 20).maxDocumentSizeBytes(1_000) }
        val changed = options.newBuilder().maxDocumentSizeBytes(2_000).build()
        assertEquals(64L shl 20, changed.memoryBudgetBytes)
        assertEquals(2_000L, changed.maxDocumentSizeBytes)
        assertNotEquals(options, changed)
        assertEquals(options.hashCode(), options.newBuilder().build().hashCode())
    }
}
