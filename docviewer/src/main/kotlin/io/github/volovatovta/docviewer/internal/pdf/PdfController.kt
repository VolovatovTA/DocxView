package io.github.volovatovta.docviewer.internal.pdf

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.ui.geometry.Offset
import io.github.volovatovta.docviewer.DocumentViewerState
import io.github.volovatovta.docviewer.internal.ViewerController
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlin.math.abs

internal class PdfController(
    private val document: PdfDocument,
    private val viewport: PdfViewport,
    private val state: DocumentViewerState,
) : ViewerController {

    // Streams hits page by page, so the first match shows up long before a big document is fully scanned.
    override fun search(query: String): Flow<List<Any>> = flow {
        val hits = ArrayList<PdfHit>()
        for (page in 0 until document.pageCount) {
            val matches = document.search(page, query)
            if (matches.isNotEmpty()) {
                matches.mapTo(hits) { rects -> PdfHit(page, rects) }
                emit(hits.toList())
            }
        }
        if (hits.isEmpty()) emit(emptyList())
    }

    override suspend fun reveal(hit: Any) {
        val center = contentCenterOf(hit as PdfHit)
        val targetY = center.y - viewport.height / 2
        if (viewport.scale > 1f) {
            val targetX = (center.x - viewport.width / 2).coerceIn(0f, viewport.maxHorizontalOffset)
            coroutineScope {
                launch {
                    animate(viewport.horizontalOffset, targetX, animationSpec = tween(REVEAL_DURATION_MS)) { value, _ ->
                        viewport.horizontalOffset = value
                    }
                }
                launch { viewport.listState.animateScrollBy(targetY - viewport.scrollY, tween(REVEAL_DURATION_MS)) }
            }
        } else {
            scrollOrJump(targetY, REVEAL_DURATION_MS)
        }
    }

    override suspend fun scrollToItem(index: Int, animate: Boolean) {
        val page = index.coerceIn(0, (viewport.layout.pageCount - 1).coerceAtLeast(0))
        val target = viewport.layout.top(page, viewport.scale).toFloat()
        if (animate) scrollOrJump(target, SCROLL_DURATION_MS) else viewport.scrollTo(target)
    }

    override suspend fun zoomBy(factor: Float) = zoomTo(viewport.scale * factor)

    override suspend fun zoomTo(zoom: Float) {
        viewport.animateZoomTo(zoom, Offset(viewport.width / 2, viewport.height / 2))
        state.zoom = viewport.scale
    }

    // Animating across many screens would render every page on the way, so long distances jump instead.
    private suspend fun scrollOrJump(targetY: Float, durationMs: Int) {
        val distance = targetY - viewport.scrollY
        if (abs(distance) > viewport.height * JUMP_DISTANCE_SCREENS) {
            viewport.scrollTo(targetY)
        } else {
            viewport.listState.animateScrollBy(distance, tween(durationMs))
        }
    }

    private fun contentCenterOf(hit: PdfHit): Offset {
        val layout = viewport.layout
        val scale = viewport.scale
        val pointsToPixels = layout.fitPageWidth * scale / layout.pageWidthPt(hit.page)
        val gap = layout.spacing * scale
        val x = hit.rects.map { it.centerX() }.average().toFloat() * pointsToPixels + gap
        val y = hit.rects.map { it.centerY() }.average().toFloat() * pointsToPixels + layout.top(hit.page, scale) + gap
        return Offset(x, y)
    }

    private companion object {
        const val REVEAL_DURATION_MS = 300
        const val SCROLL_DURATION_MS = 320
        const val JUMP_DISTANCE_SCREENS = 6
    }
}
