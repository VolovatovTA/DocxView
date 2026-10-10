package io.github.volovatovta.docviewer.internal.flow

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import io.github.volovatovta.docviewer.DocumentViewerColors
import io.github.volovatovta.docviewer.DocumentViewerState
import io.github.volovatovta.docviewer.internal.ViewerController
import io.github.volovatovta.docviewer.internal.ui.FastScrollbar
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.abs
import kotlin.math.roundToInt

// Shared by the reflowable-text and CSV viewers, which are both lazy lists of items.

internal fun LazyListState.scrollFraction(): Float {
    val total = layoutInfo.totalItemsCount
    val visible = layoutInfo.visibleItemsInfo
    if (total == 0 || visible.isEmpty()) return 0f
    if (!canScrollForward) return 1f
    val first = visible.first()
    val partial = if (first.size > 0) (-first.offset.toFloat() / first.size).coerceIn(0f, 1f) else 0f
    val scrollable = (total - visible.size).coerceAtLeast(1)
    return ((first.index + partial) / scrollable).coerceIn(0f, 1f)
}

internal fun LazyListState.visibleFraction(): Float {
    val total = layoutInfo.totalItemsCount
    return if (total == 0) 1f else (layoutInfo.visibleItemsInfo.size.toFloat() / total).coerceAtMost(1f)
}

internal fun LazyListState.jumpToFraction(fraction: Float) {
    val lastIndex = layoutInfo.totalItemsCount - 1
    requestScrollToItem((fraction * lastIndex).roundToInt().coerceAtLeast(0))
}

// Long jumps are instant: animating through thousands of items only blurs the screen.
internal suspend fun LazyListState.goToItem(index: Int, animate: Boolean, animatedDistance: Int) {
    if (animate && abs(index - firstVisibleItemIndex) <= animatedDistance) {
        animateScrollToItem(index)
    } else {
        scrollToItem(index)
    }
}

// Leaves some of the previous content visible above the revealed item.
internal suspend fun LazyListState.showContextAbove(index: Int, pixels: Float) {
    if (index > 0 && canScrollBackward) animateScrollBy(-pixels)
}

@Composable
internal fun BindController(state: DocumentViewerState, controller: ViewerController) {
    DisposableEffect(controller) {
        state.controller = controller
        onDispose { if (state.controller === controller) state.controller = null }
    }
}

@Composable
internal fun TrackCurrentItem(listState: LazyListState, state: DocumentViewerState) {
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }.distinctUntilChanged().collect { state.currentPage = it }
    }
}

@Composable
internal fun ListFastScrollbar(
    listState: LazyListState,
    colors: DocumentViewerColors,
    label: (Float) -> String,
    modifier: Modifier = Modifier,
) {
    FastScrollbar(
        isScrolling = listState.isScrollInProgress,
        fraction = { listState.scrollFraction() },
        visibleFraction = { listState.visibleFraction() },
        onScrollTo = { listState.jumpToFraction(it) },
        colors = colors,
        modifier = modifier,
        label = label,
    )
}
