package io.github.volovatovta.docviewer.core

import io.github.volovatovta.docviewer.core.model.Block
import io.github.volovatovta.docviewer.core.model.Paragraph
import io.github.volovatovta.docviewer.core.model.Table
import io.github.volovatovta.docviewer.core.model.TableCell
import io.github.volovatovta.docviewer.core.model.TableRow
import io.github.volovatovta.docviewer.core.model.TextRun

// Rescales run sizes so the size most of the text is set in becomes 1.0, i.e. the viewer's body text size.
internal object SizeNormalizer {

    fun normalize(blocks: List<Block>): List<Block> {
        val weights = HashMap<Float, Int>()
        blocks.forEach { collect(it, weights) }
        val total = weights.values.sum()
        val (dominant, weight) = weights.maxByOrNull { it.value }?.toPair() ?: return blocks
        // Only a clear majority in a plausible body-text range counts; tiny or heading-heavy docs stay as they are.
        if (weight * 2 < total || dominant !in 0.6f..1.6f || kotlin.math.abs(dominant - 1f) < 0.05f) return blocks
        return blocks.map { rescale(it, dominant) }
    }

    private fun collect(block: Block, weights: HashMap<Float, Int>) {
        when (block) {
            is Paragraph -> if (block.style.headingLevel == 0) block.inlines.forEach { inline ->
                if (inline is TextRun && inline.text.isNotBlank()) {
                    val size = inline.style.relativeSize ?: 1f
                    weights[size] = (weights[size] ?: 0) + inline.text.length
                }
            }
            is Table -> block.rows.forEach { row -> row.cells.forEach { cell -> cell.blocks.forEach { collect(it, weights) } } }
            else -> Unit
        }
    }

    private fun rescale(block: Block, base: Float): Block = when (block) {
        is Paragraph -> Paragraph(
            block.inlines.map { inline ->
                if (inline !is TextRun) inline else {
                    val size = (inline.style.relativeSize ?: 1f) / base
                    TextRun(inline.text, inline.style.copy(relativeSize = size.takeIf { kotlin.math.abs(it - 1f) > 0.05f }), inline.link)
                }
            },
            block.style,
        )
        is Table -> Table(
            block.rows.map { row ->
                TableRow(row.cells.map { cell -> TableCell(cell.blocks.map { rescale(it, base) }, cell.colSpan, cell.mergedWithAbove, cell.background) }, row.isHeader)
            },
            block.columnWidths,
        )
        else -> block
    }
}
