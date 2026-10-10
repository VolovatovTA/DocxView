package io.github.volovatovta.docviewer.internal.grid

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyListState
import io.github.volovatovta.docviewer.core.model.TableSource
import io.github.volovatovta.docviewer.internal.ViewerController
import io.github.volovatovta.docviewer.internal.flow.goToItem
import io.github.volovatovta.docviewer.internal.flow.showContextAbove
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

internal class GridHit(val row: Int, val column: Int)

private object GridNavigation {
    const val ANIMATED_DISTANCE = 100

    // A row counts as visible when it ends above this share of the viewport.
    const val VISIBLE_BOTTOM = 0.9f
    const val CONTEXT_ABOVE_MATCH = 1f / 3
    const val HORIZONTAL_SCROLL_MILLIS = 250
}

internal class GridController(
    private val document: TableSource,
    private val listState: LazyListState,
    private val scroll: GridScroll,
    private val grid: GridLayout,
    var viewportWidth: Float,
    var viewportHeight: Float,
) : ViewerController {

    override fun search(query: String): Flow<List<Any>> = flow {
        val hits = ArrayList<GridHit>()
        document.forEachRow { row, cells ->
            cells.forEachIndexed { column, cell ->
                if (cell.contains(query, ignoreCase = true)) hits += GridHit(row, column)
            }
        }
        emit(hits)
    }.flowOn(Dispatchers.Default)

    override suspend fun reveal(hit: Any) {
        val match = hit as GridHit
        if (!isRowVisible(match.row)) {
            scrollToItem(match.row, animate = true)
            listState.showContextAbove(match.row, viewportHeight * GridNavigation.CONTEXT_ABOVE_MATCH)
        }
        val target = horizontalTarget(match.column) ?: return
        animate(scroll.offset, target, animationSpec = tween(GridNavigation.HORIZONTAL_SCROLL_MILLIS)) { value, _ ->
            scroll.offset = value
        }
    }

    override suspend fun scrollToItem(index: Int, animate: Boolean) =
        listState.goToItem(index, animate, GridNavigation.ANIMATED_DISTANCE)

    override suspend fun zoomBy(factor: Float) = Unit

    override suspend fun zoomTo(zoom: Float) = Unit

    private fun isRowVisible(row: Int): Boolean = listState.layoutInfo.visibleItemsInfo.any {
        it.index == row && it.offset >= 0 && it.offset + it.size <= viewportHeight * GridNavigation.VISIBLE_BOTTOM
    }

    // Scrolls just enough to bring the column into view, or not at all.
    private fun horizontalTarget(column: Int): Float? {
        val left = grid.columnX[column]
        val right = grid.columnX[column + 1]
        val visibleWidth = viewportWidth - grid.rowNumberWidth
        val target = when {
            left < scroll.offset -> left
            right > scroll.offset + visibleWidth -> right - visibleWidth
            else -> return null
        }
        return target.coerceIn(0f, scroll.max)
    }
}
