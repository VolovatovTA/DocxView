package io.github.volovatovta.docviewer.internal.flow

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.volovatovta.docviewer.DocumentViewerColors
import io.github.volovatovta.docviewer.R
import io.github.volovatovta.docviewer.core.CsvParser
import io.github.volovatovta.docviewer.core.FlowMatch
import io.github.volovatovta.docviewer.core.model.Block
import io.github.volovatovta.docviewer.core.model.Divider
import io.github.volovatovta.docviewer.core.model.ImageRun
import io.github.volovatovta.docviewer.core.model.ImageStore
import io.github.volovatovta.docviewer.core.model.PageBreak
import io.github.volovatovta.docviewer.core.model.Paragraph
import io.github.volovatovta.docviewer.core.model.ParagraphStyle
import io.github.volovatovta.docviewer.core.model.RunStyle
import io.github.volovatovta.docviewer.core.model.Table
import io.github.volovatovta.docviewer.core.model.TextAlignment
import io.github.volovatovta.docviewer.core.model.TextRun
import io.github.volovatovta.docviewer.core.model.VerticalAlign
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Immutable
internal class FlowContext(
    val colors: DocumentViewerColors,
    val textSize: TextUnit,
    val scale: Float,
    val highlights: Map<Paragraph, List<FlowMatch>>,
    val current: FlowMatch?,
    val onLink: (String) -> Unit,
    val images: ImageStore,
    val imageCache: LruCache<String, ImageBitmap>,
)

private val LocalInHeaderRow = staticCompositionLocalOf { false }

@Composable
internal fun BlockView(block: Block, ctx: FlowContext, compact: Boolean = false) {
    when (block) {
        is Paragraph -> ParagraphView(block, ctx, compact)
        is Table -> TableView(block, ctx)
        PageBreak -> PageBreakView(ctx.colors)
        Divider -> Box(Modifier.padding(vertical = 16.dp).width(96.dp).height(1.dp).background(ctx.colors.tableBorder))
    }
}

@Composable
private fun PageBreakView(colors: DocumentViewerColors) {
    Canvas(Modifier.fillMaxWidth().height(32.dp)) {
        drawLine(
            colors.tableBorder,
            Offset(0f, size.height / 2),
            Offset(size.width, size.height / 2),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())),
        )
    }
}

@Composable
private fun ParagraphView(paragraph: Paragraph, ctx: FlowContext, compact: Boolean) {
    val style = paragraph.style
    val scale = ctx.scale
    val pt = { value: Float -> (value * scale).dp }
    if (paragraph.inlines.isEmpty()) {
        // Empty paragraphs are how Word users make vertical space.
        Spacer(Modifier.height(if (compact) 0.dp else (ctx.textSize.value * 0.9f).dp))
        return
    }
    val heading = style.headingLevel
    val explicitSize = paragraph.inlines.any { it is TextRun && it.style.relativeSize != null }
    val headingScale = when (heading) {
        ParagraphStyle.TITLE_LEVEL -> 1.9f
        1 -> 1.6f
        2 -> 1.35f
        3 -> 1.2f
        4 -> 1.1f
        else -> 1f
    }
    val fontSize = if (explicitSize) ctx.textSize else ctx.textSize * headingScale
    val maxRelative = paragraph.inlines.maxOf { (it as? TextRun)?.style?.relativeSize ?: 1f }
    val inHeader = LocalInHeaderRow.current
    val textStyle = TextStyle(
        color = ctx.colors.text,
        fontSize = fontSize,
        fontWeight = if (heading > 0 || inHeader) FontWeight.SemiBold else null,
        textAlign = when (style.alignment) {
            TextAlignment.Start -> TextAlign.Start
            TextAlignment.Center -> TextAlign.Center
            TextAlignment.End -> TextAlign.End
            TextAlignment.Justify -> TextAlign.Justify
        },
        lineHeight = if (maxRelative > 1.15f || heading > 0) TextUnit.Unspecified else 1.5.em,
        textIndent = if (style.firstLineIndentPt != 0f && style.listMarker == null) {
            TextIndent(firstLine = (style.firstLineIndentPt.coerceIn(-72f, 72f) * scale).sp)
        } else {
            null
        },
    )
    val defaultBefore = if (heading > 0) 14f else 0f
    val defaultAfter = if (heading > 0) 6f else 8f
    val before = pt((style.spaceBeforePt ?: defaultBefore).coerceIn(0f, 30f) * if (compact) 0.3f else 1f)
    val after = pt((style.spaceAfterPt ?: defaultAfter).coerceIn(0f, 24f) * if (compact) 0.3f else 1f)
    val indent = pt(style.indentStartPt.coerceIn(0f, 108f))
    var modifier = Modifier.fillMaxWidth().padding(start = indent, top = before, bottom = after)
    style.background?.let { modifier = modifier.background(Color(it)) }
    if (heading > 0) modifier = modifier.semantics { heading() }

    val content: @Composable () -> Unit = { ParagraphContent(paragraph, ctx, textStyle) }
    val marker = style.listMarker
    if (marker != null) {
        Row(modifier) {
            BasicText(
                marker,
                Modifier.widthIn(min = (22 * scale).dp).padding(end = (6 * scale).dp),
                style = textStyle.copy(textIndent = null, textAlign = TextAlign.End),
            )
            Box(Modifier.weight(1f)) { content() }
        }
    } else {
        Box(modifier) { content() }
    }
}

@Composable
private fun ParagraphContent(paragraph: Paragraph, ctx: FlowContext, textStyle: TextStyle) {
    val matches = ctx.highlights[paragraph]
    val hasImages = paragraph.inlines.any { it is ImageRun }
    if (!hasImages) {
        val text = remember(paragraph, matches, ctx.current, ctx.colors) {
            buildParagraphText(paragraph.inlines.filterIsInstance<TextRun>(), 0, matches, ctx)
        }
        BasicText(text, style = textStyle)
        return
    }
    Column(Modifier.fillMaxWidth()) {
        var offset = 0
        val group = ArrayList<TextRun>()
        fun flush(): @Composable () -> Unit {
            val runs = group.toList()
            val start = offset
            offset += runs.sumOf { it.text.length }
            group.clear()
            return { if (runs.any { it.text.isNotBlank() }) BasicText(buildParagraphText(runs, start, matches, ctx), style = textStyle) }
        }
        val parts = ArrayList<@Composable () -> Unit>()
        for (inline in paragraph.inlines) {
            when (inline) {
                is TextRun -> group += inline
                is ImageRun -> {
                    if (group.isNotEmpty()) parts += flush()
                    parts += { FlowImage(inline, ctx, textStyle.textAlign) }
                }
            }
        }
        if (group.isNotEmpty()) parts += flush()
        parts.forEach { it() }
    }
}

private fun buildParagraphText(runs: List<TextRun>, offset: Int, matches: List<FlowMatch>?, ctx: FlowContext): AnnotatedString = buildAnnotatedString {
    for (run in runs) {
        val start = length
        val link = run.link
        if (link != null) {
            val linkStyle = TextLinkStyles(SpanStyle(color = ctx.colors.link, textDecoration = TextDecoration.Underline))
            withLink(LinkAnnotation.Clickable(link, linkStyle) { ctx.onLink(link) }) { append(run.text) }
        } else {
            append(run.text)
        }
        addStyle(spanStyle(run.style, ctx, isLink = link != null), start, length)
    }
    if (matches != null) {
        val end = offset + length
        for (match in matches) {
            if (match.end <= offset || match.start >= end) continue
            val color = if (match === ctx.current) ctx.colors.currentSearchMatch else ctx.colors.searchMatch
            addStyle(SpanStyle(background = color), (match.start - offset).coerceAtLeast(0), (match.end - offset).coerceAtMost(length))
        }
    }
}

private fun spanStyle(style: RunStyle, ctx: FlowContext, isLink: Boolean): SpanStyle {
    if (style == RunStyle.Default) return SpanStyle()
    val decorations = buildList {
        if (style.underline && !isLink) add(TextDecoration.Underline)
        if (style.strikethrough) add(TextDecoration.LineThrough)
    }
    val background = style.background?.let { Color(it) }
    val size = style.relativeSize?.coerceIn(0.5f, 4f)
    val shifted = style.verticalAlign != VerticalAlign.Baseline
    return SpanStyle(
        color = if (isLink) Color.Unspecified else readableColor(style.color, background, ctx.colors),
        fontSize = when {
            shifted -> ((size ?: 1f) * 0.72f).em
            size != null -> size.em
            else -> TextUnit.Unspecified
        },
        fontWeight = if (style.bold) FontWeight.Bold else null,
        fontStyle = if (style.italic) FontStyle.Italic else null,
        fontFamily = if (style.monospace) FontFamily.Monospace else null,
        fontFeatureSettings = if (style.smallCaps) "smcp" else null,
        baselineShift = when (style.verticalAlign) {
            VerticalAlign.Superscript -> BaselineShift.Superscript
            VerticalAlign.Subscript -> BaselineShift.Subscript
            VerticalAlign.Baseline -> null
        },
        textDecoration = if (decorations.isEmpty()) null else TextDecoration.combine(decorations),
        background = background ?: Color.Unspecified,
    )
}

// Keeps document colors unless they would vanish on the current page color (e.g. black text in dark mode).
private fun readableColor(argb: Int?, background: Color?, colors: DocumentViewerColors): Color {
    val against = background ?: colors.page
    if (argb == null) return if (background != null) onColor(background) else Color.Unspecified
    val color = Color(argb)
    return if (contrast(color, against) >= 2.2f) color else if (background != null) onColor(background) else Color.Unspecified
}

private fun onColor(background: Color) = if (background.luminance() > 0.45f) Color(0xFF111111) else Color.White

private fun contrast(a: Color, b: Color): Float {
    val la = a.luminance() + 0.05f
    val lb = b.luminance() + 0.05f
    return if (la > lb) la / lb else lb / la
}

private sealed interface ImageState {
    data object Loading : ImageState
    data object Unsupported : ImageState
    class Ready(val bitmap: ImageBitmap) : ImageState
}

@Composable
private fun FlowImage(run: ImageRun, ctx: FlowContext, align: TextAlign) {
    val context = LocalContext.current
    val state by produceState<ImageState>(ctx.imageCache.get(run.imageId)?.let { ImageState.Ready(it) } ?: ImageState.Loading, run.imageId) {
        if (value is ImageState.Ready) return@produceState
        value = withContext(Dispatchers.IO) {
            val bytes = runCatching { ctx.images.open(run.imageId) }.getOrNull() ?: return@withContext ImageState.Unsupported
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0) return@withContext ImageState.Unsupported
            val target = context.resources.displayMetrics.widthPixels.coerceIn(480, 2048)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= target) sample *= 2
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
                ?: return@withContext ImageState.Unsupported
            ImageState.Ready(bitmap.asImageBitmap().also { ctx.imageCache.put(run.imageId, it) })
        }
    }
    var fullscreen by remember { mutableStateOf(false) }
    val widthPt = run.widthPt?.takeIf { it > 1f }
    val heightPt = run.heightPt?.takeIf { it > 1f }
    val ratio = when (val s = state) {
        is ImageState.Ready -> s.bitmap.width.toFloat() / s.bitmap.height
        else -> if (widthPt != null && heightPt != null) widthPt / heightPt else 1.6f
    }.coerceIn(0.1f, 10f)
    val maxWidth: Dp = widthPt?.let { (it * 1.25f * ctx.scale).dp } ?: Dp.Unspecified
    val alignment = when (align) {
        TextAlign.Center -> Alignment.Center
        TextAlign.End, TextAlign.Right -> Alignment.CenterEnd
        else -> Alignment.CenterStart
    }
    Box(Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = alignment) {
        val shape = RoundedCornerShape(4.dp)
        val frame = Modifier.widthIn(max = maxWidth).fillMaxWidth().aspectRatio(ratio).clip(shape)
        when (val s = state) {
            is ImageState.Ready -> Image(
                s.bitmap,
                contentDescription = run.description,
                modifier = frame.clickable { fullscreen = true },
                contentScale = ContentScale.Fit,
            )
            ImageState.Loading -> Box(frame.background(ctx.colors.stripe))
            ImageState.Unsupported -> Box(frame.background(ctx.colors.stripe), contentAlignment = Alignment.Center) {
                BasicText(
                    run.description ?: stringResource(R.string.dv_image_unsupported),
                    Modifier.padding(12.dp),
                    style = TextStyle(color = ctx.colors.secondaryText, fontSize = 13.sp, textAlign = TextAlign.Center),
                )
            }
        }
    }
    val ready = state as? ImageState.Ready
    if (fullscreen && ready != null) ZoomableImageDialog(ready.bitmap, run.description) { fullscreen = false }
}

@Composable
private fun ZoomableImageDialog(bitmap: ImageBitmap, description: String?, onDismiss: () -> Unit) {
    Dialog(onDismiss, DialogProperties(usePlatformDefaultWidth = false)) {
        var zoom by remember { mutableFloatStateOf(1f) }
        var pan by remember { mutableStateOf(Offset.Zero) }
        Box(
            Modifier.fillMaxSize().background(Color.Black)
                .pointerInput(Unit) {
                    detectTransformGestures { _, panChange, zoomChange, _ ->
                        zoom = (zoom * zoomChange).coerceIn(1f, 8f)
                        pan = if (zoom == 1f) Offset.Zero else pan + panChange
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onDismiss() },
                        onDoubleTap = {
                            zoom = if (zoom > 1f) 1f else 3f
                            pan = Offset.Zero
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Image(
                bitmap,
                contentDescription = description,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    scaleX = zoom
                    scaleY = zoom
                    translationX = pan.x
                    translationY = pan.y
                },
                contentScale = ContentScale.Fit,
            )
        }
    }
}

private class TableGeometry {
    var columnX = IntArray(0)
    var rowY = IntArray(0)
    // Each entry: row, first column, last column (exclusive), merged-with-above flag, background, has-background flag.
    var cells = ArrayList<IntArray>()
    var contentWidth = 0
    var viewportWidth = 0
}

@Composable
private fun TableView(table: Table, ctx: FlowContext) {
    if (table.rows.isEmpty()) return
    val colors = ctx.colors
    var offset by remember(table) { mutableFloatStateOf(0f) }
    val geometry = remember(table) { TableGeometry() }
    val scrollState = rememberScrollableState { delta ->
        val max = (geometry.contentWidth - geometry.viewportWidth).coerceAtLeast(0).toFloat()
        val old = offset
        offset = (offset - delta).coerceIn(0f, max)
        old - offset
    }
    val padding = (6 * ctx.scale).dp
    val measurer = rememberTextMeasurer(cacheSize = 0)
    val localDensity = LocalDensity.current
    val contentWidths = remember(table, ctx.scale) {
        measureColumns(table, measurer, TextStyle(fontSize = ctx.textSize), with(localDensity) { (padding + 2.dp).toPx() * 2 + 2.dp.toPx() })
    }
    Layout(
        content = {
            table.rows.forEach { row ->
                CompositionLocalProvider(LocalInHeaderRow provides row.isHeader) {
                    row.cells.forEach { cell ->
                        Column(Modifier.padding(horizontal = padding + 2.dp, vertical = padding)) {
                            cell.blocks.forEach { BlockView(it, ctx, compact = true) }
                        }
                    }
                }
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clipToBounds()
            .scrollable(scrollState, Orientation.Horizontal)
            .drawWithContent {
                drawContent()
                // Fading edges hint that the table continues off-screen.
                val max = geometry.contentWidth - geometry.viewportWidth
                if (max <= 0) return@drawWithContent
                val fade = 28.dp.toPx()
                if (offset < max - 1) drawRect(Brush.horizontalGradient(listOf(Color.Transparent, colors.page), size.width - fade, size.width), Offset(size.width - fade, 0f), Size(fade, size.height))
                if (offset > 1) drawRect(Brush.horizontalGradient(listOf(colors.page, Color.Transparent), 0f, fade), Offset.Zero, Size(fade, size.height))
            }
            .drawBehind {
                val shift = offset
                val stroke = 1.dp.toPx()
                val rows = geometry.rowY
                val cols = geometry.columnX
                for (cell in geometry.cells) {
                    val row = cell[0]
                    val first = cell[1]
                    val last = cell[2]
                    val merged = cell[3]
                    val bg = cell[4]
                    val hasBg = cell[5]
                    val left = cols[first] - shift
                    val right = cols[last] - shift
                    val top = rows[row].toFloat()
                    val bottom = rows[row + 1].toFloat()
                    val header = table.rows[row].isHeader
                    if (hasBg == 1) drawRect(Color(bg), Offset(left, top), Size(right - left, bottom - top))
                    else if (header) drawRect(colors.tableHeader, Offset(left, top), Size(right - left, bottom - top))
                    if (merged == 0) drawLine(colors.tableBorder, Offset(left, top), Offset(right, top), stroke)
                    drawLine(colors.tableBorder, Offset(left, top), Offset(left, bottom), stroke)
                    drawLine(colors.tableBorder, Offset(right, top), Offset(right, bottom), stroke)
                }
                val last = rows.last().toFloat() - stroke / 2
                drawLine(colors.tableBorder, Offset(cols.first() - shift, last), Offset(cols.last() - shift, last), stroke)
            },
    ) { measurables, constraints ->
        val viewport = constraints.maxWidth
        val count = table.columnCount.coerceAtLeast(1)
        val minColumn = (48.dp.toPx() * ctx.scale).roundToInt()
        val widths = columnWidths(table, contentWidths, viewport.toFloat(), minColumn.toFloat())
        val columnX = IntArray(count + 1)
        for (i in 0 until count) columnX[i + 1] = columnX[i] + widths[i].roundToInt()
        val contentWidth = columnX[count]
        val placeables = ArrayList<Triple<androidx.compose.ui.layout.Placeable, Int, Int>>()
        val rowY = IntArray(table.rows.size + 1)
        val cells = ArrayList<IntArray>()
        var m = 0
        table.rows.forEachIndexed { r, row ->
            var col = 0
            var rowHeight = (24.dp.toPx() * ctx.scale).roundToInt()
            val rowPlaceables = ArrayList<Pair<androidx.compose.ui.layout.Placeable, Int>>()
            row.cells.forEach { cell ->
                val first = col.coerceAtMost(count - 1)
                val last = (col + cell.colSpan).coerceAtMost(count).coerceAtLeast(first + 1)
                col = last
                val width = columnX[last] - columnX[first]
                val placeable = measurables[m++].measure(Constraints(minWidth = width, maxWidth = width))
                rowHeight = maxOf(rowHeight, placeable.height)
                rowPlaceables += placeable to columnX[first]
                cells += intArrayOf(r, first, last, if (cell.mergedWithAbove) 1 else 0, cell.background ?: 0, if (cell.background != null) 1 else 0)
            }
            if (col < count) cells += intArrayOf(r, col, count, 0, 0, 0)
            rowY[r + 1] = rowY[r] + rowHeight
            rowPlaceables.forEach { (p, x) -> placeables += Triple(p, x, rowY[r]) }
        }
        geometry.columnX = columnX
        geometry.rowY = rowY
        geometry.cells = cells
        geometry.contentWidth = contentWidth
        geometry.viewportWidth = viewport
        val width = if (constraints.hasBoundedWidth) viewport else contentWidth
        layout(width, rowY.last()) {
            placeables.forEach { (p, x, y) -> p.placeWithLayer(x - offset.roundToInt(), y) }
        }
    }
}

private class ColumnContent(val min: FloatArray, val max: FloatArray)

// Longest unbreakable word and longest line per column; numbers like "12 400" count as one word.
private fun measureColumns(table: Table, measurer: TextMeasurer, style: TextStyle, padding: Float): ColumnContent {
    val count = table.columnCount.coerceAtLeast(1)
    val min = FloatArray(count)
    val max = FloatArray(count)
    val bold = style.copy(fontWeight = FontWeight.SemiBold)
    table.rows.take(60).forEach { row ->
        var col = 0
        row.cells.forEach { cell ->
            if (cell.colSpan == 1 && col < count) {
                val textStyle = if (row.isHeader) bold else style
                cell.blocks.filterIsInstance<Paragraph>().forEach { paragraph ->
                    val text = paragraph.plainText.trim()
                    if (text.isNotEmpty()) {
                        val words = if (CsvParser.isNumeric(text)) listOf(text) else text.split(' ', '\t', '\n').filter { it.isNotEmpty() }
                        val longest = words.maxByOrNull { it.length }.orEmpty().take(40)
                        min[col] = maxOf(min[col], measurer.measure(longest, textStyle, maxLines = 1).size.width + padding)
                        max[col] = maxOf(max[col], measurer.measure(text.take(160), textStyle, maxLines = 1).size.width + padding)
                    }
                }
            }
            col += cell.colSpan
        }
    }
    return ColumnContent(min, max)
}

// HTML-like auto layout: keep the author's proportions when they fit the content, otherwise share space by need.
private fun columnWidths(table: Table, content: ColumnContent, viewport: Float, minColumn: Float): FloatArray {
    val count = content.min.size
    val min = FloatArray(count) { maxOf(content.min[it], minColumn) }
    val max = FloatArray(count) { maxOf(content.max[it], min[it]).coerceAtMost(maxOf(viewport * 0.8f, min[it])) }
    if (table.columnWidths.size == count) {
        val total = table.columnWidths.sum()
        val fitted = FloatArray(count) { viewport * table.columnWidths[it] / total }
        if ((0 until count).all { fitted[it] >= min[it] }) return fitted
    }
    val minSum = min.sum()
    val maxSum = max.sum()
    return when {
        maxSum <= 0f -> FloatArray(count) { viewport / count }
        maxSum <= viewport -> FloatArray(count) { max[it] + (viewport - maxSum) * max[it] / maxSum }
        minSum <= viewport -> {
            val slack = maxSum - minSum
            FloatArray(count) { min[it] + if (slack > 0f) (viewport - minSum) * (max[it] - min[it]) / slack else 0f }
        }
        else -> min
    }
}
