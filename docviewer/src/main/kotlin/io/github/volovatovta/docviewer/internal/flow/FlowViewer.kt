package io.github.volovatovta.docviewer.internal.flow

import android.util.LruCache
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import io.github.volovatovta.docviewer.DocumentViewerColors
import io.github.volovatovta.docviewer.DocumentViewerConfig
import io.github.volovatovta.docviewer.DocumentViewerState
import io.github.volovatovta.docviewer.core.FlowMatch
import io.github.volovatovta.docviewer.core.FlowSearch
import io.github.volovatovta.docviewer.core.model.FlowDocument
import io.github.volovatovta.docviewer.internal.ViewerController
import io.github.volovatovta.docviewer.internal.ui.FastScrollbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlin.math.abs
import kotlin.math.roundToInt

private const val MIN_TEXT_SCALE = 0.6f
private const val MAX_TEXT_SCALE = 3f

@Composable
internal fun FlowViewer(
    document: FlowDocument,
    state: DocumentViewerState,
    config: DocumentViewerConfig,
    colors: DocumentViewerColors,
    onLink: (String) -> Unit,
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = remember { state.consumeInitialPage() }.coerceIn(0, (document.blocks.size - 1).coerceAtLeast(0)))
    var scale by remember(document) { mutableFloatStateOf(state.zoom.coerceIn(MIN_TEXT_SCALE, MAX_TEXT_SCALE)) }
    val hits = remember(state.searchHits) { state.searchHits.filterIsInstance<FlowMatch>() }
    val byParagraph = remember(hits) { FlowSearch.byParagraph(hits) }
    val imageCache = remember(document) {
        object : LruCache<String, ImageBitmap>((Runtime.getRuntime().maxMemory() / 8).coerceAtMost(64L * 1024 * 1024).toInt()) {
            override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
        }
    }
    val ctx = FlowContext(
        colors = colors,
        textSize = config.textSize * scale,
        scale = scale,
        highlights = byParagraph,
        current = hits.getOrNull(state.currentSearchResult),
        onLink = onLink,
        images = document.images,
        imageCache = imageCache,
        tableColumns = remember(document, scale) { java.util.IdentityHashMap() },
    )

    BoxWithConstraints(Modifier.fillMaxSize().background(colors.background)) {
        val viewportHeight = with(LocalDensity.current) { maxHeight.toPx() }
        val controller = remember(document, listState) {
            FlowController(document, listState, viewportHeight, setScale = { scale = it; state.zoom = it }, getScale = { scale })
        }
        controller.viewportHeight = viewportHeight
        DisposableEffect(controller) {
            state.controller = controller
            onDispose { if (state.controller === controller) state.controller = null }
        }
        LaunchedEffect(listState) {
            snapshotFlow { listState.firstVisibleItemIndex }.distinctUntilChanged().collect { state.currentPage = it }
        }

        // The "paper" column spans the full height even when the document is shorter than the screen.
        Box(Modifier.align(Alignment.TopCenter).widthIn(max = config.maxContentWidth).fillMaxWidth().fillMaxHeight().background(colors.page))
        val list: @Composable () -> Unit = {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                itemsIndexed(document.blocks) { index, block ->
                    val top = if (index == 0) 28.dp else 0.dp
                    val bottom = if (index == document.blocks.lastIndex) 48.dp else 0.dp
                    Box(
                        Modifier.widthIn(max = config.maxContentWidth).fillMaxWidth()
                            .padding(start = 20.dp, end = 20.dp, top = top, bottom = bottom),
                    ) {
                        BlockView(block, ctx)
                    }
                }
            }
        }
        Box(
            Modifier.fillMaxSize().pointerInput(Unit) {
                // Two-finger pinch reflows text; consumed in the Initial pass so the list does not scroll meanwhile.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.changes.count { it.pressed } >= 2) {
                            val zoom = event.calculateZoom()
                            if (zoom != 1f) {
                                val next = (scale * zoom).coerceIn(MIN_TEXT_SCALE, MAX_TEXT_SCALE)
                                if (abs(next - scale) > 0.01f) {
                                    scale = (next * 50).roundToInt() / 50f
                                    state.zoom = scale
                                }
                            }
                            event.changes.forEach { it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
        ) {
            if (config.textSelectable) SelectionContainer { list() } else list()
        }
        if (config.showScrollbar) {
            val total = document.blocks.size
            FastScrollbar(
                isScrolling = listState.isScrollInProgress,
                fraction = { listState.scrollFraction() },
                visibleFraction = {
                    val visible = listState.layoutInfo.visibleItemsInfo.size
                    if (total == 0) 1f else (visible.toFloat() / total).coerceAtMost(1f)
                },
                onScrollTo = { f -> listState.requestScrollToItem((f * (total - 1)).roundToInt().coerceAtLeast(0)) },
                colors = colors,
                modifier = Modifier.align(Alignment.CenterEnd),
                label = { f -> "${(f * 100).roundToInt()}%" },
            )
        }
    }
}

internal fun LazyListState.scrollFraction(): Float {
    val info = layoutInfo
    val total = info.totalItemsCount
    val visible = info.visibleItemsInfo
    if (total == 0 || visible.isEmpty()) return 0f
    val first = visible.first()
    val partial = if (first.size > 0) (-first.offset.toFloat() / first.size).coerceIn(0f, 1f) else 0f
    val scrollable = (total - visible.size).coerceAtLeast(1)
    if (!canScrollForward) return 1f
    return ((first.index + partial) / scrollable).coerceIn(0f, 1f)
}

private class FlowController(
    private val document: FlowDocument,
    private val listState: LazyListState,
    var viewportHeight: Float,
    private val setScale: (Float) -> Unit,
    private val getScale: () -> Float,
) : ViewerController {

    override fun search(query: String): Flow<List<Any>> = flow { emit(FlowSearch.search(document, query)) }.flowOn(Dispatchers.Default)

    override suspend fun reveal(hit: Any) {
        hit as FlowMatch
        val visible = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == hit.blockIndex }
        // Matches already comfortably on screen do not need a jump.
        if (visible != null && visible.offset > viewportHeight * 0.1f && visible.offset + minOf(visible.size, 200) < viewportHeight * 0.85f) return
        if (abs(hit.blockIndex - listState.firstVisibleItemIndex) > 60) listState.scrollToItem(hit.blockIndex) else listState.animateScrollToItem(hit.blockIndex)
        if (hit.blockIndex > 0 && listState.canScrollBackward) listState.animateScrollBy(-viewportHeight * 0.25f)
    }

    override suspend fun scrollToItem(index: Int, animate: Boolean) {
        if (animate && abs(index - listState.firstVisibleItemIndex) < 60) listState.animateScrollToItem(index) else listState.scrollToItem(index)
    }

    override suspend fun zoomBy(factor: Float) = zoomTo(getScale() * factor)

    override suspend fun zoomTo(zoom: Float) = setScale(zoom.coerceIn(MIN_TEXT_SCALE, MAX_TEXT_SCALE))
}
