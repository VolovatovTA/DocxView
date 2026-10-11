package io.github.volovatovta.docviewer.internal.pdf

import kotlin.math.roundToInt
import kotlin.math.sqrt

// Compose packs width and height into one Long: the wider a box, the fewer bits remain for its height.
private val ConstraintsLimits = listOf(8_190 to 262_142, 32_766 to 65_534, 65_534 to 32_766, 262_142 to 8_190)

// Geometry at zoom 1, with pages fitted to the viewport width; list item i is the gap above page i plus the page.
internal class PdfLayout(val pageSizes: List<PdfPageSize>, viewportWidth: Float, val spacing: Float) {

    val pageCount = pageSizes.size

    val fitPageWidth = (viewportWidth - 2 * spacing).coerceAtLeast(1f)

    // A page too tall for Compose to measure even at zoom 1 (a 10 × 3000 pt strip) is squeezed to what fits.
    private val maxFitPageHeight = itemMaxHeight(viewportWidth.roundToInt()) * ROUNDING_HEADROOM - spacing

    private val fitPageHeights = FloatArray(pageCount) {
        minOf(fitPageWidth * pageSizes[it].height / pageSizes[it].width, maxFitPageHeight)
    }

    private val pageWidthsPt = FloatArray(pageCount) { pageSizes[it].width }

    private val fitTops = FloatArray(pageCount + 1).also { tops ->
        for (page in 0 until pageCount) tops[page + 1] = tops[page] + fitPageHeights[page] + spacing
    }

    val fitContentHeight = fitTops[pageCount] + spacing

    private val tallestFitPage = fitPageHeights.maxOrNull() ?: fitPageWidth

    // Base bitmaps are capped in pixels so a dozen cached pages fit the budget; tiles add sharpness when zoomed.
    val baseBitmapWidth = minOf(fitPageWidth, sqrt(BASE_BITMAP_PIXELS * fitPageWidth / tallestFitPage))

    fun pageWidthPt(page: Int): Float = pageWidthsPt[page]

    fun pageWidth(scale: Float): Int = (fitPageWidth * scale).roundToInt()

    fun pageHeight(page: Int, scale: Float): Int = (fitPageHeights[page] * scale).roundToInt()

    fun gap(scale: Float): Int = (spacing * scale).roundToInt()

    // Rounded prefix sums keep item heights consistent with absolute offsets at any zoom.
    fun top(page: Int, scale: Float): Int = (fitTops[page.coerceIn(0, pageCount)] * scale).roundToInt()

    fun itemHeight(page: Int, scale: Float): Int = top(page + 1, scale) - top(page, scale)

    fun pageAt(fitY: Float): Int {
        var low = 0
        var high = pageCount - 1
        while (low < high) {
            val middle = (low + high + 1) ushr 1
            if (fitTops[middle] <= fitY) low = middle else high = middle - 1
        }
        return low.coerceAtLeast(0)
    }

    // The largest zoom at which every page and its list item can still be measured by Compose.
    fun maxRepresentableScale(viewportWidth: Int): Float {
        val pageLimit = ConstraintsLimits.maxOf { (maxWidth, maxHeight) ->
            minOf(maxWidth / fitPageWidth, maxHeight / tallestFitPage)
        }
        val itemLimit = itemMaxHeight(viewportWidth) / (tallestFitPage + spacing)
        return minOf(pageLimit, itemLimit) * ROUNDING_HEADROOM
    }

    private fun itemMaxHeight(viewportWidth: Int): Int =
        ConstraintsLimits.firstOrNull { (maxWidth, _) -> viewportWidth <= maxWidth }?.second ?: 0

    private companion object {
        const val BASE_BITMAP_PIXELS = 2_500_000f
        const val ROUNDING_HEADROOM = 0.99f
    }
}
