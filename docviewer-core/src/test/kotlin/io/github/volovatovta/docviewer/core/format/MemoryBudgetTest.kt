package io.github.volovatovta.docviewer.core.format

import io.github.volovatovta.docviewer.core.DocumentTooLargeException
import io.github.volovatovta.docviewer.core.resource
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class MemoryBudgetTest {

    @Test
    fun `a document parsed in memory is refused when the budget cannot hold it`() {
        val tooSmall = ReadOptions { memoryBudgetBytes(1_000) }
        val error = assertFailsWith<DocumentTooLargeException> {
            DocumentReader.read(resource("sheets/workbook.xlsx"), options = tooSmall)
        }
        assertEquals(1_000L / 6, error.limitBytes)
    }

    @Test
    fun `an explicit limit replaces the estimate`() {
        assertFailsWith<DocumentTooLargeException> {
            DocumentReader.read(resource("report.docx"), options = ReadOptions { maxDocumentSizeBytes(10) })
        }
    }

    @Test
    fun `without a budget anything is read`() {
        assertIs<DocumentContent.Sheets>(DocumentReader.read(resource("sheets/workbook.xls")))
    }
}
