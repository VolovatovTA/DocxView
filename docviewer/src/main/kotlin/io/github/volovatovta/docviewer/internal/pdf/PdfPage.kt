package io.github.volovatovta.docviewer.internal.pdf

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.volovatovta.docviewer.DocumentViewerColors
import kotlin.math.roundToInt

private val PageShadow = 1.5.dp

private const val CHANNEL_MAX = 255f
private const val OFFSET_COLUMN = 4

// Inverts page colors for reading in the dark: every color channel becomes 255 minus itself, alpha stays.
internal val NightModeFilter = ColorFilter.colorMatrix(
    ColorMatrix().apply {
        setToScale(redScale = -1f, greenScale = -1f, blueScale = -1f, alphaScale = 1f)
        for (channel in 0..2) this[channel, OFFSET_COLUMN] = CHANNEL_MAX
    },
)

@Composable
internal fun PdfPage(
    page: Int,
    images: PdfPageImages,
    tile: PdfTile?,
    highlights: PageHighlights?,
    colors: DocumentViewerColors,
    nightFilter: ColorFilter?,
    modifier: Modifier = Modifier,
) {
    val base by produceState(images.cached(page), images, page) {
        if (value == null) value = images.load(page)
    }
    val paper = if (nightFilter != null) Color.Black else Color.White
    Canvas(modifier.shadow(PageShadow).background(paper)) {
        base?.let { drawBase(it, nightFilter) }
        tile?.let { drawTile(it, nightFilter) }
        highlights?.let { drawHighlights(it, colors) }
    }
}

// Fit-width page bitmaps of one document, shared through a cache by all its pages.
@Stable
internal class PdfPageImages(
    private val document: PdfDocument,
    private val layout: PdfLayout,
    private val cache: BitmapCache,
) {
    private val baseScale = layout.baseBitmapWidth / layout.fitPageWidth

    fun cached(page: Int): ImageBitmap? = cache.get(key(page))?.asImageBitmap()

    suspend fun load(page: Int): ImageBitmap? {
        val bitmap = document.render(page, layout.pageWidth(baseScale), layout.pageHeight(page, baseScale))
        bitmap?.let { cache.put(key(page), it) }
        return bitmap?.asImageBitmap()
    }

    private fun key(page: Int) = "$page@${layout.pageWidth(baseScale)}x${layout.pageHeight(page, baseScale)}"
}

private fun DrawScope.drawBase(image: ImageBitmap, filter: ColorFilter?) {
    drawImage(
        image,
        dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
        filterQuality = FilterQuality.Medium,
        colorFilter = filter,
    )
}

// The tile was rendered for a page width that may differ from the current one until it is re-rendered.
private fun DrawScope.drawTile(tile: PdfTile, filter: ColorFilter?) {
    val scale = size.width / tile.pageWidthPx
    drawImage(
        tile.bitmap,
        dstOffset = IntOffset((tile.region.left * scale).roundToInt(), (tile.region.top * scale).roundToInt()),
        dstSize = IntSize((tile.bitmap.width * scale).roundToInt(), (tile.bitmap.height * scale).roundToInt()),
        filterQuality = FilterQuality.Low,
        colorFilter = filter,
    )
}

private fun DrawScope.drawHighlights(highlights: PageHighlights, colors: DocumentViewerColors) {
    val scale = size.width / highlights.pageWidthPt
    for (hit in highlights.hits) {
        val color = if (hit === highlights.current) colors.currentSearchMatch else colors.searchMatch
        hit.rects.forEach { rect ->
            drawRect(
                color = color,
                topLeft = Offset(rect.left * scale, rect.top * scale),
                size = Size(rect.width() * scale, rect.height() * scale),
            )
        }
    }
}
