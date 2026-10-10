package io.github.volovatovta.docviewer.core

import io.github.volovatovta.docviewer.core.model.DocumentFormat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ParsingCostTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `a DOCX is measured by its unpacked XML, not by its compressed size`() {
        val xml = "<w:p><w:r><w:t>repetitive text compresses extremely well</w:t></w:r></w:p>".repeat(50_000)
        val file = temp.newFile("big.docx")
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("word/document.xml"))
            zip.write(xml.toByteArray())
            zip.closeEntry()
        }
        val cost = ParsingCost.bytesToParse(file, DocumentFormat.DOCX)
        assertEquals(xml.length.toLong(), cost)
        assertTrue(cost > file.length() * 20, "compressed ${file.length()}, unpacked $cost")
    }

    @Test
    fun `other formats are measured by their file size`() {
        val file = temp.newFile("data.csv").apply { writeText("a;b\n1;2\n") }
        assertEquals(file.length(), ParsingCost.bytesToParse(file, DocumentFormat.CSV))
    }

    @Test
    fun `a broken DOCX falls back to its file size instead of failing`() {
        val file = temp.newFile("broken.docx").apply { writeText("not a zip") }
        assertEquals(file.length(), ParsingCost.bytesToParse(file, DocumentFormat.DOCX))
    }

    @Test
    fun `PDF costs no parsing memory and CSV is the hungriest format`() {
        assertEquals(0, ParsingCost.peakMemoryPerByte(DocumentFormat.PDF))
        val costs = DocumentFormat.entries.associateWith(ParsingCost::peakMemoryPerByte)
        assertEquals(DocumentFormat.CSV, costs.maxBy { it.value }.key)
    }
}
