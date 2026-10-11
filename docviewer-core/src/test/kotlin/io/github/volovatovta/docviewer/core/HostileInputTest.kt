package io.github.volovatovta.docviewer.core

import io.github.volovatovta.docviewer.core.docx.ListSymbols
import io.github.volovatovta.docviewer.core.format.DocumentContent
import io.github.volovatovta.docviewer.core.format.DocumentReader
import io.github.volovatovta.docviewer.core.format.ReadOptions
import io.github.volovatovta.docviewer.core.model.Block
import io.github.volovatovta.docviewer.core.model.DocumentFormat
import io.github.volovatovta.docviewer.core.model.Table
import io.github.volovatovta.docviewer.core.model.Workbook
import io.github.volovatovta.docviewer.core.ole.CompoundFile
import io.github.volovatovta.docviewer.core.stream.CsvFile
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

private const val RELATIONSHIPS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"

// Files from strangers: each case is a small file that used to exhaust the heap, lose data or escape as a raw error.
class HostileInputTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `a cell far beyond Excel's grid is dropped instead of allocating every row before it`() {
        val file = xlsx(
            "<row r=\"1\"><c r=\"A1\" t=\"inlineStr\"><is><t>kept</t></is></c></row>" +
                "<row r=\"2000000000\"><c r=\"A2000000000\"><v>1</v></c></row>" +
                "<row r=\"3\"><c r=\"ZZZZZZZZ3\"><v>2</v></c></row>"
        )

        val table = workbook(file).sheets.single().table

        // Row 2,000,000,000 is gone; the unreadable reference in row 3 takes the column after the previous cell.
        assertEquals(3, table.rowCount + (if (table.header != null) 1 else 0))
        assertEquals(2, table.columnCount)
    }

    @Test
    fun `a value in the last column costs one cell, not sixteen thousand`() {
        val rows = (1..2_000).joinToString("") { "<row r=\"$it\"><c r=\"XFD$it\"><v>$it</v></c></row>" }

        val table = workbook(xlsx(rows)).sheets.single().table

        assertEquals(16_384, table.columnCount)
        assertEquals(1, table.row(table.rowCount - 1).count { it.isNotEmpty() })
    }

    @Test
    fun `a stray quote inside a CSV field keeps every row when streamed`() {
        val file = temp.newFile("stray.csv").apply {
            writeText(
                buildString {
                    appendLine("id,product,price")
                    repeat(5_000) { appendLine(if (it == 10) "$it,Monitor 27\" Dell,199" else "$it,Cable,5") }
                }
            )
        }

        assertStreamedLikeParsed(file, expectedRows = 5_000)
    }

    @Test
    fun `an unclosed quote cannot swallow the rest of a streamed CSV`() {
        val file = temp.newFile("unclosed.csv").apply {
            writeText(
                buildString {
                    appendLine("id,note")
                    appendLine("0,\"never closed")
                    repeat(100_000) { appendLine("${it + 1},${"x".repeat(20)}") }
                }
            )
        }

        CsvFile.open(file)!!.use { streamed -> assertTrue(streamed.rowCount > 1, "rows: ${streamed.rowCount}") }
    }

    @Test
    fun `a DOCTYPE in document XML is refused before any entity is expanded`() {
        val laughs = "<!DOCTYPE d [<!ENTITY a \"aaaaaaaaaa\"><!ENTITY b \"&a;&a;&a;&a;&a;&a;&a;&a;&a;&a;\">]>"
        val file = temp.newFile("laughs.docx")
        DocxFixtures.write(
            file,
            DocxFixtures.packageParts() + ("word/document.xml" to laughs + paragraph("&b;"))
        )

        assertFailsWith<CorruptedDocumentException> { DocumentReader.read(file) }
    }

    @Test
    fun `tables nested thousands deep are cut to a depth the viewer can draw`() {
        val nested = "<w:tbl><w:tr><w:tc>".repeat(3_000) + "<w:p/>" + "</w:tc></w:tr></w:tbl>".repeat(3_000)
        val file = temp.newFile("nested.docx")
        DocxFixtures.write(file, DocxFixtures.packageParts() + ("word/document.xml" to DocxFixtures.document(nested)))

        val document = assertIs<DocumentContent.Flow>(DocumentReader.read(file)).document

        assertEquals(16, document.blocks.single().tableDepth())
    }

    @Test
    fun `a part that unpacks beyond its declared size is refused`() {
        val file = temp.newFile("lying.docx")
        DocxFixtures.write(
            file,
            DocxFixtures.packageParts() + ("word/document.xml" to paragraph("bomb ".repeat(200_000)))
        )
        file.writeBytes(ZipSizes.declareSmaller(file.readBytes(), "word/document.xml", declared = 1_000))

        assertFailsWith<CorruptedDocumentException> { DocumentReader.read(file) }
    }

    @Test
    fun `a DOCTYPE hidden behind comments or a long prolog is still refused`() {
        val doctype = "<!DOCTYPE d [<!ENTITY a \"x\">]>"
        val prologs = listOf(
            "<?xml version=\"1.0\"?><!-- <w:p> -->$doctype",
            "<!--${" ".repeat(70_000)}-->$doctype",
        )
        for (prolog in prologs) {
            val file = temp.newFile()
            DocxFixtures.write(file, DocxFixtures.packageParts() + ("word/document.xml" to prolog + paragraph("&a;")))

            assertFailsWith<CorruptedDocumentException>(prolog.take(40)) { DocumentReader.read(file) }
        }
    }

    @Test
    fun `an OLE container whose DIFAT chain points at itself fails fast`() {
        val file = temp.newFile("loop.xls").apply { writeBytes(selfLoopingDifat()) }

        for (format in listOf(DocumentFormat.XLS, DocumentFormat.DOC)) {
            assertFailsWith<DocumentException>(format.name) { DocumentReader.read(file, format = format) }
        }
    }

    @Test
    fun `a workbook too large to read whole is refused as too large, not as an unknown format`() {
        val file = temp.newFile("huge.xls")
        RandomAccessFile(file, "rw").use { sparse ->
            sparse.write(CompoundFile.SIGNATURE)
            sparse.setLength(Runtime.getRuntime().maxMemory() / 4 + 1)
        }
        val options = ReadOptions { memoryBudgetBytes(64L * 1024 * 1024) }

        assertEquals(DocumentFormat.XLS, FormatDetector.detect(file, "huge.xls"))
        assertFailsWith<DocumentTooLargeException> { DocumentReader.read(file, "huge.xls", options = options) }
    }

    @Test
    fun `malformed package relationships end as a document error, not a raw parser exception`() {
        val file = temp.newFile("broken-rels.docx")
        DocxFixtures.write(file, DocxFixtures.packageParts() + ("_rels/.rels" to "<Relationships><oops"))

        assertFailsWith<DocumentException> { DocumentReader.read(file, format = DocumentFormat.DOCX) }
    }

    @Test
    fun `a list counter far beyond any real list falls back to digits`() {
        assertEquals("2000000000", ListSymbols.number(2_000_000_000, "lowerLetter"))
        assertEquals("bb", ListSymbols.number(28, "lowerLetter"))
    }

    @Test
    fun `an Excel file holding a Word object is still Excel`() {
        val file = xlsx(
            "<row r=\"1\"><c r=\"A1\"><v>1</v></c></row>",
            extra = mapOf(
                "xl/embeddings/note.docx" to "embedded",
                "word/document.xml" to DocxFixtures.document(""),
            )
        )

        assertEquals(DocumentFormat.XLSX, FormatDetector.detect(file))
    }

    @Test
    fun `text that quotes a PDF header stays text`() {
        val notes = temp.newFile(
            "notes.txt"
        ).apply { writeText("Checklist\nThe export starts with %PDF-1.7 as usual.\n") }

        assertEquals(DocumentFormat.TXT, FormatDetector.detect(notes, "notes.txt"))
    }

    private fun Block.tableDepth(): Int {
        if (this !is Table) return 0
        return 1 + (rows.flatMap { it.cells }.flatMap { it.blocks }.maxOfOrNull { it.tableDepth() } ?: 0)
    }

    private fun paragraph(text: String) = DocxFixtures.document("<w:p><w:r><w:t>$text</w:t></w:r></w:p>")

    private fun assertStreamedLikeParsed(file: File, expectedRows: Int) {
        val parsed = CsvParser.parse(file)
        CsvFile.open(file)!!.use { streamed ->
            assertEquals(expectedRows, parsed.rows.size)
            assertEquals(parsed.rows.size, streamed.rowCount)
            assertEquals(parsed.columnCount, streamed.columnCount)
            parsed.rows.forEachIndexed { index, row -> assertEquals(row, streamed.row(index), "row $index") }
        }
    }

    private fun workbook(file: File): Workbook =
        assertIs<DocumentContent.Sheets>(DocumentReader.read(file)).workbook

    private fun xlsx(sheetRows: String, extra: Map<String, String> = emptyMap()): File {
        val main = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
        val parts = mapOf(
            "[Content_Types].xml" to "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"/>",
            "_rels/.rels" to DocxFixtures.relationships(
                DocxFixtures.relationship("rId1", "officeDocument", "xl/workbook.xml"),
            ),
            "xl/workbook.xml" to "<workbook xmlns=\"$main\" xmlns:r=\"$RELATIONSHIPS\">" +
                "<sheets><sheet name=\"Data\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>",
            "xl/_rels/workbook.xml.rels" to DocxFixtures.relationships(
                DocxFixtures.relationship("rId1", "worksheet", "worksheets/sheet1.xml"),
            ),
            "xl/worksheets/sheet1.xml" to "<worksheet xmlns=\"$main\"><sheetData>$sheetRows</sheetData></worksheet>",
        )
        return temp.newFile().also { DocxFixtures.write(it, parts + extra) }
    }

    // A 512-byte-sector compound file whose only DIFAT sector lists itself as the next one, forever.
    private fun selfLoopingDifat(): ByteArray {
        val sector = 512
        val buffer = ByteBuffer.allocate(sector * 2).order(ByteOrder.LITTLE_ENDIAN)
        byteArrayOf(
            0xD0.toByte(),
            0xCF.toByte(),
            0x11,
            0xE0.toByte(),
            0xA1.toByte(),
            0xB1.toByte(),
            0x1A,
            0xE1.toByte()
        )
            .forEach(buffer::put)
        buffer.putShort(0x1A, 3).putShort(0x1C, 0xFFFE.toShort()).putShort(0x1E, 9).putShort(0x20, 6)
        buffer.putInt(0x2C, 1).putInt(0x30, 0).putInt(0x38, 4096).putInt(0x3C, -2).putInt(0x44, 0).putInt(0x48, 1)
        for (index in 0 until 109) buffer.putInt(0x4C + index * 4, -1)
        for (index in 0 until 127) buffer.putInt(sector + index * 4, 0)
        buffer.putInt(sector + 127 * 4, 0)
        return buffer.array()
    }
}

// Rewrites the uncompressed size a ZIP declares for one entry, in both its local and central headers.
private object ZipSizes {
    private const val LOCAL_HEADER = 0x04034b50
    private const val CENTRAL_HEADER = 0x02014b50

    fun declareSmaller(zip: ByteArray, name: String, declared: Int): ByteArray {
        val buffer = ByteBuffer.wrap(zip.copyOf()).order(ByteOrder.LITTLE_ENDIAN)
        val nameBytes = name.toByteArray()
        for (at in 0 until zip.size - 46) {
            when (buffer.getInt(at)) {
                LOCAL_HEADER -> if (nameAt(
                        zip,
                        at + 30,
                        buffer.getShort(at + 26).toInt(),
                        nameBytes
                    )
                ) {
                    buffer.putInt(at + 22, declared)
                }
                CENTRAL_HEADER -> if (nameAt(
                        zip,
                        at + 46,
                        buffer.getShort(at + 28).toInt(),
                        nameBytes
                    )
                ) {
                    buffer.putInt(at + 24, declared)
                }
            }
        }
        return buffer.array()
    }

    private fun nameAt(zip: ByteArray, at: Int, length: Int, name: ByteArray) =
        length == name.size && zip.copyOfRange(at, at + length).contentEquals(name)
}
