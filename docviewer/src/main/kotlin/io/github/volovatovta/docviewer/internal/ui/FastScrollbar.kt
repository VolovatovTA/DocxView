package io.github.volovatovta.docviewer.internal.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.volovatovta.docviewer.DocumentViewerColors
import kotlin.math.roundToInt

private object ScrollbarSpec {
    const val HIDE_DELAY_MILLIS = 1500L
    const val FULLY_VISIBLE = 0.98f
    const val MAX_THUMB_SHARE = 0.5f
    val MinThumbHeight = 44.dp
    val TouchWidth = 36.dp
    val WidthWithLabel = 160.dp
    val ThumbWidth = 5.dp
    val DraggedThumbWidth = 8.dp
    val ThumbEndPadding = 4.dp
    val LabelEndOffset = 44.dp
    val LabelHalfHeight = 18.dp
    val LabelHorizontalPadding = 14.dp
    val LabelVerticalPadding = 8.dp
    val LabelTextSize = 14.sp
}

// Pixel geometry of the thumb on its track for one layout pass.
private class ThumbGeometry(val trackPx: Float, val thumbPx: Float) {
    fun top(position: Float): Int = ((trackPx - thumbPx) * position).roundToInt()

    // The finger holds the thumb by its center.
    fun fractionAt(y: Float): Float {
        val range = trackPx - thumbPx
        return if (range <= 0f) 0f else ((y - thumbPx / 2) / range).coerceIn(0f, 1f)
    }
}

// Draggable thumb on the end edge; only composed while visible so it never steals touches from content.
@Composable
internal fun FastScrollbar(
    isScrolling: Boolean,
    fraction: () -> Float,
    visibleFraction: () -> Float,
    onScrollTo: (Float) -> Unit,
    colors: DocumentViewerColors,
    modifier: Modifier = Modifier,
    label: ((Float) -> String)? = null,
) {
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val visible = rememberTransientVisibility(isScrolling || dragFraction != null, ScrollbarSpec.HIDE_DELAY_MILLIS)
    if (visibleFraction() >= ScrollbarSpec.FULLY_VISIBLE) return
    AnimatedVisibility(visible, modifier.fillMaxHeight(), enter = OverlayFade.Enter, exit = OverlayFade.Exit) {
        val width = if (label != null) ScrollbarSpec.WidthWithLabel else ScrollbarSpec.TouchWidth
        BoxWithConstraints(Modifier.fillMaxHeight().width(width)) {
            val geometry = thumbGeometry(maxHeight, visibleFraction())
            val dragged = dragFraction
            val position = dragged ?: fraction().coerceIn(0f, 1f)
            ThumbTrack(
                geometry = geometry,
                position = position,
                dragging = dragged != null,
                colors = colors,
                onDrag = { target ->
                    dragFraction = target
                    target?.let(onScrollTo)
                },
                modifier = Modifier.align(Alignment.TopEnd),
            )
            if (dragged != null && label != null) {
                ThumbLabel(label(position), geometry, position, colors, Modifier.align(Alignment.TopEnd))
            }
        }
    }
}

@Composable
private fun thumbGeometry(trackHeight: Dp, visibleFraction: Float): ThumbGeometry {
    val density = LocalDensity.current
    val trackPx = with(density) { trackHeight.toPx() }
    val maxThumb = trackPx * ScrollbarSpec.MAX_THUMB_SHARE
    val minThumb = minOf(with(density) { ScrollbarSpec.MinThumbHeight.toPx() }, maxThumb)
    return ThumbGeometry(trackPx, (trackPx * visibleFraction).coerceIn(minThumb, maxThumb))
}

// onDrag receives the target fraction while dragging and null when the finger lifts.
@Composable
private fun ThumbTrack(
    geometry: ThumbGeometry,
    position: Float,
    dragging: Boolean,
    colors: DocumentViewerColors,
    onDrag: (Float?) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Geometry changes mid-drag in lazy lists, so it is read through updated state instead of restarting the gesture.
    val currentGeometry by rememberUpdatedState(geometry)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val thumbWidth by animateDpAsState(
        targetValue = if (dragging) ScrollbarSpec.DraggedThumbWidth else ScrollbarSpec.ThumbWidth,
        label = "thumbWidth",
    )
    Box(
        modifier
            .width(ScrollbarSpec.TouchWidth)
            .fillMaxHeight()
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { start -> currentOnDrag(currentGeometry.fractionAt(start.y)) },
                    onDragEnd = { currentOnDrag(null) },
                    onDragCancel = { currentOnDrag(null) },
                ) { change, _ ->
                    change.consume()
                    currentOnDrag(currentGeometry.fractionAt(change.position.y))
                }
            },
    ) {
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset { IntOffset(0, geometry.top(position)) }
                .padding(end = ScrollbarSpec.ThumbEndPadding)
                .width(thumbWidth)
                .height(with(LocalDensity.current) { geometry.thumbPx.toDp() })
                .background(if (dragging) colors.accent else colors.scrollbar, PillShape),
        )
    }
}

// Bubble next to the thumb, e.g. the page the thumb points at.
@Composable
private fun ThumbLabel(
    text: String,
    geometry: ThumbGeometry,
    position: Float,
    colors: DocumentViewerColors,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .offset {
                val centerY = geometry.top(position) + geometry.thumbPx / 2
                IntOffset(
                    x = -ScrollbarSpec.LabelEndOffset.roundToPx(),
                    y = (centerY - ScrollbarSpec.LabelHalfHeight.toPx()).roundToInt(),
                )
            }
            .background(colors.accent, PillShape)
            .padding(horizontal = ScrollbarSpec.LabelHorizontalPadding, vertical = ScrollbarSpec.LabelVerticalPadding),
    ) {
        BasicText(
            text = text,
            style = TextStyle(
                color = colors.onAccent,
                fontSize = ScrollbarSpec.LabelTextSize,
                fontWeight = FontWeight.SemiBold,
            ),
        )
    }
}
