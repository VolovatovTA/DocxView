package io.github.volovatovta.docviewer.internal.grid

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyListState
import io.github.volovatovta.docviewer.internal.flow.goToItem
import io.github.volovatovta.docviewer.internal.flow.showContextAbove

internal class GridHit(val sheet: Int, val row: Int, val column: Int)

private object GridNavigation {
    const val ANIMATED_DISTANCE = 100

    // A row counts as visible when it ends above this share of the viewport.
    const val VISIBLE_BOTTOM = 0.9f
    const val CONTEXT_ABOVE_MATCH = 1f / 3
    const val HORIZONTAL_SCROLL_MILLIS = 250
}

// Scrolls the grid of the sheet on screen to a row or a cell.
internal class SheetNavigator(
    private val listState: LazyListState,
    private val scroll: GridScroll,
    private val grid: GridLayout,
    var viewportWidth: Float,
    var viewportHeight: Float,
) {

    suspend fun reveal(row: Int, column: Int) {
        if (!isRowVisible(row)) {
            scrollToRow(row, animate = true)
            listState.showContextAbove(row, viewportHeight * GridNavigation.CONTEXT_ABOVE_MATCH)
        }
        val target = horizontalTarget(column) ?: return
        animate(scroll.offset, target, animationSpec = tween(GridNavigation.HORIZONTAL_SCROLL_MILLIS)) { value, _ ->
            scroll.offset = value
        }
    }

    suspend fun scrollToRow(index: Int, animate: Boolean) =
        listState.goToItem(index, animate, GridNavigation.ANIMATED_DISTANCE)

    private fun isRowVisible(row: Int): Boolean = listState.layoutInfo.visibleItemsInfo.any {
        it.index == row && it.offset >= 0 && it.offset + it.size <= viewportHeight * GridNavigation.VISIBLE_BOTTOM
    }

    // Scrolls just enough to bring the column into view, or not at all; a column past the grid stays put.
    private fun horizontalTarget(column: Int): Float? {
        val left = grid.columnX.getOrNull(column) ?: return null
        val right = grid.columnX.getOrNull(column + 1) ?: return null
        val visibleWidth = viewportWidth - grid.rowNumberWidth
        val target = when {
            left < scroll.offset -> left
            right > scroll.offset + visibleWidth -> right - visibleWidth
            else -> return null
        }
        return target.coerceIn(0f, scroll.max)
    }
}
