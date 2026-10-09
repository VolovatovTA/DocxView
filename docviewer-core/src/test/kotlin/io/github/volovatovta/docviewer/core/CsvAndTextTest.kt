package io.github.volovatovta.docviewer.core

import io.github.volovatovta.docviewer.core.model.DocumentFormat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CsvAndTextTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `parses quoted fields with delimiters, quotes and newlines`() {
        val doc = CsvParser.parse("name,comment,price\r\n\"Smith, J\",\"said \"\"hi\"\"\nthen left\",10.5\nDoe,,3\n")
        assertEquals(listOf("name", "comment", "price"), doc.header)
        assertEquals(listOf("Smith, J", "said \"hi\"\nthen left", "10.5"), doc.rows[0])
        assertEquals(listOf("Doe", "", "3"), doc.rows[1])
        assertEquals(3, doc.columnCount)
    }

    @Test
    fun `detects semicolon, tab and pipe delimiters`() {
        assertEquals(';', CsvParser.detectDelimiter("a;b;c\n1,5;2,5;3\n4;5;6"))
        assertEquals('\t', CsvParser.detectDelimiter("a\tb\n1\t2\n3\t4"))
        assertEquals('|', CsvParser.detectDelimiter("a|b|c\n1|2|3"))
        assertNull(CsvParser.detectDelimiter("just a sentence\nand another one"))
    }

    @Test
    fun `header heuristic`() {
        assertTrue(CsvParser.parse("city,population\nMoscow,13000000\nKazan,1300000").header != null)
        assertNull(CsvParser.parse("1,2,3\n4,5,6").header)
        assertNull(CsvParser.parse("a,b\nc,d", CsvOptions(header = CsvHeaderMode.None)).header)
    }

    @Test
    fun `row limit marks the table as truncated`() {
        val text = (1..100).joinToString("\n") { "$it,x" }
        val doc = CsvParser.parse(text, CsvOptions(header = CsvHeaderMode.None, maxRows = 10))
        assertEquals(10, doc.rows.size)
        assertTrue(doc.truncated)
    }

    @Test
    fun `decodes utf8, utf8 bom, cp1251 and latin1`() {
        assertEquals("Привет", TextDecoding.decode("Привет".toByteArray()).text)
        assertEquals("Привет", TextDecoding.decode(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "Привет".toByteArray()).text)
        val cp1251 = TextDecoding.decode("Цена;Товар\n100;Хлеб".toByteArray(charset("windows-1251")))
        assertEquals("windows-1251", cp1251.charsetName)
        assertEquals("Цена;Товар\n100;Хлеб", cp1251.text)
        val latin = TextDecoding.decode("Café crème brûlée".toByteArray(charset("windows-1252")))
        assertEquals("Café crème brûlée", latin.text)
        assertEquals("hello world", TextDecoding.decode("hello world".toByteArray(Charsets.UTF_16LE)).text)
    }

    @Test
    fun `format detection uses magic bytes before names`() {
        val pdfNamedTxt = temp.newFile("x.txt").apply { writeBytes("%PDF-1.7\n...".toByteArray()) }
        assertEquals(DocumentFormat.PDF, FormatDetector.detect(pdfNamedTxt, "x.txt"))
        assertEquals(DocumentFormat.DOC, FormatDetector.detect(resource("textutil.doc")))
        assertEquals(DocumentFormat.DOCX, FormatDetector.detect(resource("textutil.docx")))
        val csv = temp.newFile("data").apply { writeText("a;b;c\n1;2;3\n4;5;6") }
        assertEquals(DocumentFormat.CSV, FormatDetector.detect(csv))
        val txt = temp.newFile("notes").apply { writeText("Hello world.\nSecond line.") }
        assertEquals(DocumentFormat.TXT, FormatDetector.detect(txt))
        val binary = temp.newFile("blob.bin").apply { writeBytes(ByteArray(512) { (it * 7).toByte() }) }
        assertNull(FormatDetector.detect(binary))
    }

    @Test
    fun `plain text becomes paragraphs and is searchable`() {
        val doc = PlainTextParser.parse("first line\r\n\r\nsecond Line\n\n")
        assertEquals(listOf("first line", "", "second Line"), doc.blocks.map { (it as io.github.volovatovta.docviewer.core.model.Paragraph).plainText })
        val matches = FlowSearch.search(doc, "LINE")
        assertEquals(2, matches.size)
        assertEquals(7, matches[1].start)
        assertTrue(FlowSearch.search(doc, "  ").isEmpty())
    }
}
