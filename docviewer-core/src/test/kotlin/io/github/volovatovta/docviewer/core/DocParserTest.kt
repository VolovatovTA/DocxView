package io.github.volovatovta.docviewer.core

import io.github.volovatovta.docviewer.core.doc.DocParser
import io.github.volovatovta.docviewer.core.model.Paragraph
import io.github.volovatovta.docviewer.core.model.Table
import io.github.volovatovta.docviewer.core.model.TextRun
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DocParserTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val doc by lazy { DocParser.parse(resource("textutil.doc")) }

    private fun paragraphs() = doc.blocks.filterIsInstance<Paragraph>()

    @Test
    fun `extracts text including cyrillic`() {
        val texts = paragraphs().map { it.plainText }
        assertTrue("Отчёт о продажах" in texts, texts.toString())
        assertTrue(texts.any { it.startsWith("Это жирный, курсив и подчёркнутый текст.") }, texts.toString())
        assertTrue(texts.any { "needle" in it })
    }

    @Test
    fun `reads character formatting`() {
        val runs = paragraphs().flatMap { it.inlines }.filterIsInstance<TextRun>()
        assertTrue(runs.first { it.text.contains("жирный") }.style.bold)
        assertTrue(runs.first { it.text.contains("курсив") }.style.italic)
        assertTrue(runs.first { it.text.contains("подчёркнутый") }.style.underline)
        val heading = runs.first { it.text == "Отчёт о продажах" }
        assertTrue(heading.style.relativeSize!! > 1.5f, heading.toString())
        assertEquals(null, runs.first { it.text.startsWith("Last paragraph") }.style.relativeSize)
    }

    @Test
    fun `rebuilds tables from cell and row marks`() {
        val table = doc.blocks.filterIsInstance<Table>().single()
        assertEquals(3, table.rows.size)
        val cells = table.rows.map { row -> row.cells.map { (it.blocks.single() as Paragraph).plainText } }
        assertEquals(listOf("Name", "Qty", "Price"), cells[0])
        assertEquals(listOf("Яблоки", "10", "99.5"), cells[1])
    }

    @Test
    fun `textutil writes list markers as plain text`() {
        assertTrue(paragraphs().any { it.plainText == "\t•\tfirst bullet" }, paragraphs().toString())
    }

    @Test
    fun `Word 6 and 95 documents open as plain text`() {
        val texts = DocParser.parse(resource("legacy/word6.doc")).blocks
            .filterIsInstance<Paragraph>().map { it.plainText }.filter { it.isNotBlank() }
        assertEquals(listOf("This is a test.", "This is a new section."), texts)
    }

    @Test
    fun `garbage is reported as corrupted`() {
        val file = temp.newFile("bad.doc").apply { writeBytes(ByteArray(2048) { 7 }) }
        assertFailsWith<CorruptedDocumentException> { DocParser.parse(file) }
    }
}
