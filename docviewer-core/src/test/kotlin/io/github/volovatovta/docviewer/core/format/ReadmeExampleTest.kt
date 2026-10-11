package io.github.volovatovta.docviewer.core.format

import io.github.volovatovta.docviewer.core.FlowSearch
import io.github.volovatovta.docviewer.core.FormatDetector
import io.github.volovatovta.docviewer.core.model.DocumentFormat
import io.github.volovatovta.docviewer.core.resource
import org.junit.Test
import kotlin.test.assertEquals

// The "Parsers only" example of the README, kept here so that it keeps compiling and working.
class ReadmeExampleTest {

    @Test
    fun `the parsers-only example reads Word and Excel files`() {
        val results = listOf(resource("report.docx"), resource("sheets/workbook.xlsx")).map { file ->
            val format = FormatDetector.detect(file)
            val result = DocumentReader.read(file).use { content ->
                when (content) {
                    is DocumentContent.Flow -> FlowSearch.search(content.document, "needle")
                    is DocumentContent.Sheets -> content.workbook.sheets.map { it.name }
                    else -> null
                }
            }
            format to result
        }
        assertEquals(DocumentFormat.DOCX, results[0].first)
        assertEquals(listOf("Sales", "Inventory"), results[1].second)
    }
}
