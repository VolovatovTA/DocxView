package io.github.volovatovta.docviewer

import android.content.Context
import android.content.res.Configuration
import android.net.Uri
import android.os.Parcel
import android.os.Parcelable
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.volovatovta.docviewer.core.model.DocumentFormat
import kotlinx.coroutines.flow.distinctUntilChanged
import java.io.File

// Callbacks for the View-based API; every method has a default, so implement only what you need.
public interface DocumentViewListener {
    public fun onStatusChanged(status: DocumentStatus) {}

    public fun onPageChanged(page: Int, pageCount: Int) {}

    public fun onSearchResultsChanged(count: Int, current: Int) {}

    // Return true to handle the link yourself.
    public fun onLinkClicked(url: String): Boolean = false
}

// View wrapper around the Compose viewer for XML layouts; the host must be a ComponentActivity (e.g. AppCompatActivity).
public class DocumentView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    public val state: DocumentViewerState = DocumentViewerState()

    // Kotlin property, `setSource(...)` / `getSource()` from Java.
    public var source: DocumentSource? by mutableStateOf(null)

    private var listener: DocumentViewListener? = null

    public var config: DocumentViewerConfig by mutableStateOf(DocumentViewerConfig())

    // null follows the system theme.
    public var colors: DocumentViewerColors? by mutableStateOf(null)

    public enum class Theme { Auto, Light, Dark }

    public var theme: Theme by mutableStateOf(Theme.Auto)

    private val composeView = ComposeView(context).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
    }

    init {
        readAttributes(attrs, defStyleAttr)
        addView(composeView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        composeView.setContent {
            val dark = when (theme) {
                Theme.Auto -> androidx.compose.foundation.isSystemInDarkTheme()
                Theme.Light -> false
                Theme.Dark -> true
            }
            DocumentViewer(
                source = source,
                state = state,
                config = config,
                colors = colors ?: DocumentViewerDefaults.colors(dark),
                onLinkClick = { url -> listener?.onLinkClicked(url) == true },
            )
            LaunchedEffect(Unit) {
                snapshotFlow { state.status }.collect { listener?.onStatusChanged(it) }
            }
            LaunchedEffect(Unit) {
                snapshotFlow { state.currentPage to state.pageCount }.distinctUntilChanged().collect { (page, count) -> listener?.onPageChanged(page, count) }
            }
            LaunchedEffect(Unit) {
                snapshotFlow { state.searchResultCount to state.currentSearchResult }.distinctUntilChanged()
                    .collect { (count, current) -> listener?.onSearchResultsChanged(count, current) }
            }
        }
    }


    @JvmOverloads
    public fun setUri(uri: Uri, format: DocumentFormat? = null) {
        source = DocumentSource.FromUri(uri, format = format)
    }

    @JvmOverloads
    public fun setFile(file: File, format: DocumentFormat? = null) {
        source = DocumentSource.FromFile(file, format)
    }

    @JvmOverloads
    public fun setAsset(path: String, format: DocumentFormat? = null) {
        source = DocumentSource.FromAsset(path, format)
    }

    @JvmOverloads
    public fun setUrl(url: String, headers: Map<String, String> = emptyMap()) {
        source = DocumentSource.FromUrl(url, headers)
    }

    public fun setListener(listener: DocumentViewListener?) {
        this.listener = listener
    }

    public val status: DocumentStatus get() = state.status

    public val currentPage: Int get() = state.currentPage

    public val pageCount: Int get() = state.pageCount

    public fun search(query: String): Unit = state.search(query)

    public fun nextSearchResult(): Unit = state.nextSearchResult()

    public fun previousSearchResult(): Unit = state.previousSearchResult()

    public fun clearSearch(): Unit = state.clearSearch()

    public fun goToPage(page: Int): Unit = state.goToPage(page)

    public fun zoomIn(): Unit = state.zoomIn()

    public fun zoomOut(): Unit = state.zoomOut()

    public fun resetZoom(): Unit = state.resetZoom()

    public fun submitPassword(password: String): Unit = state.submitPassword(password)

    public fun retry(): Unit = state.retry()

    private fun readAttributes(attrs: AttributeSet?, defStyleAttr: Int) {
        val a = context.obtainStyledAttributes(attrs, R.styleable.DocumentView, defStyleAttr, 0)
        try {
            val density = resources.displayMetrics.density
            val scaledDensity = density * resources.configuration.fontScale
            val defaults = DocumentViewerConfig()
            config = DocumentViewerConfig(
                maxZoom = a.getFloat(R.styleable.DocumentView_dv_maxZoom, defaults.maxZoom),
                doubleTapZoom = a.getFloat(R.styleable.DocumentView_dv_doubleTapZoom, defaults.doubleTapZoom),
                pageSpacing = if (a.hasValue(R.styleable.DocumentView_dv_pageSpacing)) (a.getDimension(R.styleable.DocumentView_dv_pageSpacing, 0f) / density).dp else defaults.pageSpacing,
                textSize = if (a.hasValue(R.styleable.DocumentView_dv_textSize)) (a.getDimension(R.styleable.DocumentView_dv_textSize, 0f) / scaledDensity).sp else defaults.textSize,
                textSelectable = a.getBoolean(R.styleable.DocumentView_dv_textSelectable, defaults.textSelectable),
                showPageIndicator = a.getBoolean(R.styleable.DocumentView_dv_showPageIndicator, defaults.showPageIndicator),
                showScrollbar = a.getBoolean(R.styleable.DocumentView_dv_showScrollbar, defaults.showScrollbar),
                pdfNightMode = a.getBoolean(R.styleable.DocumentView_dv_pdfNightMode, defaults.pdfNightMode),
                showCsvRowNumbers = a.getBoolean(R.styleable.DocumentView_dv_csvRowNumbers, defaults.showCsvRowNumbers),
            )
            theme = Theme.entries[a.getInt(R.styleable.DocumentView_dv_theme, 0).coerceIn(0, 2)]
            val customized = listOf(
                R.styleable.DocumentView_dv_accentColor,
                R.styleable.DocumentView_dv_backgroundColor,
                R.styleable.DocumentView_dv_pageColor,
                R.styleable.DocumentView_dv_textColor,
            ).any(a::hasValue)
            if (customized) {
                val night = when (theme) {
                    Theme.Dark -> true
                    Theme.Light -> false
                    Theme.Auto -> (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
                }
                val base = if (night) DocumentViewerDefaults.darkColors() else DocumentViewerDefaults.lightColors()
                fun color(index: Int, fallback: Color) = if (a.hasValue(index)) Color(a.getColor(index, 0)) else fallback
                val accent = color(R.styleable.DocumentView_dv_accentColor, base.accent)
                val text = color(R.styleable.DocumentView_dv_textColor, base.text)
                colors = base.copy(
                    accent = accent,
                    link = accent,
                    background = color(R.styleable.DocumentView_dv_backgroundColor, base.background),
                    page = color(R.styleable.DocumentView_dv_pageColor, base.page),
                    text = text,
                    secondaryText = text.copy(alpha = 0.62f),
                )
            }
            a.getString(R.styleable.DocumentView_dv_source)?.takeIf { it.isNotBlank() }?.let { source = DocumentSource.parse(it) }
        } finally {
            a.recycle()
        }
    }

    override fun onSaveInstanceState(): Parcelable = SavedState(super.onSaveInstanceState()).also {
        it.page = state.currentPage
        it.query = state.searchQuery
    }

    override fun onRestoreInstanceState(parcelable: Parcelable?) {
        val saved = parcelable as? SavedState
        super.onRestoreInstanceState(saved?.superState ?: parcelable)
        if (saved != null) {
            state.goToPage(saved.page)
            if (saved.query.isNotEmpty()) state.search(saved.query)
        }
    }

    private class SavedState : BaseSavedState {
        var page = 0
        var query = ""

        constructor(superState: Parcelable?) : super(superState)

        private constructor(parcel: Parcel) : super(parcel) {
            page = parcel.readInt()
            query = parcel.readString().orEmpty()
        }

        override fun writeToParcel(out: Parcel, flags: Int) {
            super.writeToParcel(out, flags)
            out.writeInt(page)
            out.writeString(query)
        }

        companion object {
            @JvmField
            val CREATOR: Parcelable.Creator<SavedState> = object : Parcelable.Creator<SavedState> {
                override fun createFromParcel(source: Parcel) = SavedState(source)
                override fun newArray(size: Int) = arrayOfNulls<SavedState>(size)
            }
        }
    }
}
