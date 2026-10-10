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
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.volovatovta.docviewer.core.model.BorderLine
import io.github.volovatovta.docviewer.core.model.ImageRun
import io.github.volovatovta.docviewer.core.model.Inline
import io.github.volovatovta.docviewer.core.model.Paragraph
import io.github.volovatovta.docviewer.core.model.ParagraphBorders
import io.github.volovatovta.docviewer.core.model.ParagraphStyle
import io.github.volovatovta.docviewer.core.model.TextAlignment
import io.github.volovatovta.docviewer.core.model.TextRun

private object ParagraphSpec {
    const val TITLE_SCALE = 1.9f

    // Text size of heading levels 1, 2, 3 and 4 relative to body text; deeper levels look like body text.
    val HeadingScales = floatArrayOf(1.6f, 1.35f, 1.2f, 1.1f)
    const val BODY_LINE_HEIGHT_EM = 1.5f

    // Word's single spacing is about 1.2 font sizes; documents give their spacing as a multiple of it.
    const val SINGLE_LINE_HEIGHT_EM = 1.2f
    const val MIN_LINE_SPACING = 0.8f
    const val MAX_LINE_SPACING = 3f

    // Images up to about two lines tall sit inside the text, like Word's inline pictures.
    const val MAX_INLINE_IMAGE_PT = 28f
    const val INLINE_IMAGE_SCALE = 1.25f
    const val BORDER_GAP_PT = 4f

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
    val surface = LocalSurfaceColor.current
    val textStyle = paragraphTextStyle(piece, context, isInHeaderRow = LocalInHeaderRow.current, surface)
    val borderColor = surface?.let(::inkOn) ?: context.colors.text
    val boxModifier = modifier.paragraphBox(style, context.scale, compact, piece, borderColor)
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
    val surface = LocalSurfaceColor.current
    val segments = remember(piece) { piece.segments() }
    Column(Modifier.fillMaxWidth()) {
        for (segment in segments) {
            when (segment) {
                is ParagraphSegment.Image -> {
                    val alignment = segment.run.floatAlignment?.toTextAlign() ?: textStyle.textAlign
                    FlowImage(segment.run, context, alignment)
                }
                is ParagraphSegment.Text -> if (segment.isVisible) {
                    val text = remember(segment, matches, context.search.current, context.colors, surface) {
                        paragraphText(segment.inlines, segment.offset, matches, context, surface)
                    }
                    BasicText(
                        text = text,
                        modifier = Modifier.fillMaxWidth(),
                        style = textStyle,
                        inlineContent = segment.inlineImages(context),
                    )
                }
            }
        }
    }
}

private sealed interface ParagraphSegment {
    // offset is where the text starts in Paragraph.plainText, so search highlights line up.
    class Text(val inlines: List<Inline>, val offset: Int) : ParagraphSegment {
        val isVisible: Boolean get() = inlines.any { it is ImageRun || (it as TextRun).text.isNotBlank() }
    }

    class Image(val run: ImageRun) : ParagraphSegment
}

private fun ParagraphSegment.Text.inlineImages(context: FlowContext): Map<String, InlineTextContent> =
    inlines.withIndex().filter { it.value is ImageRun }.associate { (index, inline) ->
        val run = inline as ImageRun
        val factor = ParagraphSpec.INLINE_IMAGE_SCALE * context.scale
        val width = (run.widthPt ?: ParagraphSpec.MAX_INLINE_IMAGE_PT) * factor
        val height = (run.heightPt ?: ParagraphSpec.MAX_INLINE_IMAGE_PT) * factor
        inlineImageId(index) to InlineTextContent(
            Placeholder(width.sp, height.sp, PlaceholderVerticalAlign.TextCenter),
        ) { InlineFlowImage(run, context) }
    }

// Small inline pictures stay in the text; large or floating ones get a line of their own.
private fun ImageRun.staysInText(): Boolean =
    floatAlignment == null && (heightPt ?: Float.MAX_VALUE) <= ParagraphSpec.MAX_INLINE_IMAGE_PT

private fun ParagraphPiece.segments(): List<ParagraphSegment> {
    val segments = ArrayList<ParagraphSegment>()
    var pending = ArrayList<Inline>()
    var offset = textOffset
    fun flushText() {
        if (pending.isEmpty()) return
        segments += ParagraphSegment.Text(pending, offset)
        offset += pending.sumOf { (it as? TextRun)?.text?.length ?: 0 }
        pending = ArrayList()
    }
    for (inline in inlines) {
        if (inline is ImageRun && !inline.staysInText()) {
            flushText()
            segments += ParagraphSegment.Image(inline)
        } else {
            pending += inline
        }
    }
    flushText()
    return segments
}

private fun paragraphTextStyle(
    piece: ParagraphPiece,
    context: FlowContext,
    isInHeaderRow: Boolean,
    surface: Color?,
): TextStyle {
    val paragraph = piece.source
    val style = paragraph.style
    val isHeading = style.headingLevel > 0
    val runSizes = paragraph.inlines.mapNotNull { (it as? TextRun)?.style?.relativeSize }
    // Explicit run sizes already carry the heading size, so only unsized headings get the default scale.
    val fontSize = if (runSizes.isEmpty()) context.textSize * headingScale(style.headingLevel) else context.textSize
    val hasTallRuns = (runSizes.maxOrNull() ?: 1f) > ParagraphSpec.TALL_RUN_SIZE
    val lineSpacing = style.lineSpacing?.coerceIn(ParagraphSpec.MIN_LINE_SPACING, ParagraphSpec.MAX_LINE_SPACING)
    val lineHeight = lineSpacing?.let {
        (it * ParagraphSpec.SINGLE_LINE_HEIGHT_EM).em
    } ?: ParagraphSpec.BODY_LINE_HEIGHT_EM.em
    return TextStyle(
        color = surface?.let(::inkOn) ?: context.colors.text,
        fontSize = fontSize,
        fontWeight = if (isHeading || isInHeaderRow) FontWeight.SemiBold else null,
        textAlign = style.alignment.toTextAlign(),
        lineHeight = if (hasTallRuns || isHeading) TextUnit.Unspecified else lineHeight,
        textIndent = if (piece.isFirst) firstLineIndent(style, context.scale) else null,
    )
}

private fun headingScale(level: Int): Float = when (level) {
    ParagraphStyle.TITLE_LEVEL -> ParagraphSpec.TITLE_SCALE
    else -> ParagraphSpec.HeadingScales.getOrElse(level - 1) { 1f }
}

internal fun TextAlignment.toTextAlign(): TextAlign = when (this) {
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
    borderColor: Color,
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
        .then(style.borders?.let { Modifier.paragraphBorders(it, borderColor, piece, scale) } ?: Modifier)
        .then(if (isHeading) Modifier.semantics { heading() } else Modifier)
}

// Word draws paragraph borders a few points away from the text; long paragraphs keep top and bottom at their ends.
private fun Modifier.paragraphBorders(
    borders: ParagraphBorders,
    ink: Color,
    piece: ParagraphPiece,
    scale: Float
): Modifier {
    val gap = ParagraphSpec.BORDER_GAP_PT.ptToDp(scale)
    return drawBehind {
        fun width(line: BorderLine) = (line.widthPt * scale).dp.toPx().coerceAtLeast(1f)
        fun color(line: BorderLine) = line.color?.let(::Color) ?: ink
        borders.top?.takeIf { piece.isFirst }?.let {
            drawLine(color(it), Offset.Zero, Offset(size.width, 0f), width(it))
        }
        borders.bottom?.takeIf { piece.isLast }?.let {
            drawLine(color(it), Offset(0f, size.height), Offset(size.width, size.height), width(it))
        }
        borders.start?.let { drawLine(color(it), Offset.Zero, Offset(0f, size.height), width(it)) }
        borders.end?.let { drawLine(color(it), Offset(size.width, 0f), Offset(size.width, size.height), width(it)) }
    }.padding(
        start = if (borders.start != null) gap else 0.dp,
        end = if (borders.end != null) gap else 0.dp,
        top = if (borders.top != null && piece.isFirst) gap else 0.dp,
        bottom = if (borders.bottom != null && piece.isLast) gap else 0.dp,
    )
}

// Document points map one-to-one onto dp before the user's pinch scale.
private fun Float.ptToDp(scale: Float): Dp = (this * scale).dp
