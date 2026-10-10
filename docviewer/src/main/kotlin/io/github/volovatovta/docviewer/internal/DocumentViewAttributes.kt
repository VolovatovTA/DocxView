package io.github.volovatovta.docviewer.internal

import android.content.res.Configuration
import android.content.res.Resources
import android.content.res.TypedArray
import android.util.AttributeSet
import android.view.View
import androidx.annotation.StyleableRes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.volovatovta.docviewer.DocumentSource
import io.github.volovatovta.docviewer.DocumentView
import io.github.volovatovta.docviewer.DocumentViewerColors
import io.github.volovatovta.docviewer.DocumentViewerConfig
import io.github.volovatovta.docviewer.DocumentViewerDefaults
import io.github.volovatovta.docviewer.R

// The dv_* XML attributes of a DocumentView, read once at inflation.
internal class DocumentViewAttributes(
    val config: DocumentViewerConfig,
    val theme: DocumentView.Theme,
    // null when no color attribute is set, so the view follows the system theme live.
    val colors: DocumentViewerColors?,
    val source: DocumentSource?,
) {
    companion object {
        fun read(view: View, attrs: AttributeSet?, defStyleAttr: Int): DocumentViewAttributes {
            val array = view.context.obtainStyledAttributes(attrs, R.styleable.DocumentView, defStyleAttr, 0)
            try {
                val theme = array.theme()
                return DocumentViewAttributes(
                    config = array.config(view.resources),
                    theme = theme,
                    colors = array.colors(theme, view.resources),
                    source = array.getString(R.styleable.DocumentView_dv_source)
                        ?.takeIf { it.isNotBlank() }
                        ?.let(DocumentSource::parse),
                )
            } finally {
                array.recycle()
            }
        }
    }
}

private val ColorAttributes = intArrayOf(
    R.styleable.DocumentView_dv_accentColor,
    R.styleable.DocumentView_dv_backgroundColor,
    R.styleable.DocumentView_dv_pageColor,
    R.styleable.DocumentView_dv_textColor,
)

private fun TypedArray.config(resources: Resources): DocumentViewerConfig {
    val defaults = DocumentViewerConfig()
    return DocumentViewerConfig(
        maxZoom = getFloat(R.styleable.DocumentView_dv_maxZoom, defaults.maxZoom),
        doubleTapZoom = getFloat(R.styleable.DocumentView_dv_doubleTapZoom, defaults.doubleTapZoom),
        pageSpacing = dp(R.styleable.DocumentView_dv_pageSpacing, resources, defaults.pageSpacing),
        textSize = sp(R.styleable.DocumentView_dv_textSize, resources, defaults.textSize),
        textSelectable = getBoolean(R.styleable.DocumentView_dv_textSelectable, defaults.textSelectable),
        showPageIndicator = getBoolean(R.styleable.DocumentView_dv_showPageIndicator, defaults.showPageIndicator),
        showScrollbar = getBoolean(R.styleable.DocumentView_dv_showScrollbar, defaults.showScrollbar),
        pdfNightMode = getBoolean(R.styleable.DocumentView_dv_pdfNightMode, defaults.pdfNightMode),
        showCsvRowNumbers = getBoolean(R.styleable.DocumentView_dv_csvRowNumbers, defaults.showCsvRowNumbers),
    )
}

private fun TypedArray.theme(): DocumentView.Theme =
    DocumentView.Theme.entries.getOrElse(getInt(R.styleable.DocumentView_dv_theme, 0)) { DocumentView.Theme.Auto }

private fun TypedArray.colors(theme: DocumentView.Theme, resources: Resources): DocumentViewerColors? {
    if (ColorAttributes.none { hasValue(it) }) return null
    val dark = theme.isDark(resources)
    val base = if (dark) DocumentViewerDefaults.darkColors() else DocumentViewerDefaults.lightColors()
    val accent = color(R.styleable.DocumentView_dv_accentColor, base.accent)
    val text = color(R.styleable.DocumentView_dv_textColor, base.text)
    return base.copy(
        accent = accent,
        link = accent,
        background = color(R.styleable.DocumentView_dv_backgroundColor, base.background),
        page = color(R.styleable.DocumentView_dv_pageColor, base.page),
        text = text,
        secondaryText = text.copy(alpha = base.secondaryText.alpha),
    )
}

private fun DocumentView.Theme.isDark(resources: Resources): Boolean = when (this) {
    DocumentView.Theme.Dark -> true
    DocumentView.Theme.Light -> false
    DocumentView.Theme.Auto ->
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
}

private fun TypedArray.color(@StyleableRes index: Int, fallback: Color): Color =
    if (hasValue(index)) Color(getColor(index, 0)) else fallback

private fun TypedArray.dp(@StyleableRes index: Int, resources: Resources, fallback: Dp): Dp =
    if (hasValue(index)) (getDimension(index, 0f) / resources.displayMetrics.density).dp else fallback

private fun TypedArray.sp(@StyleableRes index: Int, resources: Resources, fallback: TextUnit): TextUnit {
    if (!hasValue(index)) return fallback
    val pixelsPerSp = resources.displayMetrics.density * resources.configuration.fontScale
    return (getDimension(index, 0f) / pixelsPerSp).sp
}
