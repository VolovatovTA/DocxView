package io.github.volovatovta.docviewer.internal.pdf

import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntRect
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.roundToInt

private const val TILE_DEBOUNCE_MS = 140L
private const val TILE_MIN_UPSCALE = 1.08f

// Once scrolling and zooming settle, each visible page gets a tile of its visible region at full resolution.
@Composable
internal fun rememberSharpTiles(document: PdfDocument, viewport: PdfViewport): Map<Int, PdfTile> {
    var tiles by remember(viewport) { mutableStateOf(emptyMap<Int, PdfTile>()) }
    LaunchedEffect(viewport) {
        snapshotFlow { viewport.tileTrigger() }.collectLatest { trigger ->
            if (!trigger.settled) return@collectLatest
            delay(TILE_DEBOUNCE_MS)
            tiles = if (viewport.needsTiles()) {
                renderVisibleTiles(document, viewport, tiles) { partial -> tiles = partial }
            } else {
                emptyMap()
            }
        }
    }
    return tiles
}

internal class PdfTile(val pageWidthPx: Int, val region: IntRect, val bitmap: ImageBitmap) {
    fun covers(pageWidthPx: Int, region: IntRect): Boolean = this.pageWidthPx == pageWidthPx && this.region == region
}

private data class TileTrigger(val scale: Float, val left: Int, val top: Int, val settled: Boolean)

private fun PdfViewport.tileTrigger() =
    TileTrigger(scale, horizontalOffset.roundToInt(), scrollY.roundToInt(), isSettled)

private fun PdfViewport.needsTiles(): Boolean = layout.pageWidth(scale) > layout.baseBitmapWidth * TILE_MIN_UPSCALE

private suspend fun renderVisibleTiles(
    document: PdfDocument,
    viewport: PdfViewport,
    previous: Map<Int, PdfTile>,
    onTileRendered: (Map<Int, PdfTile>) -> Unit,
): Map<Int, PdfTile> {
    val fresh = HashMap<Int, PdfTile>()
    val visiblePages = viewport.listState.layoutInfo.visibleItemsInfo.filter { it.index < viewport.layout.pageCount }
    for (item in visiblePages) {
        val reused = previous[item.index]
        val tile = tileFor(document, viewport, item, reused) ?: continue
        fresh[item.index] = tile
        // Publish every new tile right away so the screen sharpens page by page.
        if (tile !== reused) onTileRendered(previous + fresh)
    }
    return fresh
}

private suspend fun tileFor(
    document: PdfDocument,
    viewport: PdfViewport,
    item: LazyListItemInfo,
    reused: PdfTile?,
): PdfTile? {
    val page = item.index
    val region = viewport.visibleRegion(item) ?: return null
    val pageWidth = viewport.layout.pageWidth(viewport.scale)
    if (reused != null && reused.covers(pageWidth, region)) return reused
    val pageHeight = viewport.layout.pageHeight(page, viewport.scale)
    val bitmap = document.render(page, pageWidth, pageHeight, region.left, region.top, region.width, region.height)
    return bitmap?.let { PdfTile(pageWidth, region, it.asImageBitmap()) }
}

// The part of a page inside the viewport, in the page's own pixel coordinates.
private fun PdfViewport.visibleRegion(item: LazyListItemInfo): IntRect? {
    val pageLeft = pageLeft.roundToInt()
    val pageTop = item.offset + layout.gap(scale)
    val onScreen = IntRect(-pageLeft, -pageTop, width.toInt() - pageLeft, height.toInt() - pageTop)
    val page = IntRect(0, 0, layout.pageWidth(scale), layout.pageHeight(item.index, scale))
    return onScreen.intersect(page).takeUnless { it.isEmpty }
}
