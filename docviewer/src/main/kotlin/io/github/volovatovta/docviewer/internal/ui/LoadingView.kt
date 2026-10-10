package io.github.volovatovta.docviewer.internal.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.volovatovta.docviewer.DocumentViewerColors
import io.github.volovatovta.docviewer.R
import kotlin.math.roundToInt

private object LoadingSpec {
    val IndicatorSize = 40.dp
    val StrokeWidth = 3.5.dp
    val LabelGap = 16.dp
    val LabelSize = 14.sp
    const val TRACK_ALPHA = 0.18f
    const val FULL_TURN = 360f
    const val TOP_ANGLE = -90f
    const val SPIN_MILLIS = 1100
    const val SWEEP_MILLIS = 900
    const val SWEEP_MIN = 40f
    const val SWEEP_MAX = 260f
    const val PERCENT = 100
}

// progress is 0..1 for downloads and copies with a known size, null while it cannot be measured.
@Composable
internal fun LoadingView(progress: Float?, colors: DocumentViewerColors, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().background(colors.background),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ProgressRing(progress, colors.accent)
        Spacer(Modifier.height(LoadingSpec.LabelGap))
        BasicText(
            text = loadingLabel(progress),
            style = TextStyle(color = colors.secondaryText, fontSize = LoadingSpec.LabelSize),
        )
    }
}

@Composable
private fun loadingLabel(progress: Float?): String {
    val label = stringResource(R.string.dv_loading)
    return if (progress == null) label else "$label  ${(progress * LoadingSpec.PERCENT).roundToInt()}%"
}

@Composable
private fun ProgressRing(progress: Float?, color: Color) {
    val transition = rememberInfiniteTransition(label = "spinner")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = LoadingSpec.FULL_TURN,
        animationSpec = infiniteRepeatable(tween(LoadingSpec.SPIN_MILLIS, easing = LinearEasing), RepeatMode.Restart),
        label = "rotation",
    )
    val sweep by transition.animateFloat(
        initialValue = LoadingSpec.SWEEP_MIN,
        targetValue = LoadingSpec.SWEEP_MAX,
        animationSpec = infiniteRepeatable(tween(LoadingSpec.SWEEP_MILLIS), RepeatMode.Reverse),
        label = "sweep",
    )
    val shownProgress by animateFloatAsState(progress ?: 0f, label = "progress")
    Canvas(Modifier.size(LoadingSpec.IndicatorSize)) {
        drawRing(color.copy(alpha = LoadingSpec.TRACK_ALPHA), 0f, LoadingSpec.FULL_TURN, StrokeCap.Butt)
        if (progress == null) {
            drawRing(color, rotation, sweep, StrokeCap.Round)
        } else {
            drawRing(color, LoadingSpec.TOP_ANGLE, LoadingSpec.FULL_TURN * shownProgress, StrokeCap.Round)
        }
    }
}

private fun DrawScope.drawRing(color: Color, startAngle: Float, sweepAngle: Float, cap: StrokeCap) {
    val stroke = LoadingSpec.StrokeWidth.toPx()
    val inset = stroke / 2
    drawArc(
        color = color,
        startAngle = startAngle,
        sweepAngle = sweepAngle,
        useCenter = false,
        topLeft = Offset(inset, inset),
        size = Size(size.width - stroke, size.height - stroke),
        style = Stroke(stroke, cap = cap),
    )
}
