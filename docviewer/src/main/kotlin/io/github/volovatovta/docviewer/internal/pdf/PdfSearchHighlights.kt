package io.github.volovatovta.docviewer.internal.pdf

import android.graphics.RectF
import androidx.compose.runtime.Immutable

// One search match; a match that wraps onto the next line has several rectangles, in PDF points.
internal class PdfHit(val page: Int, val rects: List<RectF>)

@Immutable
internal class PdfSearchHighlights(hits: List<PdfHit>, private val current: PdfHit?) {
    private val hitsByPage = hits.groupBy { it.page }

    fun forPage(page: Int, pageWidthPt: Float): PageHighlights? = hitsByPage[page]?.let { hits ->
        PageHighlights(hits, current?.takeIf { it.page == page }, pageWidthPt)
    }
}

@Immutable
internal class PageHighlights(val hits: List<PdfHit>, val current: PdfHit?, val pageWidthPt: Float)
