package io.github.volovatovta.docviewer.internal.flow

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import io.github.volovatovta.docviewer.DocumentViewerColors
import io.github.volovatovta.docviewer.core.FlowMatch
import io.github.volovatovta.docviewer.core.model.RunStyle
import io.github.volovatovta.docviewer.core.model.TextRun
import io.github.volovatovta.docviewer.core.model.VerticalAlign

private object RunStyleSpec {
    const val MIN_RELATIVE_SIZE = 0.5f
    const val MAX_RELATIVE_SIZE = 4f
    const val SHIFTED_SIZE_FACTOR = 0.72f
    const val SMALL_CAPS_FEATURE = "smcp"
}

private object Contrast {
    // WCAG contrast below which a document color is treated as invisible on the page.
    const val MIN_RATIO = 2.2f
    const val LUMINANCE_OFFSET = 0.05f
    const val LIGHT_BACKGROUND_LUMINANCE = 0.45f
    val DarkInk = Color(0xFF111111)
}

// offset is where these runs start in the paragraph's plain text; matches use those coordinates.
internal fun paragraphText(
    runs: List<TextRun>,
    offset: Int,
    matches: List<FlowMatch>?,
    context: FlowContext,
): AnnotatedString = buildAnnotatedString {
    runs.forEach { run -> appendRun(run, context) }
    matches?.let { highlightMatches(it, offset, context) }
}

private fun AnnotatedString.Builder.appendRun(run: TextRun, context: FlowContext) {
    val start = length
    val link = run.link
    if (link == null) {
        append(run.text)
    } else {
        val linkStyle = TextLinkStyles(
            SpanStyle(color = context.colors.link, textDecoration = TextDecoration.Underline),
        )
        withLink(LinkAnnotation.Clickable(link, linkStyle) { context.onLink(link) }) { append(run.text) }
    }
    addStyle(run.style.toSpanStyle(context.colors, isLink = link != null), start, length)
}

private fun AnnotatedString.Builder.highlightMatches(matches: List<FlowMatch>, offset: Int, context: FlowContext) {
    val end = offset + length
    val colors = context.colors
    matches.filter { it.end > offset && it.start < end }.forEach { match ->
        val color = if (match === context.search.current) colors.currentSearchMatch else colors.searchMatch
        addStyle(
            SpanStyle(background = color),
            (match.start - offset).coerceAtLeast(0),
            (match.end - offset).coerceAtMost(length),
        )
    }
}

private fun RunStyle.toSpanStyle(colors: DocumentViewerColors, isLink: Boolean): SpanStyle {
    if (this == RunStyle.Default) return SpanStyle()
    val backgroundColor = background?.let(::Color)
    return SpanStyle(
        color = if (isLink) Color.Unspecified else readableColor(color, backgroundColor, colors),
        fontSize = relativeFontSize(),
        fontWeight = FontWeight.Bold.takeIf { bold },
        fontStyle = FontStyle.Italic.takeIf { italic },
        fontFamily = FontFamily.Monospace.takeIf { monospace },
        fontFeatureSettings = RunStyleSpec.SMALL_CAPS_FEATURE.takeIf { smallCaps },
        baselineShift = verticalAlign.toBaselineShift(),
        textDecoration = decoration(isLink),
        background = backgroundColor ?: Color.Unspecified,
    )
}

private fun RunStyle.relativeFontSize(): TextUnit {
    val size = relativeSize?.coerceIn(RunStyleSpec.MIN_RELATIVE_SIZE, RunStyleSpec.MAX_RELATIVE_SIZE)
    return when {
        verticalAlign != VerticalAlign.Baseline -> ((size ?: 1f) * RunStyleSpec.SHIFTED_SIZE_FACTOR).em
        size != null -> size.em
        else -> TextUnit.Unspecified
    }
}

private fun VerticalAlign.toBaselineShift(): BaselineShift? = when (this) {
    VerticalAlign.Superscript -> BaselineShift.Superscript
    VerticalAlign.Subscript -> BaselineShift.Subscript
    VerticalAlign.Baseline -> null
}

// Links draw their own underline, so the run's underline would double it.
private fun RunStyle.decoration(isLink: Boolean): TextDecoration? {
    val decorations = buildList {
        if (underline && !isLink) add(TextDecoration.Underline)
        if (strikethrough) add(TextDecoration.LineThrough)
    }
    return if (decorations.isEmpty()) null else TextDecoration.combine(decorations)
}

// Keeps document colors unless they would vanish on the current page color (e.g. black text in dark mode).
private fun readableColor(argb: Int?, background: Color?, colors: DocumentViewerColors): Color {
    val fallback = background?.let(::inkOn) ?: Color.Unspecified
    if (argb == null) return fallback
    val color = Color(argb)
    return if (contrastRatio(color, background ?: colors.page) >= Contrast.MIN_RATIO) color else fallback
}

private fun inkOn(background: Color): Color =
    if (background.luminance() > Contrast.LIGHT_BACKGROUND_LUMINANCE) Contrast.DarkInk else Color.White

private fun contrastRatio(first: Color, second: Color): Float {
    val a = first.luminance() + Contrast.LUMINANCE_OFFSET
    val b = second.luminance() + Contrast.LUMINANCE_OFFSET
    return maxOf(a, b) / minOf(a, b)
}
