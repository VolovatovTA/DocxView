package io.github.volovatovta.docviewer

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/** Colors of a [DocumentViewer]; start from one of the [DocumentViewerDefaults] schemes. */
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
    public val isDark: Boolean get() = page.luminance() < DARK_PAGE_LUMINANCE

    private companion object {
        const val DARK_PAGE_LUMINANCE = 0.4f
    }
}
