package io.github.volovatovta.docviewer.internal.pdf

import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.splineBasedDecay
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private const val PINCH_POINTERS = 2
private const val ZOOMED_IN_THRESHOLD = 1.05f
private const val WHEEL_SCROLL_STEP_PX = 64f
private const val WHEEL_ZOOM_STEP = 1.15f

// Shows the uncommitted pinch or double-tap zoom as a layer transform around the gesture pivot.
internal fun Modifier.pdfGestureZoom(viewport: PdfViewport): Modifier = graphicsLayer {
    scaleX = viewport.gestureZoom
    scaleY = viewport.gestureZoom
    transformOrigin = TransformOrigin(
        pivotFractionX = viewport.gesturePivot.x / viewport.width.coerceAtLeast(1f),
        pivotFractionY = viewport.gesturePivot.y / viewport.height.coerceAtLeast(1f),
    )
}

// One finger pans in both axes with a fling, two fingers pinch-zoom; both are consumed here, not by the list.
internal fun Modifier.pdfPanAndPinch(viewport: PdfViewport, scope: CoroutineScope): Modifier = pointerInput(viewport) {
    val decay = splineBasedDecay<Float>(this)
    awaitEachGesture {
        val session = PanPinchSession(viewport, viewConfiguration.touchSlop, awaitFirstDown(requireUnconsumed = false))
        do {
            val event = awaitPointerEvent()
            val pressed = event.changes.filter { it.pressed }
            if (pressed.isNotEmpty()) session.onEvent(event, pressed)
        } while (pressed.isNotEmpty())
        session.finish(scope, decay)
    }
}

internal fun Modifier.pdfDoubleTapZoom(viewport: PdfViewport, scope: CoroutineScope, zoomedInZoom: Float): Modifier =
    pointerInput(viewport, zoomedInZoom) {
        detectTapGestures(
            onDoubleTap = { position ->
                scope.launch {
                    val target = if (viewport.scale > ZOOMED_IN_THRESHOLD) 1f else zoomedInZoom
                    viewport.animateZoomTo(target, position)
                }
            },
        )
    }

// Mouse wheel scrolls, Ctrl + wheel zooms around the cursor, as on desktops and Chromebooks.
internal fun Modifier.pdfMouseWheel(viewport: PdfViewport): Modifier = pointerInput(viewport) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent()
            if (event.type == PointerEventType.Scroll) onWheel(event, viewport)
        }
    }
}

private fun onWheel(event: PointerEvent, viewport: PdfViewport) {
    val change = event.changes.first()
    val delta = change.scrollDelta
    if (event.keyboardModifiers.isCtrlPressed) {
        val factor = if (delta.y < 0) WHEEL_ZOOM_STEP else 1f / WHEEL_ZOOM_STEP
        viewport.commitZoom(viewport.scale * factor, change.position)
    } else {
        viewport.scrollBy(delta.x * WHEEL_SCROLL_STEP_PX, delta.y * WHEEL_SCROLL_STEP_PX)
    }
    event.changes.forEach { it.consume() }
}

// State of one touch gesture from the first finger down to the last finger up.
private class PanPinchSession(
    private val viewport: PdfViewport,
    private val touchSlop: Float,
    down: PointerInputChange,
) {
    private val velocityTracker = VelocityTracker().apply { addPosition(down.uptimeMillis, down.position) }
    private var pinching = false
    private var dragging = false
    private var travelled = 0f

    init {
        viewport.stopFling()
    }

    fun onEvent(event: PointerEvent, pressed: List<PointerInputChange>) {
        if (pressed.size >= PINCH_POINTERS || pinching) pinch(event, pressed.size) else drag(pressed.first())
    }

    fun finish(scope: CoroutineScope, decay: DecayAnimationSpec<Float>) {
        when {
            pinching -> scope.launch {
                viewport.settleZoom()
                viewport.isInteracting = false
            }
            dragging -> viewport.fling(scope, -velocityTracker.calculateVelocity(), decay)
        }
    }

    // Once a pinch starts it owns the gesture, even after one finger lifts, so the content does not jump.
    private fun pinch(event: PointerEvent, pointers: Int) {
        if (!pinching) {
            pinching = true
            viewport.isInteracting = true
            viewport.startPinch(event.calculateCentroid(useCurrent = true))
        }
        if (pointers >= PINCH_POINTERS) viewport.pinch(event.calculateZoom(), event.calculatePan())
        event.changes.forEach { it.consume() }
    }

    private fun drag(change: PointerInputChange) {
        val delta = change.positionChange()
        if (!dragging) {
            travelled += delta.getDistance()
            dragging = travelled > touchSlop
            if (dragging) viewport.isInteracting = true
        }
        if (dragging) {
            viewport.scrollBy(-delta.x, -delta.y)
            change.consume()
        }
        velocityTracker.addPosition(change.uptimeMillis, change.position)
    }
}
