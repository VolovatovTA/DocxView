package io.github.volovatovta.docviewer.internal.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.volovatovta.docviewer.DocumentViewerColors
import io.github.volovatovta.docviewer.R
import io.github.volovatovta.docviewer.core.CorruptedDocumentException
import io.github.volovatovta.docviewer.core.UnsupportedFormatException
import io.github.volovatovta.docviewer.internal.NetworkException
import io.github.volovatovta.docviewer.internal.PasswordUnsupportedException
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
internal fun LoadingView(progress: Float?, colors: DocumentViewerColors) {
    Column(
        Modifier.fillMaxSize().background(colors.background),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val transition = rememberInfiniteTransition(label = "spinner")
        val rotation by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart), label = "rotation")
        val sweep by transition.animateFloat(40f, 260f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "sweep")
        val shownProgress by animateFloatAsState(progress ?: 0f, label = "progress")
        Canvas(Modifier.size(40.dp)) {
            val stroke = 3.5.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(colors.accent.copy(alpha = 0.18f), 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            if (progress != null) {
                drawArc(colors.accent, -90f, 360f * shownProgress, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            } else {
                drawArc(colors.accent, rotation, sweep, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
        Spacer(Modifier.height(16.dp))
        val label = stringResource(R.string.dv_loading) + if (progress != null) "  ${(progress * 100).roundToInt()}%" else ""
        BasicText(label, style = TextStyle(color = colors.secondaryText, fontSize = 14.sp))
    }
}

@Composable
internal fun ErrorView(error: Throwable, onRetry: () -> Unit, colors: DocumentViewerColors) {
    val message = when (error) {
        is PasswordUnsupportedException -> stringResource(if (error.isPdf) R.string.dv_error_pdf_password_old_android else R.string.dv_error_doc_password)
        is UnsupportedFormatException -> stringResource(R.string.dv_error_unsupported)
        is CorruptedDocumentException -> stringResource(R.string.dv_error_corrupted)
        is NetworkException -> stringResource(R.string.dv_error_network)
        else -> stringResource(R.string.dv_error_io)
    }
    val retryable = error !is UnsupportedFormatException && error !is CorruptedDocumentException
    Column(
        Modifier.fillMaxSize().background(colors.background).padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DocumentGlyph(colors)
        Spacer(Modifier.height(20.dp))
        BasicText(
            stringResource(R.string.dv_error_title),
            style = TextStyle(color = colors.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center),
        )
        Spacer(Modifier.height(8.dp))
        BasicText(message, Modifier.widthIn(max = 360.dp), style = TextStyle(color = colors.secondaryText, fontSize = 14.sp, textAlign = TextAlign.Center))
        if (retryable) {
            Spacer(Modifier.height(24.dp))
            PillButton(stringResource(R.string.dv_retry), colors, onClick = onRetry)
        }
    }
}

@Composable
internal fun PasswordView(wrongPassword: Boolean, onSubmit: (String) -> Unit, colors: DocumentViewerColors) {
    var password by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Column(
        Modifier.fillMaxSize().background(colors.background).padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DocumentGlyph(colors, locked = true)
        Spacer(Modifier.height(20.dp))
        BasicText(stringResource(R.string.dv_password_title), style = TextStyle(color = colors.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold))
        Spacer(Modifier.height(6.dp))
        BasicText(
            stringResource(if (wrongPassword) R.string.dv_password_wrong else R.string.dv_password_subtitle),
            style = TextStyle(color = if (wrongPassword) androidx.compose.ui.graphics.Color(0xFFE5484D) else colors.secondaryText, fontSize = 14.sp, textAlign = TextAlign.Center),
        )
        Spacer(Modifier.height(20.dp))
        BasicTextField(
            value = password,
            onValueChange = { password = it },
            singleLine = true,
            textStyle = TextStyle(color = colors.text, fontSize = 16.sp),
            cursorBrush = SolidColor(colors.accent),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (password.isNotEmpty()) onSubmit(password) }),
            modifier = Modifier.focusRequester(focus).widthIn(max = 320.dp).fillMaxWidth(),
            decorationBox = { inner ->
                Box(
                    Modifier.fillMaxWidth().background(colors.page, RoundedCornerShape(12.dp))
                        .border(1.dp, if (wrongPassword) androidx.compose.ui.graphics.Color(0xFFE5484D) else colors.tableBorder, RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    if (password.isEmpty()) BasicText(stringResource(R.string.dv_password_hint), style = TextStyle(color = colors.secondaryText, fontSize = 16.sp))
                    inner()
                }
            },
        )
        Spacer(Modifier.height(16.dp))
        PillButton(stringResource(R.string.dv_password_open), colors, enabled = password.isNotEmpty()) { onSubmit(password) }
    }
}

@Composable
internal fun PillButton(text: String, colors: DocumentViewerColors, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.alpha(if (enabled) 1f else 0.4f)
            .background(colors.accent, RoundedCornerShape(50))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 28.dp, vertical = 12.dp),
    ) {
        BasicText(text, style = TextStyle(color = colors.onAccent, fontSize = 15.sp, fontWeight = FontWeight.Medium))
    }
}

@Composable
private fun DocumentGlyph(colors: DocumentViewerColors, locked: Boolean = false) {
    Canvas(Modifier.size(width = 52.dp, height = 64.dp)) {
        val stroke = 2.5.dp.toPx()
        val fold = size.width * 0.32f
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(stroke, stroke)
            lineTo(size.width - fold, stroke)
            lineTo(size.width - stroke, fold)
            lineTo(size.width - stroke, size.height - stroke)
            lineTo(stroke, size.height - stroke)
            close()
        }
        drawPath(path, colors.secondaryText, style = Stroke(stroke, join = androidx.compose.ui.graphics.StrokeJoin.Round))
        if (locked) {
            val cx = size.width / 2
            val cy = size.height * 0.62f
            drawRoundRect(colors.accent, Offset(cx - 11.dp.toPx(), cy - 6.dp.toPx()), Size(22.dp.toPx(), 16.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()))
            drawArc(colors.accent, 180f, 180f, false, Offset(cx - 7.dp.toPx(), cy - 15.dp.toPx()), Size(14.dp.toPx(), 18.dp.toPx()), style = Stroke(2.5.dp.toPx()))
        } else {
            for (i in 0 until 3) {
                val y = size.height * (0.45f + i * 0.14f)
                drawLine(colors.secondaryText, Offset(size.width * 0.25f, y), Offset(size.width * (if (i == 2) 0.55f else 0.75f), y), stroke, StrokeCap.Round)
            }
        }
    }
}

// Pill shown while scrolling, e.g. "12 / 300".
@Composable
internal fun ScrollIndicator(text: String, visibleKey: Any, isScrolling: Boolean, colors: DocumentViewerColors, modifier: Modifier = Modifier) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(visibleKey, isScrolling) {
        if (isScrolling) {
            visible = true
        } else if (visible) {
            delay(1400)
            visible = false
        }
    }
    AnimatedVisibility(visible, modifier, enter = fadeIn(tween(120)), exit = fadeOut(tween(400))) {
        Box(Modifier.background(colors.overlay, RoundedCornerShape(50)).padding(horizontal = 14.dp, vertical = 6.dp)) {
            BasicText(text, style = TextStyle(color = colors.onOverlay, fontSize = 13.sp, fontWeight = FontWeight.Medium))
        }
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
    var visible by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(isScrolling, dragging) {
        if (isScrolling || dragging) {
            visible = true
        } else if (visible) {
            delay(1500)
            visible = false
        }
    }
    if (visibleFraction() >= 0.98f) return
    AnimatedVisibility(visible, modifier.fillMaxHeight(), enter = fadeIn(tween(120)), exit = fadeOut(tween(400))) {
        BoxWithConstraints(Modifier.fillMaxHeight().width(if (label != null) 160.dp else 36.dp)) {
            val density = LocalDensity.current
            val trackPx = with(density) { maxHeight.toPx() }
            val thumbPx = (trackPx * visibleFraction()).coerceIn(with(density) { 44.dp.toPx() }, trackPx * 0.5f)
            val position = if (dragging) dragFraction else fraction().coerceIn(0f, 1f)
            val thumbTop = ((trackPx - thumbPx) * position).roundToInt()
            val thumbWidth by animateFloatAsState(if (dragging) 8f else 5f, label = "thumbWidth")
            Box(
                Modifier.align(Alignment.TopEnd).width(36.dp).fillMaxHeight().pointerInput(trackPx, thumbPx) {
                    fun toFraction(y: Float) = ((y - thumbPx / 2) / (trackPx - thumbPx)).coerceIn(0f, 1f)
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            dragging = true
                            dragFraction = toFraction(offset.y)
                            onScrollTo(dragFraction)
                        },
                        onDragEnd = { dragging = false },
                        onDragCancel = { dragging = false },
                    ) { change, _ ->
                        change.consume()
                        dragFraction = toFraction(change.position.y)
                        onScrollTo(dragFraction)
                    }
                },
            ) {
                Box(
                    Modifier.align(Alignment.TopEnd)
                        .offset { IntOffset(0, thumbTop) }
                        .padding(end = 4.dp)
                        .width(thumbWidth.dp)
                        .height(with(density) { thumbPx.toDp() })
                        .background(if (dragging) colors.accent else colors.scrollbar, RoundedCornerShape(50)),
                )
            }
            if (dragging && label != null) {
                Box(
                    Modifier.align(Alignment.TopEnd)
                        .offset { IntOffset(-(44.dp.roundToPx()), (thumbTop + thumbPx / 2 - 18.dp.toPx()).roundToInt()) }
                        .background(colors.accent, RoundedCornerShape(50))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    BasicText(label(position), style = TextStyle(color = colors.onAccent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold))
                }
            }
        }
    }
}

@Composable
internal fun Banner(text: String, colors: DocumentViewerColors, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().background(colors.overlay).padding(horizontal = 16.dp, vertical = 10.dp)) {
        BasicText(text, style = TextStyle(color = colors.onOverlay, fontSize = 13.sp))
    }
}
