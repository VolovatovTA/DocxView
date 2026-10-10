package io.github.volovatovta.docviewer.internal.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import io.github.volovatovta.docviewer.DocumentViewerColors

// Proportions are fractions of the glyph size, so the drawing scales with it.
private object GlyphSpec {
    val Width = 52.dp
    val Height = 64.dp
    val StrokeWidth = 2.5.dp
    const val FOLD = 0.32f
    const val TEXT_LINES = 3
    const val FIRST_LINE_Y = 0.45f
    const val LINE_SPACING = 0.14f
    const val LINE_START = 0.25f
    const val LINE_END = 0.75f
    const val LAST_LINE_END = 0.55f
    const val LOCK_CENTER_Y = 0.62f
    val LockBodySize = Size(22f, 16f)
    val LockBodyOffset = Offset(-11f, -6f)
    val LockCorner = 3.dp
    val ShackleSize = Size(14f, 18f)
    val ShackleOffset = Offset(-7f, -15f)
    const val HALF_TURN = 180f
}

// A sheet with a folded corner: lined for errors, with a padlock for protected documents.
@Composable
internal fun DocumentGlyph(colors: DocumentViewerColors, modifier: Modifier = Modifier, locked: Boolean = false) {
    Canvas(modifier.size(width = GlyphSpec.Width, height = GlyphSpec.Height)) {
        drawSheet(colors.secondaryText)
        if (locked) drawPadlock(colors.accent) else drawTextLines(colors.secondaryText)
    }
}

private fun DrawScope.drawSheet(color: Color) {
    val stroke = GlyphSpec.StrokeWidth.toPx()
    val fold = size.width * GlyphSpec.FOLD
    val outline = Path().apply {
        moveTo(stroke, stroke)
        lineTo(size.width - fold, stroke)
        lineTo(size.width - stroke, fold)
        lineTo(size.width - stroke, size.height - stroke)
        lineTo(stroke, size.height - stroke)
        close()
    }
    drawPath(outline, color, style = Stroke(stroke, join = StrokeJoin.Round))
}

private fun DrawScope.drawTextLines(color: Color) {
    repeat(GlyphSpec.TEXT_LINES) { line ->
        val y = size.height * (GlyphSpec.FIRST_LINE_Y + line * GlyphSpec.LINE_SPACING)
        val end = if (line == GlyphSpec.TEXT_LINES - 1) GlyphSpec.LAST_LINE_END else GlyphSpec.LINE_END
        drawLine(
            color = color,
            start = Offset(size.width * GlyphSpec.LINE_START, y),
            end = Offset(size.width * end, y),
            strokeWidth = GlyphSpec.StrokeWidth.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.drawPadlock(color: Color) {
    val center = Offset(size.width / 2, size.height * GlyphSpec.LOCK_CENTER_Y)
    drawRoundRect(
        color = color,
        topLeft = center + dpToPx(GlyphSpec.LockBodyOffset),
        size = dpToPx(GlyphSpec.LockBodySize),
        cornerRadius = CornerRadius(GlyphSpec.LockCorner.toPx()),
    )
    drawArc(
        color = color,
        startAngle = GlyphSpec.HALF_TURN,
        sweepAngle = GlyphSpec.HALF_TURN,
        useCenter = false,
        topLeft = center + dpToPx(GlyphSpec.ShackleOffset),
        size = dpToPx(GlyphSpec.ShackleSize),
        style = Stroke(GlyphSpec.StrokeWidth.toPx()),
    )
}

// Lock geometry is specified in dp so it stays crisp on every density.
private fun DrawScope.dpToPx(offset: Offset) = Offset(offset.x.dp.toPx(), offset.y.dp.toPx())

private fun DrawScope.dpToPx(size: Size) = Size(size.width.dp.toPx(), size.height.dp.toPx())
