package io.github.volovatovta.docviewer.internal.flow

import io.github.volovatovta.docviewer.core.FlowSearch
import io.github.volovatovta.docviewer.core.model.FlowDocument
import io.github.volovatovta.docviewer.core.model.ImageRun
import io.github.volovatovta.docviewer.core.model.Paragraph
import io.github.volovatovta.docviewer.core.model.RunStyle
import io.github.volovatovta.docviewer.core.model.Table
import io.github.volovatovta.docviewer.core.model.TableCell
import io.github.volovatovta.docviewer.core.model.TableRow
import io.github.volovatovta.docviewer.core.model.TextRun
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FlowPlanTest {

    private fun paragraph(text: String) = Paragraph(listOf(TextRun(text)))

    private fun parts(document: FlowDocument) = FlowPlan.of(
        document
    ).items.map { assertIs<FlowItem.ParagraphPart>(it).piece }

    @Test
    fun `short paragraphs stay whole`() {
        val pieces = parts(FlowDocument(listOf(paragraph("Hello"), paragraph("World"))))
        assertEquals(2, pieces.size)
        assertTrue(pieces.all { it.isFirst && it.isLast })
    }

    @Test
    fun `a book-length paragraph is cut into small parts without losing a character`() {
        val text = "Lorem ipsum dolor sit amet, consectetur adipiscing elit. ".repeat(20_000)
        val pieces = parts(FlowDocument(listOf(paragraph(text))))
        assertTrue(pieces.size > 500)
        assertTrue(pieces.all { it.textLength <= ParagraphSplitter.PART_CHARS })
        val joined = pieces.joinToString("") { piece -> piece.inlines.joinToString("") { (it as TextRun).text } }
        assertEquals(text, joined)
        pieces.zipWithNext { a, b -> assertEquals(a.textOffset + a.textLength, b.textOffset) }
        val middle = pieces.drop(1).dropLast(1)
        assertTrue(pieces.first().isFirst && pieces.last().isLast && middle.none { it.isFirst || it.isLast })
    }

    @Test
    fun `cuts prefer forced line breaks and sentence ends`() {
        val line = "word ".repeat(40) + "\n"
        val byLines = parts(FlowDocument(listOf(paragraph(line.repeat(100)))))
        assertTrue(
            byLines.dropLast(1).all { it.inlines.joinToString("") { run -> (run as TextRun).text }.endsWith("\n") }
        )
        val sentences = "Short sentence here. ".repeat(500)
        val bySentence = parts(FlowDocument(listOf(paragraph(sentences))))
        assertTrue(bySentence.dropLast(1).all { (it.inlines.last() as TextRun).text.endsWith(". ") })
    }

    @Test
    fun `styles and images survive the cut`() {
        val bold = RunStyle(bold = true)
        val image = ImageRun("media/chart.png")
        val inlines = listOf(TextRun("a ".repeat(1_000), bold), image, TextRun("b ".repeat(1_000)))
        val pieces = parts(FlowDocument(listOf(Paragraph(inlines))))
        assertEquals(1, pieces.sumOf { piece -> piece.inlines.count { it === image } })
        assertTrue(pieces.first().inlines.all { (it as TextRun).style == bold })
    }

    @Test
    fun `tables become one item per row and navigation maps both ways`() {
        val cell = { text: String -> TableCell(listOf(paragraph(text))) }
        val table = Table(List(300) { TableRow(listOf(cell("row $it"), cell("needle $it"))) })
        val document = FlowDocument(listOf(paragraph("Intro"), table, paragraph("Outro")))
        val plan = FlowPlan.of(document)
        assertEquals(302, plan.items.size)
        assertEquals(1, plan.itemOf(1))
        assertEquals(301, plan.itemOf(2))
        assertEquals(1, plan.blockOf(150))
        val match = FlowSearch.search(document, "needle 250").single()
        assertEquals(1 + 250, plan.itemOf(match))
    }

    @Test
    fun `a search match is found in the right part of a long paragraph`() {
        val text = "filler text ".repeat(1_000) + "needle " + "more text ".repeat(1_000)
        val document = FlowDocument(listOf(paragraph(text)))
        val plan = FlowPlan.of(document)
        val match = FlowSearch.search(document, "needle").single()
        val part = assertIs<FlowItem.ParagraphPart>(plan.items[plan.itemOf(match)]).piece
        assertTrue(match.start in part.textOffset until part.textOffset + part.textLength)
    }
}
