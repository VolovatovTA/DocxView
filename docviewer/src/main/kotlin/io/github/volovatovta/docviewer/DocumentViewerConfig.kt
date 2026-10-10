package io.github.volovatovta.docviewer

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.volovatovta.docviewer.core.CsvOptions

/** Behavior and layout settings of a [DocumentViewer]. */
@Immutable
public data class DocumentViewerConfig(
    val maxZoom: Float = 6f,
    val doubleTapZoom: Float = 2.5f,
    val pageSpacing: Dp = 10.dp,
    // Body text size of DOCX/DOC/TXT before pinch scaling.
    val textSize: TextUnit = 16.sp,
    val maxContentWidth: Dp = 760.dp,
    val textSelectable: Boolean = true,
    val showPageIndicator: Boolean = true,
    val showScrollbar: Boolean = true,
    // Inverts PDF pages for reading in the dark.
    val pdfNightMode: Boolean = false,
    val csvOptions: CsvOptions = CsvOptions(),
    val showCsvRowNumbers: Boolean = true,
)
