package io.github.volovatovta.docviewer.internal.flow

import android.util.LruCache
import androidx.compose.foundation.background
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.volovatovta.docviewer.DocumentViewerColors
import io.github.volovatovta.docviewer.DocumentViewerConfig
import io.github.volovatovta.docviewer.DocumentViewerState
import io.github.volovatovta.docviewer.core.FlowMatch
import io.github.volovatovta.docviewer.core.FlowSearch
import io.github.volovatovta.docviewer.core.model.FlowDocument
import io.github.volovatovta.docviewer.core.model.Table
import java.util.IdentityHashMap
import kotlin.math.abs
import kotlin.math.roundToInt

private object FlowViewerSpec {
    val HorizontalPadding = 20.dp
    val TopPadding = 28.dp
    val BottomPadding = 48.dp

    // Pinch scale snaps to steps of 1/50 so tiny finger jitter does not reflow the whole document.
    const val SCALE_STEPS = 50
    const val MIN_SCALE_CHANGE = 0.01f
    const val PERCENT = 100
}

private object ImageCacheSize {
    const val HEAP_SHARE_DIVISOR = 8
    const val MAX_BYTES = 64L * 1024 * 1024
    const val BYTES_PER_PIXEL = 4
}

@Composable
internal fun FlowViewer(
    document: FlowDocument,
    state: DocumentViewerState,
    config: DocumentViewerConfig,
    colors: DocumentViewerColors,
    onLink: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val initialItem = remember { state.consumeInitialPage() }.coerceIn(0, document.blocks.lastIndex.coerceAtLeast(0))
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialItem)
    var scale by remember(document) { mutableFloatStateOf(TextScale.clamp(state.zoom)) }
    val applyScale: (Float) -> Unit = { value ->
        scale = value
        state.zoom = value
    }
    val context = rememberFlowContext(document, state, config, colors, onLink, scale)

    BoxWithConstraints(modifier.fillMaxSize().background(colors.background)) {
        val viewportHeight = with(LocalDensity.current) { maxHeight.toPx() }
        val controller = remember(document, listState) {
            FlowController(document, listState, viewportHeight, currentScale = { scale }, onScale = applyScale)
        }
        controller.viewportHeight = viewportHeight
        BindController(state, controller)
        TrackCurrentItem(listState, state)

        // The "paper" column spans the full height even when the document is shorter than the screen.
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = config.maxContentWidth)
                .fillMaxWidth()
                .fillMaxHeight()
                .background(colors.page),
        )
        Box(Modifier.fillMaxSize().pinchToScaleText(currentScale = { scale }, onScale = applyScale)) {
            if (config.textSelectable) {
                SelectionContainer { FlowList(document, listState, context, config.maxContentWidth) }
            } else {
                FlowList(document, listState, context, config.maxContentWidth)
            }
        }
        if (config.showScrollbar) {
            ListFastScrollbar(
                listState = listState,
                colors = colors,
                label = { fraction -> "${(fraction * FlowViewerSpec.PERCENT).roundToInt()}%" },
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
    }
}

@Composable
private fun FlowList(
    document: FlowDocument,
    listState: LazyListState,
    context: FlowContext,
    maxContentWidth: Dp,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        itemsIndexed(document.blocks) { index, block ->
            Box(
                Modifier
                    .widthIn(max = maxContentWidth)
                    .fillMaxWidth()
                    .padding(
                        start = FlowViewerSpec.HorizontalPadding,
                        end = FlowViewerSpec.HorizontalPadding,
                        top = if (index == 0) FlowViewerSpec.TopPadding else 0.dp,
                        bottom = if (index == document.blocks.lastIndex) FlowViewerSpec.BottomPadding else 0.dp,
                    ),
            ) {
                BlockView(block, context)
            }
        }
    }
}

@Composable
private fun rememberFlowContext(
    document: FlowDocument,
    state: DocumentViewerState,
    config: DocumentViewerConfig,
    colors: DocumentViewerColors,
    onLink: (String) -> Unit,
    scale: Float,
): FlowContext {
    val hits = remember(state.searchHits) { state.searchHits.filterIsInstance<FlowMatch>() }
    val highlights = remember(hits) { FlowSearch.byParagraph(hits) }
    val imageCache = remember(document) { ImageBitmapCache() }
    val tableColumns = remember(document, scale) { IdentityHashMap<Table, ColumnContent>() }
    return FlowContext(
        colors = colors,
        textSize = config.textSize * scale,
        scale = scale,
        search = SearchHighlights(highlights, hits.getOrNull(state.currentSearchResult)),
        onLink = onLink,
        images = FlowImages(document.images, imageCache),
        tableColumns = tableColumns,
    )
}

private class ImageBitmapCache : LruCache<String, ImageBitmap>(maxBytes()) {
    override fun sizeOf(key: String, value: ImageBitmap): Int =
        value.width * value.height * ImageCacheSize.BYTES_PER_PIXEL

    private companion object {
        fun maxBytes(): Int {
            val heapShare = Runtime.getRuntime().maxMemory() / ImageCacheSize.HEAP_SHARE_DIVISOR
            return heapShare.coerceAtMost(ImageCacheSize.MAX_BYTES).toInt()
        }
    }
}

// Two-finger pinch reflows text; consumed in the Initial pass so the list does not scroll meanwhile.
private fun Modifier.pinchToScaleText(currentScale: () -> Float, onScale: (Float) -> Unit): Modifier =
    pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.changes.count { it.pressed } >= 2) {
                    snappedScale(currentScale(), event.calculateZoom())?.let(onScale)
                    event.changes.forEach { it.consume() }
                }
            } while (event.changes.any { it.pressed })
        }
    }

private fun snappedScale(current: Float, zoom: Float): Float? {
    if (zoom == 1f) return null
    val next = TextScale.clamp(current * zoom)
    if (abs(next - current) <= FlowViewerSpec.MIN_SCALE_CHANGE) return null
    return (next * FlowViewerSpec.SCALE_STEPS).roundToInt() / FlowViewerSpec.SCALE_STEPS.toFloat()
}
