package io.github.volovatovta.docviewer.core.format

import io.github.volovatovta.docviewer.core.FormatDetector
import io.github.volovatovta.docviewer.core.model.DocumentFormat
import io.github.volovatovta.docviewer.core.resource
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FormatsTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `every format is registered, so a new one cannot be forgotten`() {
        assertEquals(DocumentFormat.entries.toSet(), Formats.recognized)
        DocumentFormat.entries.filter { it != DocumentFormat.PDF }.forEach { format ->
            assertEquals(format, Formats.readerOf(format)?.format, "$format has no reader")
        }
    }

    @Test
    fun `each format is recognized by its content`() {
        val pdf = temp.newFile("named.txt").apply { writeText("%PDF-1.7\n") }
        val csv = temp.newFile("data").apply { writeText("a;b;c\n1;2;3\n4;5;6\n") }
        val text = temp.newFile("notes").apply { writeText("Just a few words.\nNothing tabular.") }
        val expected = mapOf(
            pdf to DocumentFormat.PDF,
            resource("textutil.docx") to DocumentFormat.DOCX,
            resource("sheets/workbook.xlsx") to DocumentFormat.XLSX,
            resource("textutil.doc") to DocumentFormat.DOC,
            resource("sheets/workbook.xls") to DocumentFormat.XLS,
            csv to DocumentFormat.CSV,
            text to DocumentFormat.TXT,
        )
        expected.forEach { (file, format) -> assertEquals(format, FormatDetector.detect(file), file.name) }
    }

    @Test
    fun `the name only settles text files and never relabels binary ones`() {
        val csvLooking = temp.newFile("list.txt").apply { writeText("a,b\n1,2\n3,4\n") }
        assertEquals(DocumentFormat.TXT, FormatDetector.detect(csvLooking, "list.txt"))
        assertEquals(DocumentFormat.XLSX, FormatDetector.detect(resource("sheets/workbook.xlsx"), "report.docx"))
        assertNull(FormatDetector.detect(temp.newFile("blob").apply { writeBytes(ByteArray(64) { 0 }) }))
    }
}
