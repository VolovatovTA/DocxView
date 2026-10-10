package io.github.volovatovta.docviewer.internal.grid

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.volovatovta.docviewer.DocumentViewerColors
import io.github.volovatovta.docviewer.DocumentViewerConfig
import io.github.volovatovta.docviewer.DocumentViewerState
import io.github.volovatovta.docviewer.R
import io.github.volovatovta.docviewer.core.model.TableDocument
import io.github.volovatovta.docviewer.internal.flow.BindController
import io.github.volovatovta.docviewer.internal.flow.ListFastScrollbar
import io.github.volovatovta.docviewer.internal.flow.TrackCurrentItem
import io.github.volovatovta.docviewer.internal.ui.Banner
import io.github.volovatovta.docviewer.internal.ui.ScrollIndicator
import kotlin.math.roundToInt

private object GridViewerSpec {
    val IndicatorBottomPadding = 24.dp
    const val HEADER_LABEL = "#"
    const val ROW_COUNT_FORMAT = "%,d"
}

// Horizontal scroll shared by the header and every row, applied at placement so rows never re-measure.
@Stable
internal class GridScroll {
    var offset by mutableFloatStateOf(0f)
    var max = 0f

    fun consume(delta: Float): Float {
        val previous = offset
        offset = (previous - delta).coerceIn(0f, max)
        return previous - offset
    }
}

// Search hits grouped by row, plus the hit the user is looking at.
@Immutable
private class GridSearch(hits: List<GridHit>, val current: GridHit?) {
    private val columnsByRow = hits.groupBy({ it.row }, { it.column })

    fun highlightsOf(row: Int) = RowHighlights(columnsByRow[row], current?.takeIf { it.row == row }?.column)
}

@Immutable
private class GridTheme(val colors: DocumentViewerColors, val cell: TextStyle, val header: TextStyle)

@Composable
internal fun GridViewer(
    document: TableDocument,
    state: DocumentViewerState,
    config: DocumentViewerConfig,
    colors: DocumentViewerColors,
    modifier: Modifier = Modifier,
) {
    val cellStyle = TextStyle(color = colors.text, fontSize = GridMetrics.CellTextSize)
    val theme = GridTheme(colors, cellStyle, cellStyle.copy(fontWeight = FontWeight.SemiBold))
    val grid = rememberGridLayout(document, theme.cell, theme.header, config.showCsvRowNumbers)
    val initialRow = remember { state.consumeInitialPage() }.coerceIn(0, document.rows.lastIndex.coerceAtLeast(0))
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialRow)
    val scroll = remember(document) { GridScroll() }
    val hits = remember(state.searchHits) { state.searchHits.filterIsInstance<GridHit>() }
    val current = hits.getOrNull(state.currentSearchResult)
    val search = remember(hits, current) { GridSearch(hits, current) }
    var openedCell by remember { mutableStateOf<GridHit?>(null) }

    BoxWithConstraints(modifier.fillMaxSize().background(colors.page)) {
        val viewportWidth = constraints.maxWidth.toFloat()
        val viewportHeight = constraints.maxHeight.toFloat()
        scroll.max = (grid.contentWidth - viewportWidth).coerceAtLeast(0f)
        val controller = remember(document, listState, grid) {
            GridController(document, listState, scroll, grid, viewportWidth, viewportHeight)
        }
        controller.viewportWidth = viewportWidth
        controller.viewportHeight = viewportHeight
        BindController(state, controller)
        TrackCurrentItem(listState, state)

        GridTable(document, listState, grid, scroll, theme, search, onCellClick = { openedCell = it })
        ScrollIndicator(
            text = "${state.currentPage + 1} / ${document.rows.size}",
            visibleKey = state.currentPage,
            isScrolling = listState.isScrollInProgress,
            colors = colors,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = GridViewerSpec.IndicatorBottomPadding),
        )
        if (config.showScrollbar) {
            val lastRow = document.rows.lastIndex
            ListFastScrollbar(
                listState = listState,
                colors = colors,
                label = { fraction -> "${(fraction * lastRow).roundToInt() + 1}" },
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
        openedCell?.let { cell -> OpenedCellDialog(document, cell, colors, onDismiss = { openedCell = null }) }
    }
}

@Composable
private fun GridTable(
    document: TableDocument,
    listState: LazyListState,
    grid: GridLayout,
    scroll: GridScroll,
    theme: GridTheme,
    search: GridSearch,
    onCellClick: (GridHit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollable = rememberScrollableState { delta -> scroll.consume(delta) }
    Column(modifier.fillMaxSize().clipToBounds().scrollable(scrollable, Orientation.Horizontal)) {
        document.header?.let { header ->
            GridRow(
                cells = header,
                rowLabel = GridViewerSpec.HEADER_LABEL,
                grid = grid,
                scroll = scroll,
                appearance = GridRowAppearance(
                    textStyle = theme.header,
                    background = theme.colors.tableHeader,
                    maxLines = GridMetrics.HEADER_MAX_LINES,
                ),
                colors = theme.colors,
            )
        }
        GridRows(document, listState, grid, scroll, theme, search, onCellClick, Modifier.weight(1f))
        if (document.truncated) {
            val shownRows = GridViewerSpec.ROW_COUNT_FORMAT.format(document.rows.size)
            Banner(stringResource(R.string.dv_csv_truncated, shownRows), theme.colors)
        }
    }
}

@Composable
private fun GridRows(
    document: TableDocument,
    listState: LazyListState,
    grid: GridLayout,
    scroll: GridScroll,
    theme: GridTheme,
    search: GridSearch,
    onCellClick: (GridHit) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(state = listState, modifier = modifier) {
        items(document.rows.size, contentType = { 0 }) { index ->
            GridRow(
                cells = document.rows[index],
                rowLabel = (index + 1).toString(),
                grid = grid,
                scroll = scroll,
                appearance = GridRowAppearance(
                    textStyle = theme.cell,
                    background = if (index % 2 == 1) theme.colors.stripe else theme.colors.page,
                    maxLines = GridMetrics.CELL_MAX_LINES,
                ),
                colors = theme.colors,
                highlights = search.highlightsOf(index),
                onCellClick = { column -> onCellClick(GridHit(index, column)) },
            )
        }
    }
}

@Composable
private fun OpenedCellDialog(
    document: TableDocument,
    cell: GridHit,
    colors: DocumentViewerColors,
    onDismiss: () -> Unit,
) {
    val columnName = document.header?.getOrNull(cell.column)?.takeIf { it.isNotBlank() }
    val title = columnName ?: "${cell.row + 1}:${cell.column + 1}"
    val value = document.rows[cell.row].getOrNull(cell.column).orEmpty()
    CellDialog(title, value, colors, onDismiss)
}
