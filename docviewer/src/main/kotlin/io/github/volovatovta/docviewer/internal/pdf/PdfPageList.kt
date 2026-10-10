package io.github.volovatovta.docviewer.internal.pdf

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import io.github.volovatovta.docviewer.DocumentViewerColors
import io.github.volovatovta.docviewer.R
import kotlin.math.roundToInt

private const val TRAILING_GAP_KEY = "trailing-gap"

// Every page is laid out at the committed zoom, so the list only composes what is on screen at any scale.
@Composable
internal fun PdfPageList(
    viewport: PdfViewport,
    images: PdfPageImages,
    tiles: Map<Int, PdfTile>,
    highlights: PdfSearchHighlights,
    colors: DocumentViewerColors,
    nightFilter: ColorFilter?,
    doubleTapZoom: Float,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val pageCount = viewport.layout.pageCount
    LazyColumn(
        state = viewport.listState,
        userScrollEnabled = false,
        modifier = modifier
            .fillMaxSize()
            .pdfGestureZoom(viewport)
            .pdfPanAndPinch(viewport, scope)
            .pdfDoubleTapZoom(viewport, scope, doubleTapZoom)
            .pdfMouseWheel(viewport),
    ) {
        items(pageCount, key = { it }) { page ->
            val description = stringResource(R.string.dv_page_description, page + 1, pageCount)
            PdfPage(
                page = page,
                images = images,
                tile = tiles[page],
                highlights = highlights.forPage(page, viewport.layout.pageWidthPt(page)),
                colors = colors,
                nightFilter = nightFilter,
                modifier = Modifier
                    .semantics { contentDescription = description }
                    .pdfPagePlacement(viewport, page),
            )
        }
        item(key = TRAILING_GAP_KEY) {
            Spacer(Modifier.pdfTrailingGap(viewport))
        }
    }
}

// Measuring reads the zoom and placing reads the horizontal offset, so panning never re-measures pages.
private fun Modifier.pdfPagePlacement(viewport: PdfViewport, page: Int): Modifier = layout { measurable, constraints ->
    val geometry = viewport.layout
    val scale = viewport.scale
    val pageConstraints = Constraints.fixed(geometry.pageWidth(scale), geometry.pageHeight(page, scale))
    val placeable = measurable.measure(pageConstraints)
    layout(constraints.maxWidth, geometry.itemHeight(page, scale)) {
        placeable.place(viewport.pageLeft.roundToInt(), geometry.gap(scale))
    }
}

private fun Modifier.pdfTrailingGap(viewport: PdfViewport): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(Constraints.fixed(constraints.maxWidth, viewport.layout.gap(viewport.scale)))
    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
}
