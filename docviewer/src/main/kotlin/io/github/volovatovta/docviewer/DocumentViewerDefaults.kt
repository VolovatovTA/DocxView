package io.github.volovatovta.docviewer

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Ready-made color schemes for [DocumentViewer] and [DocumentView]. */
public object DocumentViewerDefaults {

    @JvmStatic
    @JvmOverloads
    public fun lightColors(
        accent: Color = LightPalette.Accent,
        background: Color = LightPalette.Background,
        page: Color = Color.White,
        text: Color = LightPalette.Text,
    ): DocumentViewerColors = DocumentViewerColors(
        background = background,
        page = page,
        text = text,
        secondaryText = text.copy(alpha = LightPalette.SECONDARY_TEXT_ALPHA),
        accent = accent,
        onAccent = Color.White,
        link = accent,
        searchMatch = LightPalette.SearchMatch,
        currentSearchMatch = LightPalette.CurrentSearchMatch,
        tableBorder = LightPalette.TableBorder,
        tableHeader = LightPalette.TableHeader,
        stripe = LightPalette.Stripe,
        scrollbar = LightPalette.Scrollbar,
        overlay = LightPalette.Overlay,
        onOverlay = Color.White,
    )

    @JvmStatic
    @JvmOverloads
    public fun darkColors(
        accent: Color = DarkPalette.Accent,
        background: Color = DarkPalette.Background,
        page: Color = DarkPalette.Page,
        text: Color = DarkPalette.Text,
    ): DocumentViewerColors = DocumentViewerColors(
        background = background,
        page = page,
        text = text,
        secondaryText = text.copy(alpha = DarkPalette.SECONDARY_TEXT_ALPHA),
        accent = accent,
        onAccent = DarkPalette.OnAccent,
        link = accent,
        searchMatch = DarkPalette.SearchMatch,
        currentSearchMatch = DarkPalette.CurrentSearchMatch,
        tableBorder = DarkPalette.TableBorder,
        tableHeader = DarkPalette.TableHeader,
        stripe = DarkPalette.Stripe,
        scrollbar = DarkPalette.Scrollbar,
        overlay = DarkPalette.Overlay,
        onOverlay = DarkPalette.OnOverlay,
    )

    @Composable
    public fun colors(darkTheme: Boolean = isSystemInDarkTheme()): DocumentViewerColors =
        if (darkTheme) darkColors() else lightColors()
}

private object LightPalette {
    const val SECONDARY_TEXT_ALPHA = 0.62f
    val Accent = Color(0xFF2F6FEB)
    val Background = Color(0xFFEFF1F4)
    val Text = Color(0xFF1B1D21)
    val SearchMatch = Color(0x66FFD54F)
    val CurrentSearchMatch = Color(0xCCFF9800)
    val TableBorder = Color(0xFFD5D9E0)
    val TableHeader = Color(0xFFF2F4F7)
    val Stripe = Color(0xFFF8F9FB)
    val Scrollbar = Color(0x99424852)
    val Overlay = Color(0xE6202226)
}

private object DarkPalette {
    const val SECONDARY_TEXT_ALPHA = 0.6f
    val Accent = Color(0xFF7AA7FF)
    val Background = Color(0xFF0F1012)
    val Page = Color(0xFF1C1D21)
    val Text = Color(0xFFE4E6EB)
    val OnAccent = Color(0xFF0B1A33)
    val SearchMatch = Color(0x55FFD54F)
    val CurrentSearchMatch = Color(0xB3FF9800)
    val TableBorder = Color(0xFF3A3D44)
    val TableHeader = Color(0xFF26282D)
    val Stripe = Color(0xFF212226)
    val Scrollbar = Color(0x99C9CCD3)
    val Overlay = Color(0xE6F0F1F4)
    val OnOverlay = Color(0xFF15171A)
}
