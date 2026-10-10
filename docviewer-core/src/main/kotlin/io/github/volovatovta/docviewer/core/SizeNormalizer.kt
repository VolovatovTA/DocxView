package io.github.volovatovta.docviewer.core

import io.github.volovatovta.docviewer.core.model.Block
import io.github.volovatovta.docviewer.core.model.Inline
import io.github.volovatovta.docviewer.core.model.Paragraph
import io.github.volovatovta.docviewer.core.model.Table
import io.github.volovatovta.docviewer.core.model.TableCell
import io.github.volovatovta.docviewer.core.model.TableRow
import io.github.volovatovta.docviewer.core.model.TextRun
import kotlin.math.abs

// Rescales run sizes so the size most of the text is set in becomes 1.0, i.e. the viewer's body text size.
internal object SizeNormalizer {

    private const val NORMAL_SIZE = 1f
    private const val SIZE_TOLERANCE = 0.05f
    private val plausibleBodySizes = 0.6f..1.6f

    fun normalize(blocks: List<Block>): List<Block> {
        val base = dominantBodySize(blocks) ?: return blocks
        return blocks.map { it.rescaled(base) }
    }

    // Only a clear majority in a plausible body-text range counts; tiny or heading-heavy docs stay as they are.
    private fun dominantBodySize(blocks: List<Block>): Float? {
        val weights = blocks.asSequence()
            .flatMap(::bodyRuns)
            .filter { it.text.isNotBlank() }
            .groupingBy { it.style.relativeSize ?: NORMAL_SIZE }
            .fold(0) { total, run -> total + run.text.length }
        val (size, weight) = weights.maxByOrNull { it.value }?.toPair() ?: return null
        val isMajority = weight * 2 >= weights.values.sum()
        return size.takeIf { isMajority && it in plausibleBodySizes && !it.isNormal() }
    }

    private fun bodyRuns(block: Block): Sequence<TextRun> = when (block) {
        is Paragraph -> if (block.style.headingLevel == 0) block.textRuns() else emptySequence()
        is Table -> block.rows.asSequence().flatMap { it.cells }.flatMap { it.blocks }.flatMap(::bodyRuns)
        else -> emptySequence()
    }

    private fun Paragraph.textRuns(): Sequence<TextRun> = inlines.asSequence().filterIsInstance<TextRun>()

    private fun Block.rescaled(base: Float): Block = when (this) {
        is Paragraph -> Paragraph(inlines.map { it.rescaled(base) }, style)
        is Table -> Table(rows.map { it.rescaled(base) }, columnWidths)
        else -> this
    }

    private fun TableRow.rescaled(base: Float) = TableRow(cells.map { it.rescaled(base) }, isHeader)

    private fun TableCell.rescaled(base: Float) =
        TableCell(blocks.map { it.rescaled(base) }, colSpan, mergedWithAbove, background)

    private fun Inline.rescaled(base: Float): Inline {
        if (this !is TextRun) return this
        val size = (style.relativeSize ?: NORMAL_SIZE) / base
        return TextRun(text, style.copy(relativeSize = size.takeUnless { it.isNormal() }), link)
    }

    private fun Float.isNormal() = abs(this - NORMAL_SIZE) <= SIZE_TOLERANCE
}
