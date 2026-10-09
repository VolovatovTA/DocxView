package io.github.volovatovta.docviewer.internal.grid

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import io.github.volovatovta.docviewer.DocumentViewerColors
import io.github.volovatovta.docviewer.DocumentViewerConfig
import io.github.volovatovta.docviewer.DocumentViewerState
import io.github.volovatovta.docviewer.R
import io.github.volovatovta.docviewer.core.CsvParser
import io.github.volovatovta.docviewer.core.model.TableDocument
import io.github.volovatovta.docviewer.internal.ViewerController
import io.github.volovatovta.docviewer.internal.flow.scrollFraction
import io.github.volovatovta.docviewer.internal.ui.Banner
import io.github.volovatovta.docviewer.internal.ui.FastScrollbar
import io.github.volovatovta.docviewer.internal.ui.PillButton
import io.github.volovatovta.docviewer.internal.ui.ScrollIndicator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlin.math.abs
import kotlin.math.roundToInt

internal class GridHit(val row: Int, val column: Int)

@Stable
private class GridScroll {
    var offset by mutableFloatStateOf(0f)
    var max = 0f
}

@Composable
internal fun GridViewer(document: TableDocument, state: DocumentViewerState, config: DocumentViewerConfig, colors: DocumentViewerColors) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer(cacheSize = 0)
    val cellStyle = TextStyle(color = colors.text, fontSize = 14.sp)
    val headerStyle = cellStyle.copy(fontWeight = FontWeight.SemiBold)
    val padH = with(density) { 12.dp.toPx() }
    val columns = document.columnCount.coerceAtLeast(1)

    // Widths from the header and a sample of rows; numeric columns are right-aligned like in spreadsheets.
    val layout = remember(document, colors, config.showCsvRowNumbers) {
        val sample = document.rows.take(200)
        val minW = with(density) { 56.dp.toPx() }
        val maxW = with(density) { 300.dp.toPx() }
        val safety = with(density) { 2.dp.toPx() }
        val widths = FloatArray(columns) { col ->
            val texts = buildList {
                document.header?.getOrNull(col)?.let { add(it to true) }
                sample.forEach { row -> row.getOrNull(col)?.takeIf { it.isNotEmpty() }?.let { add(it.lineSequence().first().take(80) to false) } }
            }
            val widest = texts.maxOfOrNull { (text, header) -> measurer.measure(text, if (header) headerStyle else cellStyle, maxLines = 1).size.width.toFloat() } ?: 0f
            // A text laid out at exactly its measured width can still wrap, hence the safety margin.
            (widest + padH * 2 + safety).coerceIn(minW, maxW)
        }
        val numeric = BooleanArray(columns) { col ->
            val values = sample.mapNotNull { it.getOrNull(col)?.takeIf(String::isNotBlank) }
            values.isNotEmpty() && values.count(CsvParser::isNumeric) >= values.size * 0.8
        }
        val x = FloatArray(columns + 1).also { for (i in 0 until columns) it[i + 1] = it[i] + widths[i] }
        val numberWidth = if (config.showCsvRowNumbers) {
            measurer.measure(document.rows.size.toString(), cellStyle.copy(fontFamily = FontFamily.Monospace)).size.width + padH * 1.5f
        } else {
            0f
        }
        GridLayout(widths, x, numeric, numberWidth)
    }

    val listState = rememberLazyListState(initialFirstVisibleItemIndex = remember { state.consumeInitialPage() }.coerceIn(0, (document.rows.size - 1).coerceAtLeast(0)))
    val scroll = remember(document) { GridScroll() }
    val scrollable = rememberScrollableState { delta ->
        val old = scroll.offset
        scroll.offset = (old - delta).coerceIn(0f, scroll.max)
        old - scroll.offset
    }
    val hits = remember(state.searchHits) { state.searchHits.filterIsInstance<GridHit>() }
    val hitCells = remember(hits) { hits.groupBy({ it.row }, { it.column }) }
    val current = hits.getOrNull(state.currentSearchResult)
    var opened by remember { mutableStateOf<GridHit?>(null) }

    BoxWithConstraints(Modifier.fillMaxSize().background(colors.page)) {
        val viewportWidth = constraints.maxWidth.toFloat()
        scroll.max = (layout.numberWidth + layout.x.last() - viewportWidth).coerceAtLeast(0f)
        val viewportHeight = constraints.maxHeight.toFloat()
        val controller = remember(document, listState, layout) { GridController(document, listState, scroll, layout, viewportWidth, viewportHeight) }
        controller.viewportWidth = viewportWidth
        controller.viewportHeight = viewportHeight
        DisposableEffect(controller) {
            state.controller = controller
            onDispose { if (state.controller === controller) state.controller = null }
        }
        LaunchedEffect(listState) {
            snapshotFlow { listState.firstVisibleItemIndex }.distinctUntilChanged().collect { state.currentPage = it }
        }
        Column(Modifier.fillMaxSize().clipToBounds().scrollable(scrollable, Orientation.Horizontal)) {
            document.header?.let { header ->
                GridRow(
                    cells = header,
                    rowLabel = "#",
                    layout = layout,
                    scroll = scroll,
                    textStyle = headerStyle,
                    colors = colors,
                    background = colors.tableHeader,
                    highlighted = null,
                    current = null,
                    maxLines = 2,
                    onCellClick = null,
                )
            }
            LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
                items(document.rows.size, contentType = { 0 }) { index ->
                    val row = document.rows[index]
                    GridRow(
                        cells = row,
                        rowLabel = (index + 1).toString(),
                        layout = layout,
                        scroll = scroll,
                        textStyle = cellStyle,
                        colors = colors,
                        background = if (index % 2 == 1) colors.stripe else colors.page,
                        highlighted = hitCells[index],
                        current = current?.takeIf { it.row == index }?.column,
                        maxLines = 3,
                        onCellClick = { col -> opened = GridHit(index, col) },
                    )
                }
            }
            if (document.truncated) Banner(stringResource(R.string.dv_csv_truncated, "%,d".format(document.rows.size)), colors)
        }
        val scrolling = listState.isScrollInProgress
        ScrollIndicator(
            text = "${state.currentPage + 1} / ${document.rows.size}",
            visibleKey = state.currentPage,
            isScrolling = scrolling,
            colors = colors,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp),
        )
        if (config.showScrollbar) {
            val total = document.rows.size
            FastScrollbar(
                isScrolling = scrolling,
                fraction = { listState.scrollFraction() },
                visibleFraction = { if (total == 0) 1f else listState.layoutInfo.visibleItemsInfo.size.toFloat() / total },
                onScrollTo = { f -> listState.requestScrollToItem((f * (total - 1)).roundToInt().coerceAtLeast(0)) },
                colors = colors,
                modifier = Modifier.align(Alignment.CenterEnd),
                label = { f -> "${(f * (total - 1)).roundToInt() + 1}" },
            )
        }
    }

    opened?.let { cell ->
        val title = document.header?.getOrNull(cell.column)?.takeIf { it.isNotBlank() } ?: "${cell.row + 1}:${cell.column + 1}"
        CellDialog(title, document.rows[cell.row].getOrNull(cell.column).orEmpty(), colors) { opened = null }
    }
}

private class GridLayout(val widths: FloatArray, val x: FloatArray, val numeric: BooleanArray, val numberWidth: Float)

@Composable
private fun GridRow(
    cells: List<String>,
    rowLabel: String,
    layout: GridLayout,
    scroll: GridScroll,
    textStyle: TextStyle,
    colors: DocumentViewerColors,
    background: androidx.compose.ui.graphics.Color,
    highlighted: List<Int>?,
    current: Int?,
    maxLines: Int,
    onCellClick: ((Int) -> Unit)?,
) {
    val columns = layout.widths.size
    val numberStyle = textStyle.copy(color = colors.secondaryText, fontFamily = FontFamily.Monospace, fontSize = 12.sp, textAlign = TextAlign.End)
    Layout(
        content = {
            for (col in 0 until columns) {
                val value = cells.getOrNull(col).orEmpty()
                val bg = when {
                    current == col -> colors.currentSearchMatch
                    highlighted?.contains(col) == true -> colors.searchMatch
                    else -> androidx.compose.ui.graphics.Color.Transparent
                }
                Box(
                    Modifier.background(bg)
                        .then(if (onCellClick != null && value.isNotEmpty()) Modifier.clickable { onCellClick(col) } else Modifier)
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                ) {
                    BasicText(
                        value,
                        style = if (layout.numeric[col]) textStyle.copy(textAlign = TextAlign.End, fontFeatureSettings = "tnum") else textStyle,
                        maxLines = maxLines,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            if (layout.numberWidth > 0f) {
                Box(Modifier.background(background).padding(horizontal = 6.dp, vertical = 9.dp)) {
                    BasicText(rowLabel, style = numberStyle, maxLines = 1, modifier = Modifier.fillMaxWidth())
                }
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .drawBehind {
                val stroke = 1.dp.toPx()
                val shift = scroll.offset
                drawLine(colors.tableBorder, Offset(0f, size.height - stroke / 2), Offset(size.width, size.height - stroke / 2), stroke)
                for (i in 1..columns) {
                    val x = layout.numberWidth + layout.x[i] - shift
                    if (x > layout.numberWidth && x < size.width) drawLine(colors.tableBorder.copy(alpha = 0.6f), Offset(x, 0f), Offset(x, size.height), stroke)
                }
                if (layout.numberWidth > 0f) drawLine(colors.tableBorder, Offset(layout.numberWidth, 0f), Offset(layout.numberWidth, size.height), stroke)
            },
    ) { measurables, constraints ->
        var height = 0
        val placeables = (0 until columns).map { col ->
            measurables[col].measure(Constraints.fixedWidth(layout.widths[col].roundToInt())).also { height = maxOf(height, it.height) }
        }
        val number = if (layout.numberWidth > 0f) measurables[columns].measure(Constraints.fixedWidth(layout.numberWidth.roundToInt())) else null
        layout(constraints.maxWidth, height) {
            val shift = scroll.offset.roundToInt()
            placeables.forEachIndexed { col, p -> p.place(layout.numberWidth.roundToInt() + layout.x[col].roundToInt() - shift, 0) }
            // Row numbers stay pinned and are placed last so they cover cells scrolled underneath.
            number?.place(0, 0, zIndex = 1f)
        }
    }
}

@Composable
private fun CellDialog(title: String, value: String, colors: DocumentViewerColors, onDismiss: () -> Unit) {
    Dialog(onDismiss) {
        Column(
            Modifier.widthIn(max = 520.dp).fillMaxWidth()
                .background(colors.page, RoundedCornerShape(20.dp))
                .padding(24.dp),
        ) {
            BasicText(title, style = TextStyle(color = colors.secondaryText, fontSize = 13.sp, fontWeight = FontWeight.Medium))
            Spacer(Modifier.height(10.dp))
            SelectionContainer(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                BasicText(value, style = TextStyle(color = colors.text, fontSize = 16.sp))
            }
            Spacer(Modifier.height(20.dp))
            Box(Modifier.align(Alignment.End)) { PillButton(stringResource(R.string.dv_close), colors, onClick = onDismiss) }
        }
    }
}

private class GridController(
    private val document: TableDocument,
    private val listState: LazyListState,
    private val scroll: GridScroll,
    private val layout: GridLayout,
    var viewportWidth: Float,
    var viewportHeight: Float,
) : ViewerController {

    override fun search(query: String): Flow<List<Any>> = flow {
        val hits = ArrayList<GridHit>()
        document.rows.forEachIndexed { r, row ->
            row.forEachIndexed { c, value -> if (value.contains(query, ignoreCase = true)) hits += GridHit(r, c) }
        }
        emit(hits)
    }.flowOn(Dispatchers.Default)

    override suspend fun reveal(hit: Any) {
        hit as GridHit
        val visible = listState.layoutInfo.visibleItemsInfo
        val onScreen = visible.any { it.index == hit.row && it.offset >= 0 && it.offset + it.size <= viewportHeight * 0.9f }
        if (!onScreen) {
            if (abs(hit.row - listState.firstVisibleItemIndex) > 100) listState.scrollToItem(hit.row) else listState.animateScrollToItem(hit.row)
            if (hit.row > 0 && listState.canScrollBackward) listState.animateScrollBy(-viewportHeight / 3)
        }
        val left = layout.x[hit.column]
        val right = layout.x[hit.column + 1]
        val visibleWidth = viewportWidth - layout.numberWidth
        val target = when {
            left < scroll.offset -> left
            right > scroll.offset + visibleWidth -> right - visibleWidth
            else -> return
        }.coerceIn(0f, scroll.max)
        animate(scroll.offset, target, animationSpec = tween(250)) { value, _ -> scroll.offset = value }
    }

    override suspend fun scrollToItem(index: Int, animate: Boolean) {
        if (animate && abs(index - listState.firstVisibleItemIndex) < 100) listState.animateScrollToItem(index) else listState.scrollToItem(index)
    }

    override suspend fun zoomBy(factor: Float) = Unit

    override suspend fun zoomTo(zoom: Float) = Unit
}
