package io.github.volovatovta.docviewer.internal.flow

import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import io.github.volovatovta.docviewer.core.CsvParser
import io.github.volovatovta.docviewer.core.model.Paragraph
import io.github.volovatovta.docviewer.core.model.Table
import io.github.volovatovta.docviewer.core.model.TableCell
import io.github.volovatovta.docviewer.core.model.TableRow

private object ColumnMeasuring {
    const val SAMPLED_ROWS = 60
    const val MAX_WORD_CHARS = 40
    const val MAX_LINE_CHARS = 160

    // A single column may claim at most this share of the screen when others need room too.
    const val MAX_COLUMN_SHARE = 0.8f
    val WordBoundary = Regex("\\s+")
}

// Numbers like "12 400" count as one word: splitting them would make the column unreadable.
internal fun measureColumns(table: Table, measurer: TextMeasurer, style: TextStyle, padding: Float): ColumnContent {
    val count = table.columnCount.coerceAtLeast(1)
    val content = ColumnContent(FloatArray(count), FloatArray(count))
    val headerStyle = style.copy(fontWeight = FontWeight.SemiBold)
    table.rows.take(ColumnMeasuring.SAMPLED_ROWS).forEach { row ->
        val rowStyle = if (row.isHeader) headerStyle else style
        row.singleColumnCells(count).forEach { (column, cell) ->
            cell.blocks.filterIsInstance<Paragraph>().forEach { paragraph ->
                content.include(column, paragraph.plainText.trim(), measurer, rowStyle, padding)
            }
        }
    }
    return content
}

// Cells spanning several columns say nothing about the width of any single one.
private fun TableRow.singleColumnCells(count: Int): List<Pair<Int, TableCell>> {
    var column = 0
    return cells.mapNotNull { cell ->
        val start = column
        column += cell.colSpan
        (start to cell).takeIf { cell.colSpan == 1 && start < count }
    }
}

private fun ColumnContent.include(column: Int, text: String, measurer: TextMeasurer, style: TextStyle, padding: Float) {
    if (text.isEmpty()) return
    val words = if (CsvParser.isNumeric(text)) {
        listOf(text)
    } else {
        text.split(ColumnMeasuring.WordBoundary).filter { it.isNotEmpty() }
    }
    val longestWord = words.maxByOrNull { it.length }.orEmpty().take(ColumnMeasuring.MAX_WORD_CHARS)
    min[column] = maxOf(min[column], measurer.singleLineWidth(longestWord, style) + padding)
    val line = text.take(ColumnMeasuring.MAX_LINE_CHARS)
    max[column] = maxOf(max[column], measurer.singleLineWidth(line, style) + padding)
}

private fun TextMeasurer.singleLineWidth(text: String, style: TextStyle): Int =
    measure(text, style, maxLines = 1).size.width

// HTML-like auto layout: keep the author's proportions when they fit the content, otherwise share space by need.
internal fun columnWidths(table: Table, content: ColumnContent, viewport: Float, minColumn: Float): FloatArray {
    val min = FloatArray(content.min.size) { maxOf(content.min[it], minColumn) }
    val max = FloatArray(content.max.size) {
        maxOf(content.max[it], min[it]).coerceAtMost(maxOf(viewport * ColumnMeasuring.MAX_COLUMN_SHARE, min[it]))
    }
    return authorWidths(table, viewport, min) ?: widthsByNeed(min, max, viewport)
}

private fun authorWidths(table: Table, viewport: Float, min: FloatArray): FloatArray? {
    if (table.columnWidths.size != min.size) return null
    val total = table.columnWidths.sum()
    val fitted = FloatArray(min.size) { viewport * table.columnWidths[it] / total }
    return fitted.takeIf { min.indices.all { fitted[it] >= min[it] } }
}

private fun widthsByNeed(min: FloatArray, max: FloatArray, viewport: Float): FloatArray {
    val count = min.size
    val minSum = min.sum()
    val maxSum = max.sum()
    return when {
        maxSum <= 0f -> FloatArray(count) { viewport / count }
        maxSum <= viewport -> FloatArray(count) { max[it] + (viewport - maxSum) * max[it] / maxSum }
        minSum <= viewport -> distributeSlack(min, max, extra = viewport - minSum)
        else -> min
    }
}

// Space beyond the minimums goes to the columns that would wrap the most.
private fun distributeSlack(min: FloatArray, max: FloatArray, extra: Float): FloatArray {
    val slack = max.sum() - min.sum()
    return FloatArray(min.size) { min[it] + if (slack > 0f) extra * (max[it] - min[it]) / slack else 0f }
}

// Per column: the narrowest width that never breaks a word, and the width of the longest line.
internal class ColumnContent(val min: FloatArray, val max: FloatArray)
