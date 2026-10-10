package io.github.volovatovta.docviewer.core

import io.github.volovatovta.docviewer.core.DocxFixtures.namespaces
import io.github.volovatovta.docviewer.core.DocxFixtures.relationship
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
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DocxParserTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val styles = """
        <w:styles $namespaces>
        <w:docDefaults><w:rPrDefault><w:rPr><w:sz w:val="22"/></w:rPr></w:rPrDefault></w:docDefaults>
        <w:style w:type="paragraph" w:default="1" w:styleId="Normal"><w:name w:val="Normal"/></w:style>
        <w:style w:type="paragraph" w:styleId="Heading1"><w:name w:val="heading 1"/><w:basedOn w:val="Normal"/><w:pPr><w:outlineLvl w:val="0"/></w:pPr><w:rPr><w:b/><w:sz w:val="44"/></w:rPr></w:style>
        <w:style w:type="paragraph" w:styleId="Title"><w:name w:val="Title"/><w:rPr><w:sz w:val="56"/></w:rPr></w:style>
        <w:style w:type="character" w:styleId="Strong"><w:name w:val="Strong"/><w:rPr><w:b/></w:rPr></w:style>
        </w:styles>
    """.trimIndent()

    private val numbering = """
        <w:numbering $namespaces>
        <w:abstractNum w:abstractNumId="0">
            <w:lvl w:ilvl="0"><w:start w:val="1"/><w:numFmt w:val="decimal"/><w:lvlText w:val="%1."/><w:pPr><w:ind w:left="720" w:hanging="360"/></w:pPr></w:lvl>
            <w:lvl w:ilvl="1"><w:start w:val="1"/><w:numFmt w:val="lowerLetter"/><w:lvlText w:val="%1.%2)"/></w:lvl>
        </w:abstractNum>
        <w:abstractNum w:abstractNumId="1">
            <w:lvl w:ilvl="0"><w:numFmt w:val="bullet"/><w:lvlText w:val="&#xF0B7;"/><w:rPr><w:rFonts w:ascii="Symbol"/></w:rPr></w:lvl>
        </w:abstractNum>
        <w:num w:numId="1"><w:abstractNumId w:val="0"/></w:num>
        <w:num w:numId="2"><w:abstractNumId w:val="1"/></w:num>
        </w:numbering>
    """.trimIndent()

    private val documentRelationships = DocxFixtures.relationships(
        relationship("rId1", "styles", "styles.xml"),
        relationship("rId2", "numbering", "numbering.xml"),
        relationship("rId3", "hyperlink", "https://example.com", external = true),
        relationship("rId4", "image", "media/image1.png"),
        relationship("rId5", "footnotes", "footnotes.xml"),
    )

    private fun docx(body: String, extraParts: Map<String, String> = emptyMap()): FlowDocument {
        val file = temp.newFile("test-${System.nanoTime()}.docx")
        val parts = DocxFixtures.packageParts() + mapOf(
            "word/_rels/document.xml.rels" to documentRelationships,
            "word/styles.xml" to styles,
            "word/numbering.xml" to numbering,
            "word/document.xml" to DocxFixtures.document(body),
            "word/media/image1.png" to "PNG",
        ) + extraParts
        DocxFixtures.write(file, parts)
        return DocxParser.parse(file)
    }

    private fun FlowDocument.paragraphs() = blocks.filterIsInstance<Paragraph>()

    private fun Paragraph.runs() = inlines.map { it as TextRun }

    @Test
    fun `headings and title come from styles`() {
        val doc = docx(
            """
            <w:p><w:pPr><w:pStyle w:val="Title"/></w:pPr><w:r><w:t>Big title</w:t></w:r></w:p>
            <w:p><w:pPr><w:pStyle w:val="Heading1"/></w:pPr><w:r><w:t>Chapter</w:t></w:r></w:p>
            <w:p><w:r><w:t>Body</w:t></w:r></w:p>
            """.trimIndent(),
        )
        val (title, heading, body) = doc.paragraphs()
        assertEquals(ParagraphStyle.TITLE_LEVEL, title.style.headingLevel)
        assertEquals(1, heading.style.headingLevel)
        val headingRun = heading.runs().single()
        assertTrue(headingRun.style.bold)
        assertEquals(2f, headingRun.style.relativeSize)
        assertEquals(0, body.style.headingLevel)
        assertNull(body.runs().single().style.relativeSize)
    }

    @Test
    fun `run formatting merges character style and direct properties`() {
        val doc = docx(
            """
            <w:p><w:pPr><w:jc w:val="center"/></w:pPr>
              <w:r><w:rPr><w:rStyle w:val="Strong"/><w:i/></w:rPr><w:t xml:space="preserve">bold italic </w:t></w:r>
              <w:r><w:rPr><w:color w:val="FF0000"/><w:u w:val="single"/><w:vertAlign w:val="superscript"/></w:rPr><w:t>red</w:t></w:r>
              <w:r><w:rPr><w:b w:val="0"/><w:highlight w:val="yellow"/></w:rPr><w:t>marked</w:t></w:r>
            </w:p>
            """.trimIndent(),
        )
        val paragraph = doc.paragraphs().single()
        assertEquals(TextAlignment.Center, paragraph.style.alignment)
        val (boldItalic, red, marked) = paragraph.runs()
        assertTrue(boldItalic.style.bold && boldItalic.style.italic)
        assertEquals(0xFFFF0000.toInt(), red.style.color)
        assertTrue(red.style.underline)
        assertEquals(VerticalAlign.Superscript, red.style.verticalAlign)
        assertEquals(false, marked.style.bold)
        assertEquals(0xFFFFFF00.toInt(), marked.style.background)
    }

    @Test
    fun `hyperlinks from relationships and from fields`() {
        val doc = docx(
            """
            <w:p><w:hyperlink r:id="rId3"><w:r><w:t>site</w:t></w:r></w:hyperlink></w:p>
            <w:p>
              <w:r><w:fldChar w:fldCharType="begin"/></w:r>
              <w:r><w:instrText xml:space="preserve"> HYPERLINK "https://kotlinlang.org" </w:instrText></w:r>
              <w:r><w:fldChar w:fldCharType="separate"/></w:r>
              <w:r><w:t>kotlin</w:t></w:r>
              <w:r><w:fldChar w:fldCharType="end"/></w:r>
              <w:r><w:t xml:space="preserve"> after</w:t></w:r>
            </w:p>
            """.trimIndent(),
        )
        val (relationshipLink, fieldLink) = doc.paragraphs()
        assertEquals("https://example.com", relationshipLink.runs().single().link)
        assertEquals("kotlin after", fieldLink.plainText)
        assertEquals("https://kotlinlang.org", fieldLink.runs().first().link)
        assertNull(fieldLink.runs().last().link)
    }

    @Test
    fun `numbered and bulleted lists get markers`() {
        val items = listOf(1 to 0, 1 to 1, 1 to 1, 1 to 0, 1 to 1, 2 to 0)
        val doc = docx(items.joinToString("") { (list, level) -> listItem(list, level) })
        val markers = doc.paragraphs().map { it.style.listMarker }
        assertEquals(listOf("1.", "1.a)", "1.b)", "2.", "2.a)", "•"), markers)
        assertEquals(18f, doc.paragraphs().first().style.indentStartPt)
    }

    @Test
    fun `tables keep spans, merges and nested content`() {
        val doc = docx(
            """
            <w:tbl><w:tblGrid><w:gridCol w:w="2000"/><w:gridCol w:w="4000"/></w:tblGrid>
              <w:tr><w:trPr><w:tblHeader/></w:trPr>${cell("wide", "<w:gridSpan w:val=\"2\"/>")}</w:tr>
              <w:tr>${cell("a", "<w:vMerge w:val=\"restart\"/>")}${cell("b", "<w:shd w:fill=\"00FF00\"/>")}</w:tr>
              <w:tr>${cell("", "<w:vMerge/>")}${cell("c", "")}</w:tr>
            </w:tbl>
            <w:p><w:r><w:t>after</w:t></w:r></w:p>
            """.trimIndent(),
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
            """
            <w:p><w:r><w:drawing><wp:inline>
              <wp:extent cx="1270000" cy="635000"/><wp:docPr id="1" name="Pic" descr="A chart"/>
              <a:graphic><a:graphicData><a:blip r:embed="rId4"/></a:graphicData></a:graphic>
            </wp:inline></w:drawing></w:r></w:p>
            <w:p><w:r><w:br w:type="page"/></w:r><w:r><w:t>new page</w:t></w:r></w:p>
            <w:p>
              <w:r><w:t xml:space="preserve">keep </w:t></w:r>
              <w:del><w:r><w:delText>removed</w:delText></w:r></w:del>
              <w:ins><w:r><w:t>added</w:t></w:r></w:ins>
            </w:p>
            <w:p><mc:AlternateContent>
              <mc:Choice Requires="wps"><w:r><w:t>choice</w:t></w:r></mc:Choice>
              <mc:Fallback><w:r><w:t>fallback</w:t></w:r></mc:Fallback>
            </mc:AlternateContent></w:p>
            """.trimIndent(),
        )
        val image = (doc.blocks[0] as Paragraph).inlines.single() as ImageRun
        assertEquals("word/media/image1.png", image.imageId)
        assertEquals(100f, image.widthPt)
        assertEquals(50f, image.heightPt)
        assertEquals("A chart", image.description)
        assertEquals("PNG", assertNotNull(doc.images.open(image.imageId)).decodeToString())
        assertEquals(PageBreak, doc.blocks[1])
        assertEquals("new page", (doc.blocks[2] as Paragraph).plainText)
        assertEquals("keep added", (doc.blocks[3] as Paragraph).plainText)
        assertEquals("choice", (doc.blocks[4] as Paragraph).plainText)
        doc.close()
    }

    @Test
    fun `footnotes are numbered and appended after a divider`() {
        val footnotes = """
            <w:footnotes $namespaces>
            <w:footnote w:type="separator" w:id="-1"><w:p><w:r><w:separator/></w:r></w:p></w:footnote>
            <w:footnote w:id="5"><w:p>
              <w:r><w:rPr><w:vertAlign w:val="superscript"/></w:rPr><w:footnoteRef/></w:r>
              <w:r><w:t xml:space="preserve"> The source.</w:t></w:r>
            </w:p></w:footnote>
            </w:footnotes>
        """.trimIndent()
        val doc = docx(
            body = """<w:p><w:r><w:t>Claim</w:t></w:r><w:r><w:footnoteReference w:id="5"/></w:r></w:p>""",
            extraParts = mapOf("word/footnotes.xml" to footnotes),
        )
        assertEquals("Claim1", (doc.blocks[0] as Paragraph).plainText)
        assertEquals(Divider, doc.blocks[1])
        assertEquals("1 The source.", (doc.blocks[2] as Paragraph).plainText)
    }

    @Test
    fun `document produced by macOS textutil`() {
        // textutil writes tables as plain paragraphs and links as styled text, so only text and sizes are checked.
        val doc = DocxParser.parse(resource("textutil.docx"))
        val heading = doc.paragraphs().first { it.plainText == "Отчёт о продажах" }
        assertEquals(2f, heading.runs().single().style.relativeSize)
        val body = doc.paragraphs().first { it.plainText.startsWith("Это") }
        assertNull(body.runs().first().style.relativeSize, "body text must be normalized to 1.0")
        assertTrue(doc.paragraphs().any { it.plainText == "Яблоки" })
        assertEquals(1, FlowSearch.search(doc, "NEEDLE").size)
    }

    @Test
    fun `DOCTYPE declarations are rejected`() {
        // OOXML never declares a DOCTYPE; accepting one opens the door to XXE and entity-expansion bombs.
        val document = """
            <?xml version="1.0"?><!DOCTYPE w:document [<!ENTITY e "INJECTED">]>
            <w:document $namespaces><w:body><w:p><w:r><w:t>&e;</w:t></w:r></w:p><w:sectPr/></w:body></w:document>
        """.trimIndent()
        assertFailsWith<CorruptedDocumentException> { docx("", mapOf("word/document.xml" to document)) }
    }

    private fun listItem(list: Int, level: Int): String {
        val properties = "<w:pPr><w:numPr><w:ilvl w:val=\"$level\"/><w:numId w:val=\"$list\"/></w:numPr></w:pPr>"
        return "<w:p>$properties<w:r><w:t>item</w:t></w:r></w:p>"
    }

    private fun cell(text: String, properties: String): String =
        "<w:tc><w:tcPr>$properties</w:tcPr><w:p><w:r><w:t>$text</w:t></w:r></w:p></w:tc>"
}
