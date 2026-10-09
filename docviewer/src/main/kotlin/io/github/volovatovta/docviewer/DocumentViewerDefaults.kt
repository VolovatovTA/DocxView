package io.github.volovatovta.docviewer

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.volovatovta.docviewer.core.CsvOptions

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

@Immutable
public data class DocumentViewerColors(
    val background: Color,
    val page: Color,
    val text: Color,
    val secondaryText: Color,
    val accent: Color,
    val onAccent: Color,
    val link: Color,
    val searchMatch: Color,
    val currentSearchMatch: Color,
    val tableBorder: Color,
    val tableHeader: Color,
    val stripe: Color,
    val scrollbar: Color,
    val overlay: Color,
    val onOverlay: Color,
) {
    public val isDark: Boolean get() = page.luminance() < 0.4f
}

public object DocumentViewerDefaults {

    private val Accent = Color(0xFF2F6FEB)

    @JvmStatic
    @JvmOverloads
    public fun lightColors(
        accent: Color = Accent,
        background: Color = Color(0xFFEFF1F4),
        page: Color = Color.White,
        text: Color = Color(0xFF1B1D21),
    ): DocumentViewerColors = DocumentViewerColors(
        background = background,
        page = page,
        text = text,
        secondaryText = text.copy(alpha = 0.62f),
        accent = accent,
        onAccent = Color.White,
        link = accent,
        searchMatch = Color(0x66FFD54F),
        currentSearchMatch = Color(0xCCFF9800),
        tableBorder = Color(0xFFD5D9E0),
        tableHeader = Color(0xFFF2F4F7),
        stripe = Color(0xFFF8F9FB),
        scrollbar = Color(0x99424852),
        overlay = Color(0xE6202226),
        onOverlay = Color.White,
    )

    @JvmStatic
    @JvmOverloads
    public fun darkColors(
        accent: Color = Color(0xFF7AA7FF),
        background: Color = Color(0xFF0F1012),
        page: Color = Color(0xFF1C1D21),
        text: Color = Color(0xFFE4E6EB),
    ): DocumentViewerColors = DocumentViewerColors(
        background = background,
        page = page,
        text = text,
        secondaryText = text.copy(alpha = 0.6f),
        accent = accent,
        onAccent = Color(0xFF0B1A33),
        link = accent,
        searchMatch = Color(0x55FFD54F),
        currentSearchMatch = Color(0xB3FF9800),
        tableBorder = Color(0xFF3A3D44),
        tableHeader = Color(0xFF26282D),
        stripe = Color(0xFF212226),
        scrollbar = Color(0x99C9CCD3),
        overlay = Color(0xE6F0F1F4),
        onOverlay = Color(0xFF15171A),
    )

    @Composable
    public fun colors(darkTheme: Boolean = isSystemInDarkTheme()): DocumentViewerColors = if (darkTheme) darkColors() else lightColors()
}
