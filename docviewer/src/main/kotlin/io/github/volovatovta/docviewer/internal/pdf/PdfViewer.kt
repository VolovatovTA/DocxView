package io.github.volovatovta.docviewer.internal.pdf

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import io.github.volovatovta.docviewer.DocumentViewerColors
import io.github.volovatovta.docviewer.DocumentViewerConfig
import io.github.volovatovta.docviewer.DocumentViewerState
import io.github.volovatovta.docviewer.internal.ui.FastScrollbar
import io.github.volovatovta.docviewer.internal.ui.ScrollIndicator
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.roundToInt

private val PageIndicatorTopPadding = 12.dp

@Composable
internal fun PdfViewer(
    document: PdfDocument,
    state: DocumentViewerState,
    config: DocumentViewerConfig,
    colors: DocumentViewerColors,
    modifier: Modifier = Modifier,
) {
    val cache = remember(document) { BitmapCache.sizedForHeap() }
    DisposableEffect(cache) { onDispose { cache.evictAll() } }
    BoxWithConstraints(modifier.fillMaxSize().background(colors.background).clipToBounds()) {
        val viewport = rememberPdfViewport(
            document = document,
            config = config,
            width = constraints.maxWidth.toFloat(),
            height = constraints.maxHeight.toFloat(),
        )
        val images = remember(viewport) { PdfPageImages(document, viewport.layout, cache) }
        SyncWithState(document, viewport, state)
        PdfPageList(
            viewport = viewport,
            images = images,
            tiles = rememberSharpTiles(document, viewport),
            highlights = rememberSearchHighlights(state),
            colors = colors,
            nightFilter = NightModeFilter.takeIf { config.pdfNightMode },
            doubleTapZoom = config.doubleTapZoom,
        )
        if (config.showPageIndicator) {
            PageIndicator(
                viewport = viewport,
                state = state,
                colors = colors,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = PageIndicatorTopPadding),
            )
        }
        if (config.showScrollbar) {
            PdfScrollbar(viewport, colors, Modifier.align(Alignment.CenterEnd))
        }
    }
}

@Composable
private fun rememberPdfViewport(
    document: PdfDocument,
    config: DocumentViewerConfig,
    width: Float,
    height: Float,
): PdfViewport {
    val spacing = with(LocalDensity.current) { config.pageSpacing.toPx() }
    val listState = rememberLazyListState()
    val viewport = remember(document, width, spacing, config.maxZoom) {
        val layout = PdfLayout(document.pageSizes, width, spacing)
        // Extremely tall pages would overflow Compose constraints before reaching the configured zoom.
        val maxZoom = minOf(config.maxZoom, layout.maxRepresentableScale(width.roundToInt())).coerceAtLeast(1f)
        PdfViewport(layout, listState, width, height, maxZoom)
    }
    SideEffect { viewport.height = height }
    return viewport
}

@Composable
private fun rememberSearchHighlights(state: DocumentViewerState): PdfSearchHighlights {
    val hits = remember(state.searchHits) { state.searchHits.filterIsInstance<PdfHit>() }
    val current = hits.getOrNull(state.currentSearchResult)
    return remember(hits, current) { PdfSearchHighlights(hits, current) }
}

// Registers the controller and mirrors page and zoom back into the public state.
@Composable
private fun SyncWithState(document: PdfDocument, viewport: PdfViewport, state: DocumentViewerState) {
    val controller = remember(viewport) { PdfController(document, viewport, state) }
    DisposableEffect(controller) {
        state.controller = controller
        onDispose { if (state.controller === controller) state.controller = null }
    }
    LaunchedEffect(viewport) {
        val page = state.consumeInitialPage()
        if (page in 1 until viewport.layout.pageCount) viewport.listState.scrollToItem(page)
        state.zoom = 1f
    }
    LaunchedEffect(viewport) {
        snapshotFlow { viewport.pageAtCenter }.distinctUntilChanged().collect { state.currentPage = it }
    }
    LaunchedEffect(viewport) {
        snapshotFlow { viewport.scale }.collect { state.zoom = it }
    }
}

@Composable
private fun PageIndicator(
    viewport: PdfViewport,
    state: DocumentViewerState,
    colors: DocumentViewerColors,
    modifier: Modifier = Modifier,
) {
    ScrollIndicator(
        text = pageLabel(state.currentPage, viewport),
        visibleKey = state.currentPage,
        isScrolling = viewport.isScrolling,
        colors = colors,
        modifier = modifier,
    )
}

@Composable
private fun PdfScrollbar(viewport: PdfViewport, colors: DocumentViewerColors, modifier: Modifier = Modifier) {
    FastScrollbar(
        isScrolling = viewport.isScrolling,
        fraction = { viewport.scrollY / viewport.maxScrollY.coerceAtLeast(1f) },
        visibleFraction = { viewport.height / viewport.contentHeight },
        onScrollTo = { fraction -> viewport.scrollTo(fraction * viewport.maxScrollY) },
        colors = colors,
        modifier = modifier,
        label = { fraction -> pageLabel(pageAtScrollFraction(viewport, fraction), viewport) },
    )
}

private fun pageAtScrollFraction(viewport: PdfViewport, fraction: Float): Int {
    val centerY = fraction * viewport.maxScrollY + viewport.height / 2
    return viewport.layout.pageAt(centerY / viewport.scale)
}

private fun pageLabel(page: Int, viewport: PdfViewport): String = "${page + 1} / ${viewport.layout.pageCount}"
