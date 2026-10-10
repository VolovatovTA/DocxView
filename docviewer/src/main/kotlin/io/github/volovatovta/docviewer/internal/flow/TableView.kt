package io.github.volovatovta.docviewer.internal.flow

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableState
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.volovatovta.docviewer.DocumentViewerColors
import io.github.volovatovta.docviewer.core.model.Table
import io.github.volovatovta.docviewer.core.model.TableCell
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

// Shared by every row of one table: column widths and the horizontal scroll position.
internal class TableRuntime(val columns: ColumnContent) {
    var offset by mutableFloatStateOf(0f)
    var contentWidth = 0
    var viewportWidth = 0
    val maxOffset: Int get() = (contentWidth - viewportWidth).coerceAtLeast(0)

    val scroll = ScrollableState { delta ->
        val previous = offset
        offset = (offset - delta).coerceIn(0f, maxOffset.toFloat())
        previous - offset
    }
}

// Tables nested inside cells are small enough to render whole.
@Composable
internal fun TableView(table: Table, context: FlowContext, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        table.rows.indices.forEach { row -> TableRowView(table, row, context) }
    }
}

// One row of a table, laid out on the table's shared column grid; top-level tables render one row per list item.
@Composable
internal fun TableRowView(table: Table, rowIndex: Int, context: FlowContext, modifier: Modifier = Modifier) {
    val runtime = rememberTableRuntime(table, context)
    val geometry = remember(table, rowIndex) { RowGeometry() }
    val row = table.rows[rowIndex]
    val isLast = rowIndex == table.rows.lastIndex
    val cellPadding = TableSpec.CellPadding * context.scale
    Layout(
        content = {
            CompositionLocalProvider(LocalInHeaderRow provides row.isHeader) {
                row.cells.forEach { cell -> TableCellContent(cell, context, cellPadding) }
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .padding(
                top = if (rowIndex == 0) TableSpec.VerticalMargin else 0.dp,
                bottom = if (isLast) TableSpec.VerticalMargin else 0.dp,
            )
            .clipToBounds()
            .scrollable(runtime.scroll, Orientation.Horizontal)
            .drawWithContent {
                drawContent()
                drawFadingEdges(runtime, context.colors.page)
            }
            .drawBehind { drawRowGrid(geometry, runtime.offset, row.isHeader, isLast, context.colors) },
    ) { measurables, constraints ->
        val minColumn = (TableSpec.MinColumnWidth.toPx() * context.scale).roundToInt().toFloat()
        val edges = columnEdges(columnWidths(table, runtime.columns, constraints.maxWidth.toFloat(), minColumn))
        runtime.contentWidth = edges.last()
        runtime.viewportWidth = constraints.maxWidth
        val slots = cellSlots(row.cells, edges.size - 1)
        val placeables = measurables.zip(slots) { measurable, slot ->
            measurable.measure(Constraints.fixedWidth(edges[slot.lastColumn] - edges[slot.firstColumn]))
        }
        val minHeight = (TableSpec.MinRowHeight.toPx() * context.scale).roundToInt()
        geometry.update(edges, slots, maxOf(minHeight, placeables.maxOfOrNull { it.height } ?: 0))
        layout(constraints.maxWidth, geometry.height) {
            val shift = runtime.offset.roundToInt()
            placeables.zip(slots) { placeable, slot -> placeable.placeWithLayer(edges[slot.firstColumn] - shift, 0) }
        }
    }
}

@Composable
private fun TableCellContent(cell: TableCell, context: FlowContext, cellPadding: Dp, modifier: Modifier = Modifier) {
    val horizontalPadding = cellPadding + TableSpec.CellExtraHorizontalPadding
    CompositionLocalProvider(LocalSurfaceColor provides cell.background?.let(::Color)) {
        Column(modifier.padding(horizontal = horizontalPadding, vertical = cellPadding)) {
            cell.blocks.forEach { BlockView(it, context, compact = true) }
        }
    }
}

@Composable
private fun rememberTableRuntime(table: Table, context: FlowContext): TableRuntime {
    val measurer = rememberTextMeasurer(cacheSize = 0)
    val density = LocalDensity.current
    return context.tables.getOrPut(table) {
        val padding = with(density) {
            (TableSpec.CellPadding * context.scale + TableSpec.CellExtraHorizontalPadding).toPx() * 2 +
                TableSpec.MeasureSafety.toPx()
        }
        val pointPx = with(density) { (1f * context.scale).dp.toPx() }
        TableRuntime(measureColumns(table, measurer, TextStyle(fontSize = context.textSize), padding, pointPx))
    }
}

// The columns one cell spans; rows with fewer cells than the grid get an empty slot for the rest.
private class CellSlot(val firstColumn: Int, val lastColumn: Int, val mergedWithAbove: Boolean, val background: Int?)

// Written by the measure pass, read when drawing the grid behind the cells.
private class RowGeometry {
    var edges = IntArray(0)
        private set
    var slots: List<CellSlot> = emptyList()
        private set
    var height = 0
        private set

    fun update(edges: IntArray, slots: List<CellSlot>, height: Int) {
        this.edges = edges
        this.slots = slots
        this.height = height
    }
}

private fun cellSlots(cells: List<TableCell>, columnCount: Int): List<CellSlot> {
    var column = 0
    val slots = cells.map { cell ->
        val first = column.coerceAtMost(columnCount - 1)
        val last = (column + cell.colSpan).coerceAtMost(columnCount).coerceAtLeast(first + 1)
        column = last
        CellSlot(first, last, cell.mergedWithAbove, cell.background)
    }
    if (column >= columnCount) return slots
    return slots + CellSlot(column, columnCount, mergedWithAbove = false, background = null)
}

private fun columnEdges(widths: FloatArray): IntArray {
    val edges = IntArray(widths.size + 1)
    widths.forEachIndexed { index, width -> edges[index + 1] = edges[index] + width.roundToInt() }
    return edges
}

private fun DrawScope.drawRowGrid(
    geometry: RowGeometry,
    offset: Float,
    isHeader: Boolean,
    isLast: Boolean,
    colors: DocumentViewerColors,
) {
    val stroke = TableSpec.BorderWidth.toPx()
    val bottom = size.height - stroke / 2
    geometry.slots.forEach { slot ->
        val left = geometry.edges[slot.firstColumn] - offset
        val right = geometry.edges[slot.lastColumn] - offset
        val fill = slot.background?.let(::Color) ?: colors.tableHeader.takeIf { isHeader }
        fill?.let { drawRect(it, Offset(left, 0f), Size(right - left, size.height)) }
        if (!slot.mergedWithAbove) drawLine(colors.tableBorder, Offset(left, 0f), Offset(right, 0f), stroke)
        drawLine(colors.tableBorder, Offset(left, 0f), Offset(left, size.height), stroke)
        drawLine(colors.tableBorder, Offset(right, 0f), Offset(right, size.height), stroke)
        if (isLast) drawLine(colors.tableBorder, Offset(left, bottom), Offset(right, bottom), stroke)
    }
}

// Fading edges hint that the table continues off-screen.
private fun DrawScope.drawFadingEdges(runtime: TableRuntime, page: Color) {
    val maxOffset = runtime.maxOffset
    if (maxOffset <= 0) return
    val fade = TableSpec.FadeWidth.toPx()
    if (runtime.offset < maxOffset - 1) {
        val start = size.width - fade
        drawRect(
            brush = Brush.horizontalGradient(listOf(Color.Transparent, page), startX = start, endX = size.width),
            topLeft = Offset(start, 0f),
            size = Size(fade, size.height),
        )
    }
    if (runtime.offset > 1) {
        drawRect(
            brush = Brush.horizontalGradient(listOf(page, Color.Transparent), startX = 0f, endX = fade),
            topLeft = Offset.Zero,
            size = Size(fade, size.height),
        )
    }
}
