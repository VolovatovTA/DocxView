package io.github.volovatovta.docviewer.internal.flow

import io.github.volovatovta.docviewer.core.FlowMatch
import io.github.volovatovta.docviewer.core.FlowSearch
import io.github.volovatovta.docviewer.core.model.Block
import io.github.volovatovta.docviewer.core.model.FlowDocument
import io.github.volovatovta.docviewer.core.model.ImageRun
import io.github.volovatovta.docviewer.core.model.Inline
import io.github.volovatovta.docviewer.core.model.Paragraph
import io.github.volovatovta.docviewer.core.model.Table
import io.github.volovatovta.docviewer.core.model.TextRun

// One entry of the lazy list; the document is flattened so that no single entry grows with the document.
internal sealed interface FlowItem {
    // The top-level block this entry belongs to, for navigation and search.
    val blockIndex: Int

    class WholeBlock(override val blockIndex: Int, val block: Block) : FlowItem

    class TableRowItem(override val blockIndex: Int, val table: Table, val row: Int) : FlowItem

    class ParagraphPart(override val blockIndex: Int, val piece: ParagraphPiece) : FlowItem
}

// A slice of a paragraph; textOffset maps it back to Paragraph.plainText for search highlights.
internal class ParagraphPiece(
    val source: Paragraph,
    val inlines: List<Inline>,
    val textOffset: Int,
    val isFirst: Boolean,
    val isLast: Boolean,
) {
    val textLength: Int = inlines.sumOf { (it as? TextRun)?.text?.length ?: 0 }

    companion object {
        fun whole(paragraph: Paragraph) = ParagraphPiece(paragraph, paragraph.inlines, 0, isFirst = true, isLast = true)
    }
}

internal class FlowPlan private constructor(val items: List<FlowItem>, private val firstItemOfBlock: IntArray) {

    fun itemOf(blockIndex: Int): Int =
        if (firstItemOfBlock.isEmpty()) 0 else firstItemOfBlock[blockIndex.coerceIn(0, firstItemOfBlock.lastIndex)]

    fun blockOf(itemIndex: Int): Int = items.getOrNull(itemIndex)?.blockIndex ?: 0

    // The exact part of a long paragraph or the table row that holds a search match.
    fun itemOf(match: FlowMatch): Int {
        val first = itemOf(match.blockIndex)
        val last = (first until items.size).lastOrNull { items[it].blockIndex == match.blockIndex } ?: first
        return (first..last).firstOrNull { items[it].holds(match) } ?: first
    }

    private fun FlowItem.holds(match: FlowMatch): Boolean = when (this) {
        is FlowItem.ParagraphPart ->
            piece.source === match.paragraph &&
                match.start >= piece.textOffset &&
                (match.start < piece.textOffset + piece.textLength || piece.isLast)
        is FlowItem.TableRowItem -> table.rows[row].cells.any { cell ->
            cell.blocks.any { block -> block.contains(match.paragraph) }
        }
        is FlowItem.WholeBlock -> false
    }

    private fun Block.contains(paragraph: Paragraph): Boolean {
        var found = false
        FlowSearch.forEachParagraph(this) { if (it === paragraph) found = true }
        return found
    }

    companion object {
        fun of(document: FlowDocument): FlowPlan {
            val items = ArrayList<FlowItem>(document.blocks.size)
            val firstItems = IntArray(document.blocks.size)
            document.blocks.forEachIndexed { index, block ->
                firstItems[index] = items.size
                items += itemsFor(index, block)
            }
            return FlowPlan(items, firstItems)
        }

        private fun itemsFor(index: Int, block: Block): List<FlowItem> = when (block) {
            is Table -> List(block.rows.size) { row -> FlowItem.TableRowItem(index, block, row) }
            is Paragraph -> ParagraphSplitter.split(block).map { FlowItem.ParagraphPart(index, it) }
            else -> listOf(FlowItem.WholeBlock(index, block))
        }
    }
}

// Long paragraphs are cut where a reader barely notices: forced line breaks, then sentence ends, then spaces.
internal object ParagraphSplitter {
    const val PART_CHARS = 800
    private const val SEARCH_WINDOW_DIVISOR = 2
    private val SentenceEnds = listOf(". ", "! ", "? ", "; ")

    fun split(paragraph: Paragraph): List<ParagraphPiece> {
        val text = paragraph.plainText
        if (text.length <= PART_CHARS) return listOf(ParagraphPiece.whole(paragraph))
        val cuts = cutPoints(text)
        return cuts.zipWithNext().mapIndexed { index, (start, end) ->
            val isLast = index == cuts.size - 2
            ParagraphPiece(paragraph, paragraph.inlinesBetween(start, end, isLast), start, index == 0, isLast)
        }
    }

    private fun cutPoints(text: String): List<Int> {
        val cuts = mutableListOf(0)
        while (text.length - cuts.last() > PART_CHARS) cuts += nextCut(text, cuts.last())
        cuts += text.length
        return cuts
    }

    // Searches only the window where a cut is allowed: an unbounded lastIndexOf makes long texts quadratic.
    private fun nextCut(text: String, start: Int): Int {
        val limit = start + PART_CHARS
        val earliest = start + PART_CHARS / SEARCH_WINDOW_DIVISOR
        val lineBreak = text.lastIndexIn("\n", earliest, limit)?.plus(1)
        val sentence = SentenceEnds.mapNotNull { text.lastIndexIn(it, earliest, limit) }.maxOrNull()?.plus(2)
        val space = text.lastIndexIn(" ", earliest, limit)?.plus(1)
        return lineBreak ?: sentence ?: space ?: limit
    }

    private fun String.lastIndexIn(needle: String, from: Int, to: Int): Int? {
        for (index in minOf(to, length - needle.length) downTo from) {
            if (regionMatches(index, needle, 0, needle.length)) return index
        }
        return null
    }

    // Images sit between characters; one exactly at a cut belongs to the following part.
    private fun Paragraph.inlinesBetween(start: Int, end: Int, includeEnd: Boolean): List<Inline> {
        val slice = ArrayList<Inline>()
        var position = 0
        for (inline in inlines) {
            when (inline) {
                is TextRun -> {
                    val runEnd = position + inline.text.length
                    val from = maxOf(start, position)
                    val to = minOf(end, runEnd)
                    if (from < to) slice += inline.slice(from - position, to - position)
                    position = runEnd
                }
                is ImageRun -> if (position >= start && position.isBefore(end, includeEnd)) slice += inline
            }
        }
        return slice
    }

    private fun TextRun.slice(from: Int, to: Int) = TextRun(text.substring(from, to), style, link)

    private fun Int.isBefore(end: Int, includeEnd: Boolean) = this < end || includeEnd && this == end
}
