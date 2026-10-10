package io.github.volovatovta.docviewer.internal.flow

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.volovatovta.docviewer.DocumentViewerColors
import io.github.volovatovta.docviewer.core.model.Table
import io.github.volovatovta.docviewer.core.model.TableCell
import io.github.volovatovta.docviewer.core.model.TableRow
import kotlin.math.roundToInt

private object TableSpec {
    val CellPadding = 6.dp
    val CellExtraHorizontalPadding = 2.dp

    // A text laid out at exactly its measured width can still wrap, hence the safety margin.
    val MeasureSafety = 2.dp
    val VerticalMargin = 8.dp
    val MinColumnWidth = 48.dp
    val MinRowHeight = 24.dp
    val FadeWidth = 28.dp
    val BorderWidth = 1.dp
}

// One rectangle of the grid as drawn: merged continuation cells have no top border.
private class CellBox(
    val row: Int,
    val firstColumn: Int,
    val lastColumn: Int,
    val mergedWithAbove: Boolean,
    val background: Int?,
)

// Written during measure, read while drawing the grid and clamping the scroll.
private class TableGeometry {
    var columnX = IntArray(0)
    var rowY = IntArray(0)
    var cells: List<CellBox> = emptyList()
    var contentWidth = 0
    var viewportWidth = 0
    val maxOffset: Int get() = (contentWidth - viewportWidth).coerceAtLeast(0)
}

private class MeasuredRow(val height: Int, val cells: List<Pair<Placeable, Int>>, val boxes: List<CellBox>)

@Composable
internal fun TableView(table: Table, context: FlowContext, modifier: Modifier = Modifier) {
    if (table.rows.isEmpty()) return
    var offset by remember(table) { mutableFloatStateOf(0f) }
    val geometry = remember(table) { TableGeometry() }
    val scrollState = rememberScrollableState { delta ->
        val previous = offset
        offset = (offset - delta).coerceIn(0f, geometry.maxOffset.toFloat())
        previous - offset
    }
    val cellPadding = TableSpec.CellPadding * context.scale
    val columns = rememberColumnContent(table, context, cellPadding)
    Layout(
        content = {
            // Every cell is a direct child of the layout; rows only exist in the measure policy.
            table.rows.forEach { row ->
                CompositionLocalProvider(LocalInHeaderRow provides row.isHeader) {
                    row.cells.forEach { cell -> TableCellContent(cell, context, cellPadding) }
                }
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = TableSpec.VerticalMargin)
            .clipToBounds()
            .scrollable(scrollState, Orientation.Horizontal)
            .drawWithContent {
                drawContent()
                drawFadingEdges(geometry, offset, context.colors.page)
            }
            .drawBehind { drawGrid(table, geometry, offset, context.colors) },
    ) { measurables, constraints ->
        val minColumn = (TableSpec.MinColumnWidth.toPx() * context.scale).roundToInt().toFloat()
        val widths = columnWidths(table, columns, constraints.maxWidth.toFloat(), minColumn)
        val minRowHeight = (TableSpec.MinRowHeight.toPx() * context.scale).roundToInt()
        measureTable(table, measurables, constraints, widths, geometry, minRowHeight) { offset }
    }
}

@Composable
private fun TableCellContent(cell: TableCell, context: FlowContext, cellPadding: Dp, modifier: Modifier = Modifier) {
    val horizontalPadding = cellPadding + TableSpec.CellExtraHorizontalPadding
    Column(modifier.padding(horizontal = horizontalPadding, vertical = cellPadding)) {
        cell.blocks.forEach { BlockView(it, context, compact = true) }
    }
}

@Composable
private fun rememberColumnContent(table: Table, context: FlowContext, cellPadding: Dp): ColumnContent {
    val measurer = rememberTextMeasurer(cacheSize = 0)
    val density = LocalDensity.current
    return context.tableColumns.getOrPut(table) {
        val padding = with(density) {
            (cellPadding + TableSpec.CellExtraHorizontalPadding).toPx() * 2 + TableSpec.MeasureSafety.toPx()
        }
        measureColumns(table, measurer, TextStyle(fontSize = context.textSize), padding)
    }
}

private fun MeasureScope.measureTable(
    table: Table,
    measurables: List<Measurable>,
    constraints: Constraints,
    widths: FloatArray,
    geometry: TableGeometry,
    minRowHeight: Int,
    offset: () -> Float,
): MeasureResult {
    val columnX = columnEdges(widths)
    val remaining = measurables.iterator()
    val rows = table.rows.mapIndexed { index, row -> measureRow(row, index, remaining, columnX, minRowHeight) }
    val rowY = IntArray(rows.size + 1)
    rows.forEachIndexed { index, row -> rowY[index + 1] = rowY[index] + row.height }
    geometry.columnX = columnX
    geometry.rowY = rowY
    geometry.cells = rows.flatMap { it.boxes }
    geometry.contentWidth = columnX.last()
    geometry.viewportWidth = constraints.maxWidth
    val width = if (constraints.hasBoundedWidth) constraints.maxWidth else columnX.last()
    return layout(width, rowY.last()) {
        val shift = offset().roundToInt()
        rows.forEachIndexed { index, row ->
            row.cells.forEach { (placeable, x) -> placeable.placeWithLayer(x - shift, rowY[index]) }
        }
    }
}

private fun columnEdges(widths: FloatArray): IntArray {
    val edges = IntArray(widths.size + 1)
    widths.forEachIndexed { index, width -> edges[index + 1] = edges[index] + width.roundToInt() }
    return edges
}

private fun measureRow(
    row: TableRow,
    rowIndex: Int,
    measurables: Iterator<Measurable>,
    columnX: IntArray,
    minHeight: Int,
): MeasuredRow {
    val count = columnX.size - 1
    val placed = ArrayList<Pair<Placeable, Int>>()
    val boxes = ArrayList<CellBox>()
    var column = 0
    for (cell in row.cells) {
        val first = column.coerceAtMost(count - 1)
        val last = (column + cell.colSpan).coerceAtMost(count).coerceAtLeast(first + 1)
        column = last
        val width = columnX[last] - columnX[first]
        placed += measurables.next().measure(Constraints.fixedWidth(width)) to columnX[first]
        boxes += CellBox(rowIndex, first, last, cell.mergedWithAbove, cell.background)
    }
    // Rows with fewer cells than the grid still get borders for the missing part.
    if (column < count) boxes += CellBox(rowIndex, column, count, mergedWithAbove = false, background = null)
    val height = maxOf(minHeight, placed.maxOfOrNull { it.first.height } ?: 0)
    return MeasuredRow(height, placed, boxes)
}

// Fading edges hint that the table continues off-screen.
private fun DrawScope.drawFadingEdges(geometry: TableGeometry, offset: Float, page: Color) {
    val maxOffset = geometry.maxOffset
    if (maxOffset <= 0) return
    val fade = TableSpec.FadeWidth.toPx()
    if (offset < maxOffset - 1) {
        val start = size.width - fade
        drawRect(
            brush = Brush.horizontalGradient(listOf(Color.Transparent, page), startX = start, endX = size.width),
            topLeft = Offset(start, 0f),
            size = Size(fade, size.height),
        )
    }
    if (offset > 1) {
        drawRect(
            brush = Brush.horizontalGradient(listOf(page, Color.Transparent), startX = 0f, endX = fade),
            topLeft = Offset.Zero,
            size = Size(fade, size.height),
        )
    }
}

private fun DrawScope.drawGrid(table: Table, geometry: TableGeometry, offset: Float, colors: DocumentViewerColors) {
    val stroke = TableSpec.BorderWidth.toPx()
    geometry.cells.forEach { cell ->
        drawCell(cell, table.rows[cell.row].isHeader, geometry, offset, colors, stroke)
    }
    val bottom = geometry.rowY.last() - stroke / 2
    drawLine(
        color = colors.tableBorder,
        start = Offset(geometry.columnX.first() - offset, bottom),
        end = Offset(geometry.columnX.last() - offset, bottom),
        strokeWidth = stroke,
    )
}

private fun DrawScope.drawCell(
    cell: CellBox,
    isHeader: Boolean,
    geometry: TableGeometry,
    offset: Float,
    colors: DocumentViewerColors,
    stroke: Float,
) {
    val left = geometry.columnX[cell.firstColumn] - offset
    val right = geometry.columnX[cell.lastColumn] - offset
    val top = geometry.rowY[cell.row].toFloat()
    val bottom = geometry.rowY[cell.row + 1].toFloat()
    val fill = cell.background?.let(::Color) ?: colors.tableHeader.takeIf { isHeader }
    fill?.let { drawRect(it, Offset(left, top), Size(right - left, bottom - top)) }
    if (!cell.mergedWithAbove) drawLine(colors.tableBorder, Offset(left, top), Offset(right, top), stroke)
    drawLine(colors.tableBorder, Offset(left, top), Offset(left, bottom), stroke)
    drawLine(colors.tableBorder, Offset(right, top), Offset(right, bottom), stroke)
}
