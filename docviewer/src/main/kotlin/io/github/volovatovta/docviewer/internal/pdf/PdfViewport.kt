package io.github.volovatovta.docviewer.internal.pdf

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

// Zoom is laid out for real (pages stay virtualized); a pinch only scales the layer until the zoom is committed.
@Stable
internal class PdfViewport(
    val layout: PdfLayout,
    val listState: LazyListState,
    val width: Float,
    height: Float,
    val maxZoom: Float,
) {
    // Height changes with the keyboard or split-screen; it must not reset zoom or position.
    var height by mutableFloatStateOf(height)

    var scale by mutableFloatStateOf(1f)
        private set

    var horizontalOffset by mutableFloatStateOf(0f)

    var gestureZoom by mutableFloatStateOf(1f)
        private set

    var gesturePivot by mutableStateOf(Offset.Zero)
        private set

    var isInteracting by mutableStateOf(false)

    private var flingJob: Job? = null

    val maxHorizontalOffset: Float get() = (width * scale - width).coerceAtLeast(0f)

    val contentHeight: Float get() = layout.fitContentHeight * scale

    val maxScrollY: Float get() = (contentHeight - height).coerceAtLeast(0f)

    val scrollY: Float
        get() = layout.top(listState.firstVisibleItemIndex, scale) + listState.firstVisibleItemScrollOffset.toFloat()

    val pageAtCenter: Int get() = layout.pageAt((scrollY + height / 2) / scale).coerceIn(0, layout.pageCount - 1)

    val pageLeft: Float get() = layout.spacing * scale - horizontalOffset

    val isSettled: Boolean get() = gestureZoom == 1f && !isInteracting

    val isScrolling: Boolean get() = isInteracting || listState.isScrollInProgress

    fun scrollBy(dx: Float, dy: Float) {
        horizontalOffset = (horizontalOffset + dx).coerceIn(0f, maxHorizontalOffset)
        if (dy != 0f) listState.dispatchRawDelta(dy)
    }

    fun scrollTo(y: Float) {
        val clamped = y.coerceIn(0f, maxScrollY)
        val page = layout.pageAt(clamped / scale)
        listState.requestScrollToItem(page, (clamped - layout.top(page, scale)).roundToInt())
    }

    fun commitZoom(target: Float, focus: Offset) {
        val newScale = target.coerceIn(1f, maxZoom)
        val factor = newScale / scale
        val y = (scrollY + focus.y) * factor - focus.y
        val x = (horizontalOffset + focus.x) * factor - focus.x
        scale = newScale
        gestureZoom = 1f
        horizontalOffset = x.coerceIn(0f, maxHorizontalOffset)
        scrollTo(y)
    }

    fun startPinch(centroid: Offset) {
        stopFling()
        gesturePivot = centroid
        gestureZoom = 1f
    }

    fun pinch(zoomChange: Float, pan: Offset) {
        gestureZoom = (gestureZoom * zoomChange).coerceIn(PINCH_UNDERSHOOT / scale, maxZoom * PINCH_OVERSHOOT / scale)
        scrollBy(-pan.x / gestureZoom, -pan.y / gestureZoom)
    }

    suspend fun settleZoom() {
        val target = (scale * gestureZoom).coerceIn(1f, maxZoom)
        if (abs(target - scale * gestureZoom) > SCALE_EPSILON) {
            animate(gestureZoom, target / scale, animationSpec = tween(SETTLE_DURATION_MS)) { value, _ ->
                gestureZoom = value
            }
        }
        commitZoom(target, gesturePivot)
    }

    suspend fun animateZoomTo(target: Float, focus: Offset) {
        val clamped = target.coerceIn(1f, maxZoom)
        if (abs(clamped - scale) < SCALE_EPSILON) return
        stopFling()
        // When zooming out to fit-width, pick the pivot that lands exactly where the commit will clamp.
        gesturePivot = if (clamped == 1f && scale > 1f) Offset(horizontalOffset / (scale - 1f), focus.y) else focus
        isInteracting = true
        try {
            animate(1f, clamped / scale, animationSpec = tween(ZOOM_DURATION_MS)) { value, _ -> gestureZoom = value }
        } finally {
            // Commit wherever the animation stopped, otherwise a cancelled zoom leaves the transient transform behind.
            commitZoom(scale * gestureZoom, gesturePivot)
            isInteracting = false
        }
    }

    fun fling(scope: CoroutineScope, velocity: Velocity, decay: DecayAnimationSpec<Float>) {
        flingJob = scope.launch {
            try {
                coroutineScope {
                    launch { flingVertically(velocity.y, decay) }
                    launch { flingHorizontally(velocity.x, decay) }
                }
            } finally {
                isInteracting = false
            }
        }
    }

    fun stopFling() {
        flingJob?.cancel()
        flingJob = null
    }

    private suspend fun flingVertically(velocity: Float, decay: DecayAnimationSpec<Float>) {
        if (abs(velocity) < MIN_FLING_VELOCITY) return
        listState.scroll {
            var previous = 0f
            AnimationState(0f, velocity).animateDecay(decay) {
                val delta = value - previous
                previous = value
                if (abs(delta - scrollBy(delta)) > FLING_STOP_TOLERANCE) cancelAnimation()
            }
        }
    }

    private suspend fun flingHorizontally(velocity: Float, decay: DecayAnimationSpec<Float>) {
        if (abs(velocity) < MIN_FLING_VELOCITY || maxHorizontalOffset <= 0f) return
        var previous = 0f
        AnimationState(0f, velocity).animateDecay(decay) {
            val before = horizontalOffset
            horizontalOffset = (horizontalOffset + value - previous).coerceIn(0f, maxHorizontalOffset)
            previous = value
            if (horizontalOffset == before) cancelAnimation()
        }
    }

    private companion object {
        const val MIN_FLING_VELOCITY = 50f
        const val FLING_STOP_TOLERANCE = 0.5f
        const val SCALE_EPSILON = 0.001f
        const val PINCH_UNDERSHOOT = 0.5f
        const val PINCH_OVERSHOOT = 1.5f
        const val SETTLE_DURATION_MS = 180
        const val ZOOM_DURATION_MS = 260
    }
}
