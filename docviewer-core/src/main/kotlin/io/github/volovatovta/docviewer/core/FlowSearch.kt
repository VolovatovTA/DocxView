package io.github.volovatovta.docviewer.core

import io.github.volovatovta.docviewer.core.model.Block
import io.github.volovatovta.docviewer.core.model.FlowDocument
import io.github.volovatovta.docviewer.core.model.Paragraph
import io.github.volovatovta.docviewer.core.model.Table
import java.util.IdentityHashMap

public class FlowMatch(
    // Index of the top-level block to scroll to.
    public val blockIndex: Int,
    public val paragraph: Paragraph,
    public val start: Int,
    public val end: Int,
)

public object FlowSearch {

    public fun search(document: FlowDocument, query: String): List<FlowMatch> {
        if (query.isBlank()) return emptyList()
        return document.blocks.flatMapIndexed { index, block -> matchesIn(block, index, query) }
    }

    // Groups matches by paragraph identity so renderers can highlight them cheaply.
    public fun byParagraph(matches: List<FlowMatch>): Map<Paragraph, List<FlowMatch>> {
        val map = IdentityHashMap<Paragraph, MutableList<FlowMatch>>()
        for (match in matches) map.getOrPut(match.paragraph) { ArrayList() }.add(match)
        return map
    }

    public fun forEachParagraph(block: Block, action: (Paragraph) -> Unit) {
        when (block) {
            is Paragraph -> action(block)
            is Table -> block.nestedBlocks().forEach { forEachParagraph(it, action) }
            else -> Unit
        }
    }

    public inline fun findAll(text: String, query: String, onMatch: (start: Int, end: Int) -> Unit) {
        if (query.isEmpty() || text.length < query.length) return
        var at = text.indexOf(query, 0, ignoreCase = true)
        while (at >= 0) {
            onMatch(at, at + query.length)
            at = text.indexOf(query, at + query.length, ignoreCase = true)
        }
    }

    private fun matchesIn(block: Block, blockIndex: Int, query: String): List<FlowMatch> = buildList {
        forEachParagraph(block) { paragraph ->
            findAll(paragraph.plainText, query) { start, end -> add(FlowMatch(blockIndex, paragraph, start, end)) }
        }
    }

    private fun Table.nestedBlocks(): Sequence<Block> =
        rows.asSequence().flatMap { it.cells }.flatMap { it.blocks }
}
