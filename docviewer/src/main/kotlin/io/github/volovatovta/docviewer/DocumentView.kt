package io.github.volovatovta.docviewer

import android.content.Context
import android.net.Uri
import android.os.Parcel
import android.os.Parcelable
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import io.github.volovatovta.docviewer.core.model.DocumentFormat
import io.github.volovatovta.docviewer.internal.DocumentViewAttributes
import kotlinx.coroutines.flow.distinctUntilChanged
import java.io.File

/** [DocumentViewer] for XML layouts; the host must be a ComponentActivity (e.g. AppCompatActivity). */
// The View twin of DocumentViewerState: every function here is documented API, so their number is by design.
@Suppress("TooManyFunctions")
public class DocumentView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    public enum class Theme { Auto, Light, Dark }

    public val state: DocumentViewerState = DocumentViewerState()

    // Kotlin property, `setSource(...)` / `getSource()` from Java.
    public var source: DocumentSource? by mutableStateOf(null)

    public var config: DocumentViewerConfig by mutableStateOf(DocumentViewerConfig())

    // null follows the system theme.
    public var colors: DocumentViewerColors? by mutableStateOf(null)

    public var theme: Theme by mutableStateOf(Theme.Auto)

    public val status: DocumentStatus get() = state.status

    public val currentPage: Int get() = state.currentPage

    public val pageCount: Int get() = state.pageCount

    private var listener: DocumentViewListener? = null

    private val composeView = ComposeView(context).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
    }

    init {
        val attributes = DocumentViewAttributes.read(this, attrs, defStyleAttr)
        config = attributes.config
        theme = attributes.theme
        colors = attributes.colors
        source = attributes.source
        addView(composeView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        composeView.setContent { Content() }
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

    @Composable
    private fun Content() {
        val dark = when (theme) {
            Theme.Auto -> isSystemInDarkTheme()
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
        NotifyListener()
    }

    // Forwards state changes to the listener, which plain View code cannot observe as Compose state.
    @Composable
    private fun NotifyListener() {
        LaunchedEffect(state) {
            snapshotFlow { state.status }.collect { listener?.onStatusChanged(it) }
        }
        LaunchedEffect(state) {
            snapshotFlow { state.currentPage to state.pageCount }
                .distinctUntilChanged()
                .collect { (page, count) -> listener?.onPageChanged(page, count) }
        }
        LaunchedEffect(state) {
            snapshotFlow { state.searchResultCount to state.currentSearchResult }
                .distinctUntilChanged()
                .collect { (count, current) -> listener?.onSearchResultsChanged(count, current) }
        }
    }

    override fun onSaveInstanceState(): Parcelable = SavedState(super.onSaveInstanceState()).also {
        it.page = state.currentPage
        it.query = state.searchQuery
    }

    override fun onRestoreInstanceState(parcelable: Parcelable?) {
        val saved = parcelable as? SavedState
        super.onRestoreInstanceState(saved?.superState ?: parcelable)
        if (saved == null) return
        state.goToPage(saved.page)
        if (saved.query.isNotEmpty()) state.search(saved.query)
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
