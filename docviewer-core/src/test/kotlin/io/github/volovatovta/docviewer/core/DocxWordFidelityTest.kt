package io.github.volovatovta.docviewer.core

import io.github.volovatovta.docviewer.core.DocxFixtures.namespaces
import io.github.volovatovta.docviewer.core.DocxFixtures.relationship
import io.github.volovatovta.docviewer.core.docx.DocxParser
import io.github.volovatovta.docviewer.core.model.FlowDocument
import io.github.volovatovta.docviewer.core.model.ImageRun
import io.github.volovatovta.docviewer.core.model.Paragraph
import io.github.volovatovta.docviewer.core.model.Table
import io.github.volovatovta.docviewer.core.model.TextAlignment
import io.github.volovatovta.docviewer.core.model.TextRun
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Behaviors checked against Word on calibre's demo.docx: lists, spacing, borders, drop caps, table styles, floats.
class DocxWordFidelityTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val styles = """
        <w:styles $namespaces>
        <w:docDefaults><w:pPrDefault><w:pPr><w:spacing w:line="276" w:lineRule="auto"/></w:pPr></w:pPrDefault></w:docDefaults>
        <w:style w:type="paragraph" w:default="1" w:styleId="Normal"><w:name w:val="Normal"/><w:pPr><w:ind w:firstLine="432"/></w:pPr></w:style>
        <w:style w:type="paragraph" w:styleId="ListParagraph"><w:name w:val="List Paragraph"/><w:basedOn w:val="Normal"/><w:pPr><w:ind w:left="720"/></w:pPr></w:style>
        <w:style w:type="paragraph" w:styleId="Title"><w:name w:val="Title"/><w:pPr><w:pBdr><w:bottom w:val="single" w:sz="8" w:space="4" w:color="4F81BD"/></w:pBdr></w:pPr></w:style>
        <w:style w:type="table" w:styleId="Shaded"><w:name w:val="Shaded"/>
            <w:tblStylePr w:type="firstRow"><w:rPr><w:b/><w:color w:val="FFFFFF"/></w:rPr><w:tcPr><w:shd w:val="clear" w:fill="4BACC6"/></w:tcPr></w:tblStylePr>
            <w:tblStylePr w:type="band1Horz"><w:tcPr><w:shd w:val="clear" w:fill="D3DFEE"/></w:tcPr></w:tblStylePr>
            <w:tblStylePr w:type="lastRow"><w:tcPr><w:shd w:val="clear" w:fill="EEEEEE"/></w:tcPr></w:tblStylePr>
        </w:style>
        </w:styles>
    """.trimIndent()

    private val numbering = """
        <w:numbering $namespaces>
        <w:abstractNum w:abstractNumId="0">
            <w:lvl w:ilvl="0"><w:numFmt w:val="decimal"/><w:lvlText w:val="%1."/><w:pPr><w:ind w:left="360" w:hanging="360"/></w:pPr></w:lvl>
            <w:lvl w:ilvl="1"><w:numFmt w:val="decimal"/><w:lvlText w:val="%1.%2."/><w:pPr><w:ind w:left="792" w:hanging="432"/></w:pPr></w:lvl>
        </w:abstractNum>
        <w:num w:numId="1"><w:abstractNumId w:val="0"/></w:num>
        </w:numbering>
    """.trimIndent()

    private fun docx(body: String): FlowDocument {
        val file = temp.newFile("fidelity-${System.nanoTime()}.docx")
        DocxFixtures.write(
            file,
            DocxFixtures.packageParts() + mapOf(
                "word/_rels/document.xml.rels" to DocxFixtures.relationships(
                    relationship("rId1", "styles", "styles.xml"),
                    relationship("rId2", "numbering", "numbering.xml"),
                    relationship("rId4", "image", "media/image1.png"),
                ),
                "word/styles.xml" to styles,
                "word/numbering.xml" to numbering,
                "word/document.xml" to DocxFixtures.document(body),
                "word/media/image1.png" to "PNG",
            ),
        )
        return DocxParser.parse(file)
    }

    private fun paragraph(text: String, properties: String = "") =
        "<w:p><w:pPr>$properties</w:pPr><w:r><w:t>$text</w:t></w:r></w:p>"

    @Test
    fun `list levels indent by the numbering definition, not by the list paragraph style`() {
        val item = { level: Int ->
            "<w:pStyle w:val=\"ListParagraph\"/><w:numPr><w:ilvl w:val=\"$level\"/><w:numId w:val=\"1\"/></w:numPr>"
        }
        val ownIndent = "<w:ind w:left=\"1440\" w:hanging=\"360\"/>"
        val body = paragraph("one", item(0)) + paragraph("two", item(1)) + paragraph("own", item(1) + ownIndent)
        val document = docx(body)
        val indents = document.blocks.map { (it as Paragraph).style.indentStartPt }
        assertEquals(listOf(0f, 18f, 54f), indents)
    }

    @Test
    fun `spacing is zero unless set and line spacing comes from the document`() {
        val style = (docx(paragraph("text")).blocks.single() as Paragraph).style
        assertEquals(0f, style.spaceBeforePt)
        assertEquals(0f, style.spaceAfterPt)
        assertEquals(1.15f, assertNotNull(style.lineSpacing), 0.001f)
        assertEquals(21.6f, style.firstLineIndentPt, 0.01f)
    }

    @Test
    fun `paragraph borders come from styles and direct properties`() {
        val document = docx(
            paragraph("Title", "<w:pStyle w:val=\"Title\"/>") +
                paragraph("boxed", "<w:pBdr><w:right w:val=\"single\" w:sz=\"4\" w:color=\"auto\"/></w:pBdr>"),
        )
        val (title, boxed) = document.blocks.map { (it as Paragraph).style.borders }
        assertEquals(1f, assertNotNull(title?.bottom).widthPt)
        assertEquals(0xFF4F81BD.toInt(), title?.bottom?.color)
        assertNull(title?.top)
        assertEquals(0.5f, assertNotNull(boxed?.end).widthPt)
        assertNull(boxed?.end?.color)
    }

    @Test
    fun `a drop cap becomes the first letter of the next paragraph`() {
        val frame = "<w:pPr><w:framePr w:dropCap=\"drop\" w:lines=\"3\"/></w:pPr>"
        val dropCap = "<w:p>$frame<w:r><w:rPr><w:sz w:val=\"117\"/></w:rPr><w:t>D</w:t></w:r></w:p>"
        val paragraphs = docx(dropCap + paragraph("rop caps are big.")).blocks
        val merged = paragraphs.single() as Paragraph
        assertEquals("Drop caps are big.", merged.plainText)
        assertTrue(assertNotNull((merged.inlines.first() as TextRun).style.relativeSize) > 2f)
    }

    @Test
    fun `table styles shade the header row, bands and the last row`() {
        val row = { text: String -> "<w:tr><w:tc><w:p><w:r><w:t>$text</w:t></w:r></w:p></w:tc></w:tr>" }
        val look = "<w:tblLook w:firstRow=\"1\" w:lastRow=\"1\" w:noHBand=\"0\"/>"
        val body = "<w:tbl><w:tblPr><w:tblStyle w:val=\"Shaded\"/>$look</w:tblPr>" +
            row("Head") + row("odd") + row("even") + row("Total") + "</w:tbl>"
        val table = docx(body).blocks.single() as Table
        val backgrounds = table.rows.map { it.cells.single().background }
        assertEquals(listOf(0xFF4BACC6.toInt(), 0xFFD3DFEE.toInt(), null, 0xFFEEEEEE.toInt()), backgrounds)
        val header = (table.rows.first().cells.single().blocks.single() as Paragraph).inlines.single() as TextRun
        assertTrue(header.style.bold)
        assertEquals(0xFFFFFFFF.toInt(), header.style.color)
    }

    @Test
    fun `floating images keep their horizontal alignment, inline ones have none`() {
        val picture = "<wp:extent cx=\"127000\" cy=\"127000\"/>" +
            "<a:graphic><a:graphicData><a:blip r:embed=\"rId4\"/></a:graphicData></a:graphic>"
        val anchor = { align: String ->
            "<w:r><w:drawing><wp:anchor><wp:positionH relativeFrom=\"margin\">" +
                "<wp:align>$align</wp:align></wp:positionH>" +
                "$picture</wp:anchor></w:drawing></w:r>"
        }
        val inline = "<w:r><w:drawing><wp:inline>$picture</wp:inline></w:drawing></w:r>"
        val document = docx("<w:p>${anchor("center")}${anchor("right")}$inline</w:p>")
        val images = (document.blocks.single() as Paragraph).inlines.map { it as ImageRun }
        assertEquals(listOf(TextAlignment.Center, TextAlignment.End, null), images.map { it.floatAlignment })
    }
}
