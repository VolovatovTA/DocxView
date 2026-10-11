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
import androidx.compose.runtime.DisposableEffect
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
import io.github.volovatovta.docviewer.core.model.TableSource
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
    var viewportWidth by mutableFloatStateOf(0f)
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

// One sheet: a pinned header, rows that scroll both ways, and the search hits that fall on this sheet.
@Composable
internal fun SheetGrid(
    document: TableSource,
    sheet: Int,
    controller: WorkbookController,
    state: DocumentViewerState,
    config: DocumentViewerConfig,
    colors: DocumentViewerColors,
    modifier: Modifier = Modifier,
) {
    val cellStyle = TextStyle(color = colors.text, fontSize = GridMetrics.CellTextSize)
    val theme = GridTheme(colors, cellStyle, cellStyle.copy(fontWeight = FontWeight.SemiBold))
    val grid = rememberGridLayout(document, theme.cell, theme.header, config.showCsvRowNumbers)
    val initialRow = remember { state.consumeInitialPage() }.coerceIn(0, (document.rowCount - 1).coerceAtLeast(0))
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialRow)
    val scroll = remember(document) { GridScroll() }
    val allHits = remember(state.searchHits) { state.searchHits.filterIsInstance<GridHit>() }
    val hits = remember(allHits, sheet) { allHits.filter { it.sheet == sheet } }
    val current = allHits.getOrNull(state.currentSearchResult)?.takeIf { it.sheet == sheet }
    val search = remember(hits, current) { GridSearch(hits, current) }
    var openedCell by remember { mutableStateOf<GridHit?>(null) }

    BoxWithConstraints(modifier.fillMaxSize().background(colors.page)) {
        val viewportWidth = constraints.maxWidth.toFloat()
        val viewportHeight = constraints.maxHeight.toFloat()
        scroll.max = (grid.contentWidth - viewportWidth).coerceAtLeast(0f)
        scroll.viewportWidth = viewportWidth
        val navigator = remember(
            listState,
            grid
        ) { SheetNavigator(listState, scroll, grid, viewportWidth, viewportHeight) }
        navigator.viewportWidth = viewportWidth
        navigator.viewportHeight = viewportHeight
        DisposableEffect(navigator) {
            controller.attach(sheet, navigator)
            onDispose { controller.detach(navigator) }
        }
        TrackCurrentItem(listState, state)

        GridTable(
            document = document,
            listState = listState,
            grid = grid,
            scroll = scroll,
            theme = theme,
            search = search,
            onCellClick = { row, column -> openedCell = GridHit(sheet, row, column) },
        )
        ScrollIndicator(
            text = "${state.currentPage + 1} / ${document.rowCount}",
            visibleKey = state.currentPage,
            isScrolling = listState.isScrollInProgress,
            colors = colors,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = GridViewerSpec.IndicatorBottomPadding),
        )
        if (config.showScrollbar) {
            val lastRow = (document.rowCount - 1)
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
    document: TableSource,
    listState: LazyListState,
    grid: GridLayout,
    scroll: GridScroll,
    theme: GridTheme,
    search: GridSearch,
    onCellClick: (row: Int, column: Int) -> Unit,
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
        if (document is TableDocument && document.truncated) {
            val shownRows = GridViewerSpec.ROW_COUNT_FORMAT.format(document.rowCount)
            Banner(stringResource(R.string.dv_csv_truncated, shownRows), theme.colors)
        }
    }
}

@Composable
private fun GridRows(
    document: TableSource,
    listState: LazyListState,
    grid: GridLayout,
    scroll: GridScroll,
    theme: GridTheme,
    search: GridSearch,
    onCellClick: (row: Int, column: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(state = listState, modifier = modifier) {
        items(document.rowCount, contentType = { 0 }) { index ->
            GridRow(
                cells = document.row(index),
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
                onCellClick = { column -> onCellClick(index, column) },
            )
        }
    }
}

@Composable
private fun OpenedCellDialog(
    document: TableSource,
    cell: GridHit,
    colors: DocumentViewerColors,
    onDismiss: () -> Unit,
) {
    val columnName = document.header?.getOrNull(cell.column)?.takeIf { it.isNotBlank() }
    val title = columnName ?: "${cell.row + 1}:${cell.column + 1}"
    val value = document.row(cell.row).getOrNull(cell.column).orEmpty()
    CellDialog(title, value, colors, onDismiss)
}
