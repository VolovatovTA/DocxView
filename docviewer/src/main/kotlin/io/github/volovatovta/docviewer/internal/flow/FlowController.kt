package io.github.volovatovta.docviewer.internal.flow

import androidx.compose.foundation.lazy.LazyListState
import io.github.volovatovta.docviewer.core.FlowMatch
import io.github.volovatovta.docviewer.core.FlowSearch
import io.github.volovatovta.docviewer.core.model.FlowDocument
import io.github.volovatovta.docviewer.internal.ViewerController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

internal object TextScale {
    const val MIN = 0.6f
    const val MAX = 3f

    fun clamp(scale: Float): Float = scale.coerceIn(MIN, MAX)
}

private object FlowNavigation {
    const val ANIMATED_DISTANCE = 60

    // A match counts as visible when its block starts inside this band of the viewport.
    const val COMFORT_TOP = 0.1f
    const val COMFORT_BOTTOM = 0.85f
    const val MAX_CHECKED_BLOCK_HEIGHT_PX = 200
    const val CONTEXT_ABOVE_MATCH = 0.25f
}

internal class FlowController(
    private val document: FlowDocument,
    private val listState: LazyListState,
    var viewportHeight: Float,
    private val currentScale: () -> Float,
    private val onScale: (Float) -> Unit,
) : ViewerController {

    override fun search(query: String): Flow<List<Any>> =
        flow { emit(FlowSearch.search(document, query)) }.flowOn(Dispatchers.Default)

    override suspend fun reveal(hit: Any) {
        val match = hit as FlowMatch
        if (isComfortablyVisible(match.blockIndex)) return
        scrollToItem(match.blockIndex, animate = true)
        listState.showContextAbove(match.blockIndex, viewportHeight * FlowNavigation.CONTEXT_ABOVE_MATCH)
    }

    override suspend fun scrollToItem(index: Int, animate: Boolean) =
        listState.goToItem(index, animate, FlowNavigation.ANIMATED_DISTANCE)

    override suspend fun zoomBy(factor: Float) = zoomTo(currentScale() * factor)

    override suspend fun zoomTo(zoom: Float) = onScale(TextScale.clamp(zoom))

    private fun isComfortablyVisible(blockIndex: Int): Boolean {
        val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == blockIndex } ?: return false
        val bottom = item.offset + minOf(item.size, FlowNavigation.MAX_CHECKED_BLOCK_HEIGHT_PX)
        return item.offset > viewportHeight * FlowNavigation.COMFORT_TOP &&
            bottom < viewportHeight * FlowNavigation.COMFORT_BOTTOM
    }
}
