package io.github.volovatovta.docviewer.internal.grid

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import io.github.volovatovta.docviewer.DocumentViewerColors
import kotlin.math.roundToInt

@Immutable
internal class GridRowAppearance(val textStyle: TextStyle, val background: Color, val maxLines: Int)

// Which columns of a row match the search, and which one holds the current match.
@Immutable
internal class RowHighlights(val columns: List<Int>?, val current: Int?) {
    fun colorOf(column: Int, colors: DocumentViewerColors): Color = when {
        current == column -> colors.currentSearchMatch
        columns?.contains(column) == true -> colors.searchMatch
        else -> Color.Transparent
    }

    companion object {
        val None = RowHighlights(columns = null, current = null)
    }
}

@Composable
internal fun GridRow(
    cells: List<String>,
    rowLabel: String,
    grid: GridLayout,
    scroll: GridScroll,
    appearance: GridRowAppearance,
    colors: DocumentViewerColors,
    modifier: Modifier = Modifier,
    highlights: RowHighlights = RowHighlights.None,
    onCellClick: ((Int) -> Unit)? = null,
) {
    Layout(
        content = {
            for (column in 0 until grid.columnCount) {
                val value = cells.getOrNull(column).orEmpty()
                // Empty cells have nothing more to show, so they are not clickable.
                val onClick = if (onCellClick != null && value.isNotEmpty()) ({ onCellClick(column) }) else null
                GridCell(
                    value = value,
                    isNumeric = grid.numeric[column],
                    appearance = appearance,
                    highlight = highlights.colorOf(column, colors),
                    onClick = onClick,
                )
            }
            if (grid.rowNumberWidth > 0f) RowNumber(rowLabel, appearance, colors)
        },
        modifier = modifier
            .fillMaxWidth()
            .background(appearance.background)
            .drawBehind { drawGridLines(grid, scroll.offset, colors.tableBorder) },
    ) { measurables, constraints ->
        measureGridRow(measurables, constraints, grid) { scroll.offset }
    }
}

@Composable
private fun GridCell(
    value: String,
    isNumeric: Boolean,
    appearance: GridRowAppearance,
    highlight: Color,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .background(highlight)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = GridMetrics.CellHorizontalPadding, vertical = GridMetrics.CellVerticalPadding),
    ) {
        val style = appearance.textStyle
        BasicText(
            text = value,
            modifier = Modifier.fillMaxWidth(),
            style = if (isNumeric) style.numeric() else style,
            overflow = TextOverflow.Ellipsis,
            maxLines = appearance.maxLines,
        )
    }
}

// Numbers line up by their last digit, like in spreadsheets.
private fun TextStyle.numeric(): TextStyle =
    copy(textAlign = TextAlign.End, fontFeatureSettings = GridMetrics.TABULAR_FIGURES)

@Composable
private fun RowNumber(
    label: String,
    appearance: GridRowAppearance,
    colors: DocumentViewerColors,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .background(appearance.background)
            .padding(horizontal = GridMetrics.RowNumberHorizontalPadding, vertical = GridMetrics.CellVerticalPadding),
    ) {
        BasicText(
            text = label,
            modifier = Modifier.fillMaxWidth(),
            style = appearance.textStyle.copy(
                color = colors.secondaryText,
                fontFamily = FontFamily.Monospace,
                fontSize = GridMetrics.RowNumberTextSize,
                textAlign = TextAlign.End,
            ),
            maxLines = 1,
        )
    }
}

private fun MeasureScope.measureGridRow(
    measurables: List<Measurable>,
    constraints: Constraints,
    grid: GridLayout,
    offset: () -> Float,
): MeasureResult {
    val cells = (0 until grid.columnCount).map { column ->
        measurables[column].measure(Constraints.fixedWidth(grid.widths[column].roundToInt()))
    }
    val rowNumber = measurables.getOrNull(grid.columnCount)
        ?.measure(Constraints.fixedWidth(grid.rowNumberWidth.roundToInt()))
    val height = cells.maxOfOrNull { it.height } ?: 0
    return layout(constraints.maxWidth, height) {
        val shift = offset().roundToInt()
        val start = grid.rowNumberWidth.roundToInt()
        cells.forEachIndexed { column, cell -> cell.place(start + grid.columnX[column].roundToInt() - shift, 0) }
        // Row numbers stay pinned and are placed last so they cover cells scrolled underneath.
        rowNumber?.place(0, 0, zIndex = 1f)
    }
}

private fun DrawScope.drawGridLines(grid: GridLayout, offset: Float, border: Color) {
    val stroke = GridMetrics.BorderWidth.toPx()
    val bottom = size.height - stroke / 2
    drawLine(border, Offset(0f, bottom), Offset(size.width, bottom), stroke)
    val columnBorder = border.copy(alpha = GridMetrics.COLUMN_BORDER_ALPHA)
    for (column in 1..grid.columnCount) {
        val x = grid.rowNumberWidth + grid.columnX[column] - offset
        if (x > grid.rowNumberWidth && x < size.width) {
            drawLine(columnBorder, Offset(x, 0f), Offset(x, size.height), stroke)
        }
    }
    if (grid.rowNumberWidth > 0f) {
        drawLine(border, Offset(grid.rowNumberWidth, 0f), Offset(grid.rowNumberWidth, size.height), stroke)
    }
}
