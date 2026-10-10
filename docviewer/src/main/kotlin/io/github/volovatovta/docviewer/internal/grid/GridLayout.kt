package io.github.volovatovta.docviewer.internal.grid

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.volovatovta.docviewer.core.CsvParser
import io.github.volovatovta.docviewer.core.model.TableSource

internal object GridMetrics {
    val CellHorizontalPadding = 12.dp
    val CellVerticalPadding = 9.dp
    val RowNumberHorizontalPadding = 6.dp
    val MinColumnWidth = 56.dp
    val MaxColumnWidth = 300.dp

    // A text laid out at exactly its measured width can still wrap, hence the safety margin.
    val MeasureSafety = 2.dp
    val BorderWidth = 1.dp
    val CellTextSize = 14.sp
    val RowNumberTextSize = 12.sp
    const val HEADER_MAX_LINES = 2
    const val CELL_MAX_LINES = 3
    const val COLUMN_BORDER_ALPHA = 0.6f
    const val TABULAR_FIGURES = "tnum"
}

private object ColumnSampling {
    const val SAMPLED_ROWS = 200
    const val MAX_MEASURED_CHARS = 80

    // Spreadsheet-like right alignment once most of a column's values are numbers.
    const val NUMERIC_SHARE = 0.8
    const val ROW_NUMBER_PADDING_FACTOR = 1.5f
}

// Column widths and offsets in pixels; columnX has one extra entry for the right edge of the last column.
@Immutable
internal class GridLayout(
    val widths: FloatArray,
    val columnX: FloatArray,
    val numeric: BooleanArray,
    val rowNumberWidth: Float,
) {
    val columnCount: Int get() = widths.size
    val contentWidth: Float get() = rowNumberWidth + columnX.last()
}

@Composable
internal fun rememberGridLayout(
    document: TableSource,
    cellStyle: TextStyle,
    headerStyle: TextStyle,
    showRowNumbers: Boolean,
): GridLayout {
    val measurer = rememberTextMeasurer(cacheSize = 0)
    val density = LocalDensity.current
    return remember(document, cellStyle, headerStyle, showRowNumbers) {
        density.measureGrid(document, GridStyles(measurer, cellStyle, headerStyle), showRowNumbers)
    }
}

private class GridStyles(val measurer: TextMeasurer, val cell: TextStyle, val header: TextStyle)

private fun Density.measureGrid(document: TableSource, styles: GridStyles, showRowNumbers: Boolean): GridLayout {
    val columns = document.columnCount.coerceAtLeast(1)
    val sample = sampleRows(document)
    val widths = FloatArray(columns) { column -> columnWidth(column, document.header, sample, styles) }
    val numeric = BooleanArray(columns) { column -> sample.isNumericColumn(column) }
    val columnX = FloatArray(columns + 1)
    widths.forEachIndexed { index, width -> columnX[index + 1] = columnX[index] + width }
    val rowNumberWidth = if (showRowNumbers) rowNumberWidth(document.rowCount, styles) else 0f
    return GridLayout(widths, columnX, numeric, rowNumberWidth)
}

// Rows from both ends: ids and counters are often widest at the end of a table.
private fun sampleRows(document: TableSource): List<List<String>> {
    val half = ColumnSampling.SAMPLED_ROWS / 2
    val head = 0 until minOf(document.rowCount, half)
    val tail = maxOf(head.last + 1, document.rowCount - half) until document.rowCount
    return (head + tail).map(document::row)
}

private fun Density.columnWidth(
    column: Int,
    header: List<String>?,
    sample: List<List<String>>,
    styles: GridStyles,
): Float {
    val headerWidth = header?.getOrNull(column)?.let { styles.measurer.singleLineWidth(it, styles.header) } ?: 0f
    val widestCell = sample.maxOfOrNull { row ->
        val firstLine = row.getOrNull(column)?.takeIf { it.isNotEmpty() }?.lineSequence()?.first()
        val measured = firstLine?.take(ColumnSampling.MAX_MEASURED_CHARS)
        measured?.let { styles.measurer.singleLineWidth(it, styles.cell) } ?: 0f
    } ?: 0f
    val padding = GridMetrics.CellHorizontalPadding.toPx() * 2 + GridMetrics.MeasureSafety.toPx()
    return (maxOf(headerWidth, widestCell) + padding)
        .coerceIn(GridMetrics.MinColumnWidth.toPx(), GridMetrics.MaxColumnWidth.toPx())
}

private fun List<List<String>>.isNumericColumn(column: Int): Boolean {
    val values = mapNotNull { it.getOrNull(column)?.takeIf(String::isNotBlank) }
    return values.isNotEmpty() && values.count(CsvParser::isNumeric) >= values.size * ColumnSampling.NUMERIC_SHARE
}

private fun Density.rowNumberWidth(rowCount: Int, styles: GridStyles): Float {
    val digitStyle = styles.cell.copy(fontFamily = FontFamily.Monospace)
    val digits = styles.measurer.singleLineWidth(rowCount.toString(), digitStyle)
    return digits + GridMetrics.CellHorizontalPadding.toPx() * ColumnSampling.ROW_NUMBER_PADDING_FACTOR
}

private fun TextMeasurer.singleLineWidth(text: String, style: TextStyle): Float =
    measure(text, style, maxLines = 1).size.width.toFloat()
