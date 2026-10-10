package io.github.volovatovta.docviewer.core

import io.github.volovatovta.docviewer.core.doc.DocParser
import io.github.volovatovta.docviewer.core.docx.DocxParser
import io.github.volovatovta.docviewer.core.model.FlowDocument
import io.github.volovatovta.docviewer.core.model.Paragraph
import io.github.volovatovta.docviewer.core.model.Table
import io.github.volovatovta.docviewer.core.model.TextRun
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// The same HTML saved by LibreOffice as Word 97 and as OOXML must produce the same model.
@RunWith(Parameterized::class)
class LibreOfficeFixturesTest(private val fileName: String) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun files() = listOf("libreoffice.doc", "libreoffice.docx")
    }

    private val doc: FlowDocument by lazy {
        if (fileName.endsWith(".docx")) DocxParser.parse(resource(fileName)) else DocParser.parse(resource(fileName))
    }

    private fun paragraphs() = doc.blocks.filterIsInstance<Paragraph>()

    @Test
    fun headings() {
        val h1 = paragraphs().first { it.plainText == "Отчёт о продажах" }
        val h2 = paragraphs().first { it.plainText == "Second level" }
        assertEquals(1, h1.style.headingLevel)
        assertEquals(2, h2.style.headingLevel)
    }

    @Test
    fun formatting() {
        val runs = paragraphs().flatMap { it.inlines }.filterIsInstance<TextRun>()
        assertTrue(runs.first { it.text == "жирный" }.style.bold, runs.toString())
        assertTrue(runs.first { it.text == "курсив" }.style.italic)
        assertTrue(runs.first { it.text == "подчёркнутый" }.style.underline)
        assertEquals(0xFFFF0000.toInt(), runs.first { it.text == "Red text" }.style.color)
    }

    @Test
    fun table() {
        val table = doc.blocks.filterIsInstance<Table>().single()
        val cells = table.rows.map { row ->
            row.cells.map { cell -> cell.blocks.filterIsInstance<Paragraph>().joinToString { it.plainText } }
        }
        val expected = listOf(
            listOf("Name", "Qty", "Price"),
            listOf("Яблоки", "10", "99.5"),
            listOf("Pears", "3", "120"),
        )
        assertEquals(expected, cells)
    }

    @Test
    fun lists() {
        val items = paragraphs().filter { it.style.listMarker != null }.map { it.style.listMarker to it.plainText }
        val expected = listOf(
            "•" to "first bullet",
            "•" to "second bullet",
            "1." to "step one",
            "2." to "step two",
            "3." to "step three",
        )
        assertEquals(expected, items)
    }

    @Test
    fun hyperlink() {
        val link = paragraphs().flatMap { it.inlines }.filterIsInstance<TextRun>().first { it.link != null }
        assertEquals("example link", link.text)
        assertEquals("https://example.com/page", link.link)
    }
}
