package io.github.volovatovta.docviewer.internal.flow

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.volovatovta.docviewer.core.model.ImageRun
import io.github.volovatovta.docviewer.core.model.Paragraph
import io.github.volovatovta.docviewer.core.model.ParagraphStyle
import io.github.volovatovta.docviewer.core.model.TextAlignment
import io.github.volovatovta.docviewer.core.model.TextRun

private object ParagraphSpec {
    const val TITLE_SCALE = 1.9f

    // Text size of heading levels 1, 2, 3 and 4 relative to body text; deeper levels look like body text.
    val HeadingScales = floatArrayOf(1.6f, 1.35f, 1.2f, 1.1f)
    const val BODY_LINE_HEIGHT_EM = 1.5f

    // Runs this much larger than body text need the font's own line height to avoid clipping.
    const val TALL_RUN_SIZE = 1.15f
    const val EMPTY_LINE_FACTOR = 0.9f

    const val HEADING_SPACE_BEFORE_PT = 14f
    const val HEADING_SPACE_AFTER_PT = 6f
    const val BODY_SPACE_AFTER_PT = 8f
    const val MAX_SPACE_BEFORE_PT = 30f
    const val MAX_SPACE_AFTER_PT = 24f
    const val COMPACT_SPACING_FACTOR = 0.3f

    const val MAX_INDENT_PT = 108f
    const val MAX_FIRST_LINE_INDENT_PT = 72f
    const val LIST_MARKER_MIN_WIDTH_PT = 22f
    const val LIST_MARKER_GAP_PT = 6f
}

@Composable
internal fun ParagraphView(
    paragraph: Paragraph,
    context: FlowContext,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    ParagraphPieceView(ParagraphPiece.whole(paragraph), context, modifier, compact)
}

// Renders a whole paragraph or one part of a long one: spacing, marker and first-line indent belong to its ends.
@Composable
internal fun ParagraphPieceView(
    piece: ParagraphPiece,
    context: FlowContext,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    if (piece.source.inlines.isEmpty()) {
        EmptyLine(context, compact, modifier)
        return
    }
    val style = piece.source.style
    val textStyle = paragraphTextStyle(piece, context, isInHeaderRow = LocalInHeaderRow.current)
    val boxModifier = modifier.paragraphBox(style, context.scale, compact, piece)
    val marker = style.listMarker
    if (marker == null) {
        Box(boxModifier) { ParagraphContent(piece, context, textStyle) }
    } else {
        Row(boxModifier) {
            // Later parts of a long list item keep the marker's room so their text stays aligned.
            ListMarker(marker, textStyle, context.scale, Modifier.alpha(if (piece.isFirst) 1f else 0f))
            Box(Modifier.weight(1f)) { ParagraphContent(piece, context, textStyle) }
        }
    }
}

// Empty paragraphs are how Word users make vertical space.
@Composable
private fun EmptyLine(context: FlowContext, compact: Boolean, modifier: Modifier = Modifier) {
    val height = if (compact) 0.dp else (context.textSize.value * ParagraphSpec.EMPTY_LINE_FACTOR).dp
    Spacer(modifier.height(height))
}

@Composable
private fun ListMarker(marker: String, textStyle: TextStyle, scale: Float, modifier: Modifier = Modifier) {
    BasicText(
        text = marker,
        modifier = modifier
            .widthIn(min = ParagraphSpec.LIST_MARKER_MIN_WIDTH_PT.ptToDp(scale))
            .padding(end = ParagraphSpec.LIST_MARKER_GAP_PT.ptToDp(scale)),
        style = textStyle.copy(textIndent = null, textAlign = TextAlign.End),
    )
}

@Composable
private fun ParagraphContent(piece: ParagraphPiece, context: FlowContext, textStyle: TextStyle) {
    val matches = context.search.matchesIn(piece.source)
    if (piece.inlines.none { it is ImageRun }) {
        val text = remember(piece, matches, context.search.current, context.colors) {
            paragraphText(piece.inlines.filterIsInstance<TextRun>(), piece.textOffset, matches, context)
        }
        BasicText(text, style = textStyle)
    } else {
        val segments = remember(piece) { piece.segments() }
        Column(Modifier.fillMaxWidth()) {
            for (segment in segments) {
                when (segment) {
                    is ParagraphSegment.Image -> FlowImage(segment.run, context, textStyle.textAlign)
                    is ParagraphSegment.Text -> if (segment.isVisible) {
                        BasicText(paragraphText(segment.runs, segment.offset, matches, context), style = textStyle)
                    }
                }
            }
        }
    }
}

private sealed interface ParagraphSegment {
    // offset is where the runs start in Paragraph.plainText, so search highlights line up.
    class Text(val runs: List<TextRun>, val offset: Int) : ParagraphSegment {
        val isVisible: Boolean get() = runs.any { it.text.isNotBlank() }
    }

    class Image(val run: ImageRun) : ParagraphSegment
}

private fun ParagraphPiece.segments(): List<ParagraphSegment> {
    val segments = ArrayList<ParagraphSegment>()
    var pending = ArrayList<TextRun>()
    var offset = textOffset
    fun flushText() {
        if (pending.isEmpty()) return
        segments += ParagraphSegment.Text(pending, offset)
        offset += pending.sumOf { it.text.length }
        pending = ArrayList()
    }
    for (inline in inlines) {
        when (inline) {
            is TextRun -> pending += inline
            is ImageRun -> {
                flushText()
                segments += ParagraphSegment.Image(inline)
            }
        }
    }
    flushText()
    return segments
}

private fun paragraphTextStyle(piece: ParagraphPiece, context: FlowContext, isInHeaderRow: Boolean): TextStyle {
    val paragraph = piece.source
    val style = paragraph.style
    val isHeading = style.headingLevel > 0
    val runSizes = paragraph.inlines.mapNotNull { (it as? TextRun)?.style?.relativeSize }
    // Explicit run sizes already carry the heading size, so only unsized headings get the default scale.
    val fontSize = if (runSizes.isEmpty()) context.textSize * headingScale(style.headingLevel) else context.textSize
    val hasTallRuns = (runSizes.maxOrNull() ?: 1f) > ParagraphSpec.TALL_RUN_SIZE
    return TextStyle(
        color = context.colors.text,
        fontSize = fontSize,
        fontWeight = if (isHeading || isInHeaderRow) FontWeight.SemiBold else null,
        textAlign = style.alignment.toTextAlign(),
        lineHeight = if (hasTallRuns || isHeading) TextUnit.Unspecified else ParagraphSpec.BODY_LINE_HEIGHT_EM.em,
        textIndent = if (piece.isFirst) firstLineIndent(style, context.scale) else null,
    )
}

private fun headingScale(level: Int): Float = when (level) {
    ParagraphStyle.TITLE_LEVEL -> ParagraphSpec.TITLE_SCALE
    else -> ParagraphSpec.HeadingScales.getOrElse(level - 1) { 1f }
}

private fun TextAlignment.toTextAlign(): TextAlign = when (this) {
    TextAlignment.Start -> TextAlign.Start
    TextAlignment.Center -> TextAlign.Center
    TextAlignment.End -> TextAlign.End
    TextAlignment.Justify -> TextAlign.Justify
}

private fun firstLineIndent(style: ParagraphStyle, scale: Float): TextIndent? {
    if (style.firstLineIndentPt == 0f || style.listMarker != null) return null
    val limit = ParagraphSpec.MAX_FIRST_LINE_INDENT_PT
    return TextIndent(firstLine = (style.firstLineIndentPt.coerceIn(-limit, limit) * scale).sp)
}

private fun Modifier.paragraphBox(
    style: ParagraphStyle,
    scale: Float,
    compact: Boolean,
    piece: ParagraphPiece,
): Modifier {
    val isHeading = style.headingLevel > 0
    val spacingFactor = if (compact) ParagraphSpec.COMPACT_SPACING_FACTOR else 1f
    val defaultBefore = if (isHeading) ParagraphSpec.HEADING_SPACE_BEFORE_PT else 0f
    val defaultAfter = when {
        isHeading -> ParagraphSpec.HEADING_SPACE_AFTER_PT
        else -> ParagraphSpec.BODY_SPACE_AFTER_PT
    }
    val before = (style.spaceBeforePt ?: defaultBefore).coerceIn(0f, ParagraphSpec.MAX_SPACE_BEFORE_PT)
    val after = (style.spaceAfterPt ?: defaultAfter).coerceIn(0f, ParagraphSpec.MAX_SPACE_AFTER_PT)
    return fillMaxWidth()
        .padding(
            start = style.indentStartPt.coerceIn(0f, ParagraphSpec.MAX_INDENT_PT).ptToDp(scale),
            top = if (piece.isFirst) (before * spacingFactor).ptToDp(scale) else 0.dp,
            bottom = if (piece.isLast) (after * spacingFactor).ptToDp(scale) else 0.dp,
        )
        .then(style.background?.let { Modifier.background(Color(it)) } ?: Modifier)
        .then(if (isHeading) Modifier.semantics { heading() } else Modifier)
}

// Document points map one-to-one onto dp before the user's pinch scale.
private fun Float.ptToDp(scale: Float): Dp = (this * scale).dp
