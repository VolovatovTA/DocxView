package io.github.volovatovta.docviewer.internal.pdf

import android.graphics.Bitmap
import android.graphics.RectF
import android.util.LruCache
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.tween
import androidx.compose.animation.splineBasedDecay
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.volovatovta.docviewer.DocumentViewerColors
import io.github.volovatovta.docviewer.DocumentViewerConfig
import io.github.volovatovta.docviewer.DocumentViewerState
import io.github.volovatovta.docviewer.R
import io.github.volovatovta.docviewer.internal.ViewerController
import io.github.volovatovta.docviewer.internal.ui.FastScrollbar
import io.github.volovatovta.docviewer.internal.ui.ScrollIndicator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

internal class PdfHit(val page: Int, val rects: List<RectF>)

private class Tile(val page: Int, val pageWidthPx: Int, val left: Int, val top: Int, val bitmap: ImageBitmap)

// Geometry at zoom 1; everything scales linearly. Item i = gap above the page + the page.
private class PdfLayout(sizes: List<PdfPageSize>, viewportWidth: Float, val spacing: Float) {
    val count = sizes.size
    val pageWidth0 = (viewportWidth - 2 * spacing).coerceAtLeast(1f)
    val pageHeights0 = FloatArray(count) { pageWidth0 * sizes[it].height / sizes[it].width }
    val pageWidthsPt = FloatArray(count) { sizes[it].width }
    private val prefix = FloatArray(count + 1).also { p -> for (i in 0 until count) p[i + 1] = p[i] + pageHeights0[i] + spacing }
    val total0 = prefix[count] + spacing

    // Rounded prefix sums keep item heights consistent with absolute offsets at any zoom.
    fun top(index: Int, scale: Float): Int = (prefix[index.coerceIn(0, count)] * scale).roundToInt()

    fun itemHeight(index: Int, scale: Float): Int = top(index + 1, scale) - top(index, scale)

    fun indexAt(y0: Float): Int {
        var lo = 0
        var hi = count - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) ushr 1
            if (prefix[mid] <= y0) lo = mid else hi = mid - 1
        }
        return lo.coerceAtLeast(0)
    }
}

@Stable
private class PdfViewport(
    val layout: PdfLayout,
    val listState: LazyListState,
    val width: Float,
    height: Float,
    val maxZoom: Float,
) {
    // Height changes with the keyboard or split-screen; it must not reset zoom or position.
    var height by mutableFloatStateOf(height)
    var scale by mutableFloatStateOf(1f)
    var hOffset by mutableFloatStateOf(0f)
    var gestureZoom by mutableFloatStateOf(1f)
    var pivot by mutableStateOf(Offset.Zero)
    var interacting by mutableStateOf(false)
    private var flingJob: Job? = null

    val maxHorizontal: Float get() = (width * scale - width).coerceAtLeast(0f)
    val totalHeight: Float get() = layout.total0 * scale

    fun absY(): Float = layout.top(listState.firstVisibleItemIndex, scale) + listState.firstVisibleItemScrollOffset.toFloat()

    fun pageAtCenter(): Int = layout.indexAt((absY() + height / 2) / scale).coerceIn(0, layout.count - 1)

    fun scrollBy(dx: Float, dy: Float) {
        hOffset = (hOffset + dx).coerceIn(0f, maxHorizontal)
        if (dy != 0f) listState.dispatchRawDelta(dy)
    }

    fun scrollToAbsolute(y: Float) {
        val clamped = y.coerceIn(0f, (totalHeight - height).coerceAtLeast(0f))
        val index = layout.indexAt(clamped / scale)
        listState.requestScrollToItem(index, (clamped - layout.top(index, scale)).roundToInt())
    }

    fun commitZoom(target: Float, focus: Offset) {
        val newScale = target.coerceIn(1f, maxZoom)
        val k = newScale / scale
        val y = (absY() + focus.y) * k - focus.y
        val x = (hOffset + focus.x) * k - focus.x
        scale = newScale
        gestureZoom = 1f
        hOffset = x.coerceIn(0f, maxHorizontal)
        scrollToAbsolute(y)
    }

    fun startPinch(centroid: Offset) {
        stopFling()
        pivot = centroid
        gestureZoom = 1f
    }

    fun pinch(zoomChange: Float, pan: Offset) {
        gestureZoom = (gestureZoom * zoomChange).coerceIn(0.5f / scale, maxZoom * 1.5f / scale)
        scrollBy(-pan.x / gestureZoom, -pan.y / gestureZoom)
    }

    suspend fun settleZoom() {
        val target = (scale * gestureZoom).coerceIn(1f, maxZoom)
        if (abs(target - scale * gestureZoom) > 0.001f) {
            animate(gestureZoom, target / scale, animationSpec = tween(180)) { value, _ -> gestureZoom = value }
        }
        commitZoom(target, pivot)
    }

    suspend fun animateZoomTo(target: Float, focus: Offset) {
        val clamped = target.coerceIn(1f, maxZoom)
        if (abs(clamped - scale) < 0.001f) return
        stopFling()
        // When zooming out to fit-width, pick the pivot that lands exactly where the commit will clamp.
        pivot = if (clamped == 1f && scale > 1f) Offset(hOffset / (scale - 1f), focus.y) else focus
        interacting = true
        try {
            animate(1f, clamped / scale, animationSpec = tween(260)) { value, _ -> gestureZoom = value }
        } finally {
            // Commit wherever the animation stopped, otherwise a cancelled zoom leaves the transient transform behind.
            commitZoom(scale * gestureZoom, pivot)
            interacting = false
        }
    }

    fun fling(scope: CoroutineScope, vx: Float, vy: Float, decay: DecayAnimationSpec<Float>) {
        flingJob = scope.launch {
            try {
                val vertical = launch {
                    if (abs(vy) < 50f) return@launch
                    listState.scroll {
                        var last = 0f
                        AnimationState(0f, vy).animateDecay(decay) {
                            val delta = value - last
                            last = value
                            if (abs(delta - scrollBy(delta)) > 0.5f) cancelAnimation()
                        }
                    }
                }
                val horizontal = launch {
                    if (abs(vx) < 50f || maxHorizontal <= 0f) return@launch
                    var last = 0f
                    AnimationState(0f, vx).animateDecay(decay) {
                        val delta = value - last
                        last = value
                        val before = hOffset
                        hOffset = (hOffset + delta).coerceIn(0f, maxHorizontal)
                        if (hOffset == before) cancelAnimation()
                    }
                }
                vertical.join()
                horizontal.join()
            } finally {
                interacting = false
            }
        }
    }

    fun stopFling() {
        flingJob?.cancel()
        flingJob = null
    }
}

private class BitmapCache(maxBytes: Int) : LruCache<String, Bitmap>(maxBytes) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
}

private val InvertMatrix = ColorMatrix(floatArrayOf(-1f, 0f, 0f, 0f, 255f, 0f, -1f, 0f, 0f, 255f, 0f, 0f, -1f, 0f, 255f, 0f, 0f, 0f, 1f, 0f))

@Composable
internal fun PdfViewer(document: PdfDocument, state: DocumentViewerState, config: DocumentViewerConfig, colors: DocumentViewerColors) {
    val cache = remember(document) {
        BitmapCache((Runtime.getRuntime().maxMemory() / 6).coerceAtMost(128L * 1024 * 1024).toInt())
    }
    DisposableEffect(cache) { onDispose { cache.evictAll() } }
    val nightFilter = remember(config.pdfNightMode) { if (config.pdfNightMode) ColorFilter.colorMatrix(InvertMatrix) else null }
    BoxWithConstraints(Modifier.fillMaxSize().background(colors.background).clipToBounds()) {
        val density = LocalDensity.current
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()
        val spacing = with(density) { config.pageSpacing.toPx() }
        val listState = rememberLazyListState()
        val viewport = remember(document, width, spacing, config.maxZoom) {
            PdfViewport(PdfLayout(document.pageSizes, width, spacing), listState, width, height, config.maxZoom)
        }
        viewport.height = height
        val layout = viewport.layout
        val scope = rememberCoroutineScope()
        var tiles by remember(viewport) { mutableStateOf<Map<Int, Tile>>(emptyMap()) }
        val hits = state.searchHits.filterIsInstance<PdfHit>()
        val currentHit = hits.getOrNull(state.currentSearchResult)
        val hitsByPage = remember(hits) { hits.groupBy { it.page } }

        val baseWidth = remember(layout) {
            // ~2.5 MP per base bitmap keeps a dozen cached pages within budget; tiles add sharpness when zoomed.
            val tallest = (0 until layout.count).maxOfOrNull { layout.pageHeights0[it] / layout.pageWidth0 } ?: 1.4f
            minOf(layout.pageWidth0, sqrt(2_500_000f / tallest))
        }

        val controller = remember(viewport) { PdfController(document, viewport, state) }
        DisposableEffect(controller) {
            state.controller = controller
            onDispose { if (state.controller === controller) state.controller = null }
        }
        LaunchedEffect(viewport) {
            val page = state.consumeInitialPage()
            if (page in 1 until layout.count) listState.scrollToItem(page)
            state.zoom = 1f
        }
        LaunchedEffect(viewport) {
            snapshotFlow { viewport.pageAtCenter() }.distinctUntilChanged().collect { state.currentPage = it }
        }
        LaunchedEffect(viewport) {
            snapshotFlow { viewport.scale }.collect { state.zoom = it }
        }
        LaunchedEffect(viewport) {
            snapshotFlow {
                listOf(viewport.scale, viewport.hOffset.roundToInt(), viewport.absY().roundToInt(), viewport.gestureZoom, viewport.interacting)
            }.collectLatest {
                if (viewport.gestureZoom != 1f || viewport.interacting) return@collectLatest
                delay(140)
                val pageWidth = (layout.pageWidth0 * viewport.scale).roundToInt()
                if (pageWidth <= baseWidth * 1.08f) {
                    tiles = emptyMap()
                    return@collectLatest
                }
                val fresh = HashMap<Int, Tile>()
                for (item in listState.layoutInfo.visibleItemsInfo) {
                    val index = item.index
                    if (index >= layout.count) continue
                    val pageHeight = (layout.pageHeights0[index] * viewport.scale).roundToInt()
                    val pageTop = item.offset + (spacing * viewport.scale).roundToInt()
                    val pageLeft = (spacing * viewport.scale - viewport.hOffset).roundToInt()
                    val left = (-pageLeft).coerceAtLeast(0)
                    val top = (-pageTop).coerceAtLeast(0)
                    val right = (width.toInt() - pageLeft).coerceAtMost(pageWidth)
                    val bottom = (height.toInt() - pageTop).coerceAtMost(pageHeight)
                    if (right <= left || bottom <= top) continue
                    val existing = tiles[index]
                    if (existing != null && existing.pageWidthPx == pageWidth && existing.left == left && existing.top == top &&
                        existing.bitmap.width == right - left && existing.bitmap.height == bottom - top
                    ) {
                        fresh[index] = existing
                        continue
                    }
                    val bitmap = document.render(index, pageWidth, pageHeight, left, top, right - left, bottom - top) ?: continue
                    fresh[index] = Tile(index, pageWidth, left, top, bitmap.asImageBitmap())
                    tiles = tiles + (index to fresh.getValue(index))
                }
                tiles = fresh
            }
        }

        val decay = splineBasedDecay<Float>(density)
        val viewConfiguration = androidx.compose.ui.platform.LocalViewConfiguration.current
        val pageLabel = { page: Int -> "$page / ${layout.count}" }

        LazyColumn(
            state = listState,
            userScrollEnabled = false,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val zoom = viewport.gestureZoom
                    scaleX = zoom
                    scaleY = zoom
                    transformOrigin = TransformOrigin(viewport.pivot.x / width.coerceAtLeast(1f), viewport.pivot.y / height.coerceAtLeast(1f))
                }
                .pointerInput(viewport) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        viewport.stopFling()
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        var pinching = false
                        var dragging = false
                        var travelled = 0f
                        while (true) {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.isEmpty()) break
                            if (pressed.size >= 2 || pinching) {
                                if (!pinching) {
                                    pinching = true
                                    viewport.interacting = true
                                    viewport.startPinch(event.calculateCentroid(useCurrent = true))
                                }
                                if (pressed.size >= 2) viewport.pinch(event.calculateZoom(), event.calculatePan())
                                event.changes.forEach { it.consume() }
                            } else {
                                val change = pressed.first()
                                val delta = change.positionChange()
                                if (!dragging) {
                                    travelled += delta.getDistance()
                                    if (travelled > viewConfiguration.touchSlop) {
                                        dragging = true
                                        viewport.interacting = true
                                    }
                                }
                                if (dragging) {
                                    viewport.scrollBy(-delta.x, -delta.y)
                                    change.consume()
                                }
                                tracker.addPosition(change.uptimeMillis, change.position)
                            }
                        }
                        when {
                            pinching -> scope.launch {
                                viewport.settleZoom()
                                viewport.interacting = false
                            }
                            dragging -> {
                                val velocity = tracker.calculateVelocity()
                                viewport.fling(scope, -velocity.x, -velocity.y, decay)
                            }
                        }
                    }
                }
                .pointerInput(viewport) {
                    detectTapGestures(onDoubleTap = { position ->
                        scope.launch {
                            val target = if (viewport.scale > 1.05f) 1f else config.doubleTapZoom
                            viewport.animateZoomTo(target, position)
                        }
                    })
                }
                .pointerInput(viewport) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type != PointerEventType.Scroll) continue
                            val delta = event.changes.first().scrollDelta
                            if (event.keyboardModifiers.isCtrlPressed) {
                                val focus = event.changes.first().position
                                viewport.commitZoom(viewport.scale * if (delta.y < 0) 1.15f else 1f / 1.15f, focus)
                            } else {
                                viewport.scrollBy(delta.x * 64f, delta.y * 64f)
                            }
                            event.changes.forEach { it.consume() }
                        }
                    }
                },
        ) {
            items(layout.count, key = { it }) { index ->
                val scale = viewport.scale
                val pageWidth = (layout.pageWidth0 * scale).roundToInt()
                val pageHeight = (layout.pageHeights0[index] * scale).roundToInt()
                val itemHeight = layout.itemHeight(index, scale)
                val gap = (spacing * scale).roundToInt()
                val pageDescription = stringResource(R.string.dv_page_description, index + 1, layout.count)
                PdfPage(
                    document = document,
                    index = index,
                    baseWidth = baseWidth.roundToInt(),
                    baseHeight = (baseWidth * layout.pageHeights0[index] / layout.pageWidth0).roundToInt(),
                    cache = cache,
                    tile = tiles[index],
                    hits = hitsByPage[index],
                    currentHit = currentHit?.takeIf { it.page == index },
                    pageWidthPt = layout.pageWidthsPt[index],
                    colors = colors,
                    colorFilter = nightFilter,
                    modifier = Modifier
                        .semantics { contentDescription = pageDescription }
                        .layout { measurable, constraints ->
                            val placeable = measurable.measure(Constraints.fixed(pageWidth, pageHeight))
                            layout(constraints.maxWidth, itemHeight) {
                                placeable.place((spacing * scale - viewport.hOffset).roundToInt(), gap)
                            }
                        },
                )
            }
            item(key = "end") {
                Spacer(Modifier.layout { measurable, constraints ->
                    val p = measurable.measure(Constraints.fixed(constraints.maxWidth, (spacing * viewport.scale).roundToInt()))
                    layout(p.width, p.height) { p.place(0, 0) }
                })
            }
        }

        val scrolling = viewport.interacting || listState.isScrollInProgress
        if (config.showPageIndicator) {
            ScrollIndicator(
                text = pageLabel(state.currentPage + 1),
                visibleKey = state.currentPage,
                isScrolling = scrolling,
                colors = colors,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
            )
        }
        if (config.showScrollbar) {
            FastScrollbar(
                isScrolling = scrolling,
                fraction = { viewport.absY() / (viewport.totalHeight - height).coerceAtLeast(1f) },
                visibleFraction = { height / viewport.totalHeight },
                onScrollTo = { f -> viewport.scrollToAbsolute(f * (viewport.totalHeight - height)) },
                colors = colors,
                modifier = Modifier.align(Alignment.CenterEnd),
                label = { f ->
                    val y = f * (viewport.totalHeight - height) + height / 2
                    pageLabel(layout.indexAt(y / viewport.scale) + 1)
                },
            )
        }
    }
}

@Composable
private fun PdfPage(
    document: PdfDocument,
    index: Int,
    baseWidth: Int,
    baseHeight: Int,
    cache: BitmapCache,
    tile: Tile?,
    hits: List<PdfHit>?,
    currentHit: PdfHit?,
    pageWidthPt: Float,
    colors: DocumentViewerColors,
    colorFilter: ColorFilter?,
    modifier: Modifier,
) {
    val key = "$index@$baseWidth"
    val base by produceState(cache.get(key)?.asImageBitmap(), key) {
        if (value == null) {
            val bitmap = document.render(index, baseWidth, baseHeight)
            if (bitmap != null) {
                cache.put(key, bitmap)
                value = bitmap.asImageBitmap()
            }
        }
    }
    val paper = if (colorFilter != null) Color.Black else Color.White
    Canvas(modifier.shadow(1.5.dp).background(paper)) {
        base?.let { image ->
            drawImage(
                image,
                dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                filterQuality = FilterQuality.Medium,
                colorFilter = colorFilter,
            )
        }
        if (tile != null) {
            val k = size.width / tile.pageWidthPx
            drawImage(
                tile.bitmap,
                dstOffset = IntOffset((tile.left * k).roundToInt(), (tile.top * k).roundToInt()),
                dstSize = IntSize((tile.bitmap.width * k).roundToInt(), (tile.bitmap.height * k).roundToInt()),
                filterQuality = FilterQuality.Low,
                colorFilter = colorFilter,
            )
        }
        if (hits != null) {
            val k = size.width / pageWidthPt
            for (hit in hits) {
                val color = if (hit === currentHit) colors.currentSearchMatch else colors.searchMatch
                for (r in hit.rects) {
                    drawRect(color, Offset(r.left * k, r.top * k), Size(r.width() * k, r.height() * k))
                }
            }
        }
    }
}

private class PdfController(
    private val document: PdfDocument,
    private val viewport: PdfViewport,
    private val state: DocumentViewerState,
) : ViewerController {

    override fun search(query: String): Flow<List<Any>> = flow {
        val hits = ArrayList<PdfHit>()
        for (page in 0 until document.pageCount) {
            val matches = document.search(page, query)
            if (matches.isNotEmpty()) {
                matches.forEach { hits += PdfHit(page, it) }
                emit(hits.toList())
            }
        }
        if (hits.isEmpty()) emit(emptyList())
    }

    override suspend fun reveal(hit: Any) {
        hit as PdfHit
        val layout = viewport.layout
        val scale = viewport.scale
        val k = layout.pageWidth0 * scale / layout.pageWidthsPt[hit.page]
        val centerX = hit.rects.map { it.centerX() }.average().toFloat() * k
        val centerY = hit.rects.map { it.centerY() }.average().toFloat() * k
        val pageTop = layout.top(hit.page, scale) + layout.spacing * scale
        val targetY = pageTop + centerY - viewport.height / 2
        if (scale > 1f) {
            val targetX = (layout.spacing * scale + centerX - viewport.width / 2).coerceIn(0f, viewport.maxHorizontal)
            val startX = viewport.hOffset
            kotlinx.coroutines.coroutineScope {
                launch { animate(startX, targetX, animationSpec = tween(300)) { v, _ -> viewport.hOffset = v } }
                launch { viewport.listState.animateScrollBy(targetY - viewport.absY(), tween(300)) }
            }
        } else {
            val distance = targetY - viewport.absY()
            if (abs(distance) > viewport.height * 6) viewport.scrollToAbsolute(targetY) else viewport.listState.animateScrollBy(distance, tween(300))
        }
    }

    override suspend fun scrollToItem(index: Int, animate: Boolean) {
        val target = viewport.layout.top(index, viewport.scale).toFloat()
        val distance = target - viewport.absY()
        if (!animate || abs(distance) > viewport.height * 6) {
            viewport.scrollToAbsolute(target)
        } else {
            viewport.listState.animateScrollBy(distance, tween(320))
        }
    }

    override suspend fun zoomBy(factor: Float) = zoomTo(viewport.scale * factor)

    override suspend fun zoomTo(zoom: Float) {
        viewport.animateZoomTo(zoom, Offset(viewport.width / 2, viewport.height / 2))
        state.zoom = viewport.scale
    }
}
