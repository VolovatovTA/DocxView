package io.github.volovatovta.docviewer.core

import io.github.volovatovta.docviewer.core.docx.DocxParser
import io.github.volovatovta.docviewer.core.model.Divider
import io.github.volovatovta.docviewer.core.model.FlowDocument
import io.github.volovatovta.docviewer.core.model.ImageRun
import io.github.volovatovta.docviewer.core.model.PageBreak
import io.github.volovatovta.docviewer.core.model.Paragraph
import io.github.volovatovta.docviewer.core.model.ParagraphStyle
import io.github.volovatovta.docviewer.core.model.Table
import io.github.volovatovta.docviewer.core.model.TextAlignment
import io.github.volovatovta.docviewer.core.model.TextRun
import io.github.volovatovta.docviewer.core.model.VerticalAlign
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DocxParserTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val ns = """xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:mc="http://schemas.openxmlformats.org/markup-compatibility/2006""""

    private val styles = """<w:styles $ns>
        <w:docDefaults><w:rPrDefault><w:rPr><w:sz w:val="22"/></w:rPr></w:rPrDefault></w:docDefaults>
        <w:style w:type="paragraph" w:default="1" w:styleId="Normal"><w:name w:val="Normal"/></w:style>
        <w:style w:type="paragraph" w:styleId="Heading1"><w:name w:val="heading 1"/><w:basedOn w:val="Normal"/><w:pPr><w:outlineLvl w:val="0"/></w:pPr><w:rPr><w:b/><w:sz w:val="44"/></w:rPr></w:style>
        <w:style w:type="paragraph" w:styleId="Title"><w:name w:val="Title"/><w:rPr><w:sz w:val="56"/></w:rPr></w:style>
        <w:style w:type="character" w:styleId="Strong"><w:name w:val="Strong"/><w:rPr><w:b/></w:rPr></w:style>
    </w:styles>"""

    private val numbering = """<w:numbering $ns>
        <w:abstractNum w:abstractNumId="0">
            <w:lvl w:ilvl="0"><w:start w:val="1"/><w:numFmt w:val="decimal"/><w:lvlText w:val="%1."/><w:pPr><w:ind w:left="720" w:hanging="360"/></w:pPr></w:lvl>
            <w:lvl w:ilvl="1"><w:start w:val="1"/><w:numFmt w:val="lowerLetter"/><w:lvlText w:val="%1.%2)"/></w:lvl>
        </w:abstractNum>
        <w:abstractNum w:abstractNumId="1">
            <w:lvl w:ilvl="0"><w:numFmt w:val="bullet"/><w:lvlText w:val="&#xF0B7;"/><w:rPr><w:rFonts w:ascii="Symbol"/></w:rPr></w:lvl>
        </w:abstractNum>
        <w:num w:numId="1"><w:abstractNumId w:val="0"/></w:num>
        <w:num w:numId="2"><w:abstractNumId w:val="1"/></w:num>
    </w:numbering>"""

    private val rels = """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
        <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
        <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/numbering" Target="numbering.xml"/>
        <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/hyperlink" Target="https://example.com" TargetMode="External"/>
        <Relationship Id="rId4" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/image1.png"/>
        <Relationship Id="rId5" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/footnotes" Target="footnotes.xml"/>
    </Relationships>"""

    private fun docx(body: String, extra: Map<String, String> = emptyMap()): FlowDocument {
        val file = temp.newFile("test-${System.nanoTime()}.docx")
        val parts = mapOf(
            "[Content_Types].xml" to """<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"/>""",
            "_rels/.rels" to """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>""",
            "word/_rels/document.xml.rels" to rels,
            "word/styles.xml" to styles,
            "word/numbering.xml" to numbering,
            "word/document.xml" to """<w:document $ns><w:body>$body<w:sectPr/></w:body></w:document>""",
            "word/media/image1.png" to "PNG",
        ) + extra
        ZipOutputStream(file.outputStream()).use { zip ->
            parts.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
        return DocxParser.parse(file)
    }

    private fun FlowDocument.paragraphs() = blocks.filterIsInstance<Paragraph>()

    @Test
    fun `headings and title come from styles`() {
        val doc = docx(
            """<w:p><w:pPr><w:pStyle w:val="Title"/></w:pPr><w:r><w:t>Big title</w:t></w:r></w:p>
               <w:p><w:pPr><w:pStyle w:val="Heading1"/></w:pPr><w:r><w:t>Chapter</w:t></w:r></w:p>
               <w:p><w:r><w:t>Body</w:t></w:r></w:p>""",
        )
        val (title, heading, body) = doc.paragraphs()
        assertEquals(ParagraphStyle.TITLE_LEVEL, title.style.headingLevel)
        assertEquals(1, heading.style.headingLevel)
        val headingRun = heading.inlines.single() as TextRun
        assertTrue(headingRun.style.bold)
        assertEquals(2f, headingRun.style.relativeSize)
        assertEquals(0, body.style.headingLevel)
        assertNull((body.inlines.single() as TextRun).style.relativeSize)
    }

    @Test
    fun `run formatting merges character style and direct properties`() {
        val doc = docx(
            """<w:p><w:pPr><w:jc w:val="center"/></w:pPr>
                 <w:r><w:rPr><w:rStyle w:val="Strong"/><w:i/></w:rPr><w:t xml:space="preserve">bold italic </w:t></w:r>
                 <w:r><w:rPr><w:color w:val="FF0000"/><w:u w:val="single"/><w:vertAlign w:val="superscript"/></w:rPr><w:t>red</w:t></w:r>
                 <w:r><w:rPr><w:b w:val="0"/><w:highlight w:val="yellow"/></w:rPr><w:t>marked</w:t></w:r>
               </w:p>""",
        )
        val p = doc.paragraphs().single()
        assertEquals(TextAlignment.Center, p.style.alignment)
        val (first, second, third) = p.inlines.map { it as TextRun }
        assertTrue(first.style.bold && first.style.italic)
        assertEquals(0xFFFF0000.toInt(), second.style.color)
        assertTrue(second.style.underline)
        assertEquals(VerticalAlign.Superscript, second.style.verticalAlign)
        assertEquals(false, third.style.bold)
        assertEquals(0xFFFFFF00.toInt(), third.style.background)
    }

    @Test
    fun `hyperlinks from relationships and from fields`() {
        val doc = docx(
            """<w:p><w:hyperlink r:id="rId3"><w:r><w:t>site</w:t></w:r></w:hyperlink></w:p>
               <w:p><w:r><w:fldChar w:fldCharType="begin"/></w:r><w:r><w:instrText xml:space="preserve"> HYPERLINK "https://kotlinlang.org" </w:instrText></w:r>
                 <w:r><w:fldChar w:fldCharType="separate"/></w:r><w:r><w:t>kotlin</w:t></w:r><w:r><w:fldChar w:fldCharType="end"/></w:r><w:r><w:t xml:space="preserve"> after</w:t></w:r></w:p>""",
        )
        val (a, b) = doc.paragraphs()
        assertEquals("https://example.com", (a.inlines.single() as TextRun).link)
        assertEquals("kotlin after", b.plainText)
        assertEquals("https://kotlinlang.org", (b.inlines.first() as TextRun).link)
        assertNull((b.inlines.last() as TextRun).link)
    }

    @Test
    fun `numbered and bulleted lists get markers`() {
        val item = { num: Int, lvl: Int, text: String -> """<w:p><w:pPr><w:numPr><w:ilvl w:val="$lvl"/><w:numId w:val="$num"/></w:numPr></w:pPr><w:r><w:t>$text</w:t></w:r></w:p>""" }
        val doc = docx(item(1, 0, "one") + item(1, 1, "nested") + item(1, 1, "nested2") + item(1, 0, "two") + item(1, 1, "again") + item(2, 0, "dot"))
        val markers = doc.paragraphs().map { it.style.listMarker }
        assertEquals(listOf("1.", "1.a)", "1.b)", "2.", "2.a)", "•"), markers)
        assertEquals(18f, doc.paragraphs().first().style.indentStartPt)
    }

    @Test
    fun `tables keep spans, merges and nested content`() {
        val cell = { text: String, props: String -> """<w:tc><w:tcPr>$props</w:tcPr><w:p><w:r><w:t>$text</w:t></w:r></w:p></w:tc>""" }
        val doc = docx(
            """<w:tbl><w:tblGrid><w:gridCol w:w="2000"/><w:gridCol w:w="4000"/></w:tblGrid>
                 <w:tr><w:trPr><w:tblHeader/></w:trPr>${cell("wide", """<w:gridSpan w:val="2"/>""")}</w:tr>
                 <w:tr>${cell("a", """<w:vMerge w:val="restart"/>""")}${cell("b", """<w:shd w:fill="00FF00"/>""")}</w:tr>
                 <w:tr>${cell("", "<w:vMerge/>")}${cell("c", "")}</w:tr>
               </w:tbl><w:p><w:r><w:t>after</w:t></w:r></w:p>""",
        )
        val table = assertIs<Table>(doc.blocks[0])
        assertEquals(listOf(100f, 200f), table.columnWidths)
        assertEquals(2, table.columnCount)
        assertTrue(table.rows[0].isHeader)
        assertEquals(2, table.rows[0].cells[0].colSpan)
        assertEquals(0xFF00FF00.toInt(), table.rows[1].cells[1].background)
        assertTrue(table.rows[2].cells[0].mergedWithAbove)
        assertEquals("c", (table.rows[2].cells[1].blocks.single() as Paragraph).plainText)
        assertEquals("after", (doc.blocks[1] as Paragraph).plainText)
    }

    @Test
    fun `images, page breaks, tracked deletions and fallback content`() {
        val doc = docx(
            """<w:p><w:r><w:drawing><wp:inline><wp:extent cx="1270000" cy="635000"/><wp:docPr id="1" name="Pic" descr="A chart"/>
                 <a:graphic><a:graphicData><a:blip r:embed="rId4"/></a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p>
               <w:p><w:r><w:br w:type="page"/></w:r><w:r><w:t>new page</w:t></w:r></w:p>
               <w:p><w:r><w:t xml:space="preserve">keep </w:t></w:r><w:del><w:r><w:delText>removed</w:delText></w:r></w:del><w:ins><w:r><w:t>added</w:t></w:r></w:ins></w:p>
               <w:p><mc:AlternateContent><mc:Choice Requires="wps"><w:r><w:t>choice</w:t></w:r></mc:Choice><mc:Fallback><w:r><w:t>fallback</w:t></w:r></mc:Fallback></mc:AlternateContent></w:p>""",
        )
        val image = (doc.blocks[0] as Paragraph).inlines.single() as ImageRun
        assertEquals("word/media/image1.png", image.imageId)
        assertEquals(100f, image.widthPt)
        assertEquals(50f, image.heightPt)
        assertEquals("A chart", image.description)
        assertEquals("PNG", doc.images.open(image.imageId)!!.decodeToString())
        assertEquals(PageBreak, doc.blocks[1])
        assertEquals("new page", (doc.blocks[2] as Paragraph).plainText)
        assertEquals("keep added", (doc.blocks[3] as Paragraph).plainText)
        assertEquals("choice", (doc.blocks[4] as Paragraph).plainText)
        doc.close()
    }

    @Test
    fun `footnotes are numbered and appended after a divider`() {
        val footnotes = """<w:footnotes $ns>
            <w:footnote w:type="separator" w:id="-1"><w:p><w:r><w:separator/></w:r></w:p></w:footnote>
            <w:footnote w:id="5"><w:p><w:r><w:rPr><w:vertAlign w:val="superscript"/></w:rPr><w:footnoteRef/></w:r><w:r><w:t xml:space="preserve"> The source.</w:t></w:r></w:p></w:footnote>
        </w:footnotes>"""
        val doc = docx("""<w:p><w:r><w:t>Claim</w:t></w:r><w:r><w:footnoteReference w:id="5"/></w:r></w:p>""", mapOf("word/footnotes.xml" to footnotes))
        assertEquals("Claim1", (doc.blocks[0] as Paragraph).plainText)
        assertEquals(Divider, doc.blocks[1])
        assertEquals("1 The source.", (doc.blocks[2] as Paragraph).plainText)
    }

    @Test
    fun `document produced by macOS textutil`() {
        // textutil writes tables as plain paragraphs and links as styled text, so only text and sizes are checked here.
        val doc = DocxParser.parse(resource("textutil.docx"))
        val heading = doc.paragraphs().first { it.plainText == "Отчёт о продажах" }
        assertEquals(2f, (heading.inlines.single() as TextRun).style.relativeSize)
        val body = doc.paragraphs().first { it.plainText.startsWith("Это") }
        assertNull((body.inlines.first() as TextRun).style.relativeSize, "body text must be normalized to 1.0")
        assertTrue(doc.paragraphs().any { it.plainText == "Яблоки" })
        assertEquals(1, FlowSearch.search(doc, "NEEDLE").size)
    }
}

internal fun resource(name: String): File = File(requireNotNull(DocxParserTest::class.java.classLoader.getResource(name)) { "missing $name" }.toURI())
