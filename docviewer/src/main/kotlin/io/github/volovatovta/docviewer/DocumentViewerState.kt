package io.github.volovatovta.docviewer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import io.github.volovatovta.docviewer.core.model.DocumentFormat
import io.github.volovatovta.docviewer.internal.ViewerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

public sealed interface DocumentStatus {
    public data object Idle : DocumentStatus

    // progress is 0..1 while the file is being downloaded or copied, null when unknown.
    public data class Loading(val progress: Float? = null) : DocumentStatus

    public data class Ready(val info: DocumentInfo) : DocumentStatus

    public data class PasswordRequired(val wrongPassword: Boolean) : DocumentStatus

    public data class Failed(val error: Throwable) : DocumentStatus
}

@Immutable
public data class DocumentInfo(
    val format: DocumentFormat,
    val fileName: String?,
    val title: String?,
    // Pages for PDF, rows for CSV, top-level blocks for text documents.
    val itemCount: Int,
    val sizeBytes: Long,
    val isSearchSupported: Boolean,
)

@Stable
public class DocumentViewerState(initialPage: Int = 0, initialZoom: Float = 1f) {

    public var status: DocumentStatus by mutableStateOf(DocumentStatus.Idle)
        internal set

    public val info: DocumentInfo? get() = (status as? DocumentStatus.Ready)?.info

    // Zero-based page for PDF; for other formats the first visible block or row.
    public var currentPage: Int by mutableIntStateOf(initialPage)
        internal set

    public val pageCount: Int get() = info?.itemCount ?: 0

    // PDF zoom or text scale for reflowable documents.
    public var zoom: Float by mutableFloatStateOf(initialZoom)
        internal set

    public var searchQuery: String by mutableStateOf("")
        private set

    public var searchResultCount: Int by mutableIntStateOf(0)
        private set

    // Zero-based index of the highlighted match, -1 when there is none.
    public var currentSearchResult: Int by mutableIntStateOf(-1)
        private set

    public var isSearching: Boolean by mutableStateOf(false)
        private set

    internal var searchHits: List<Any> by mutableStateOf(emptyList())
        private set

    internal var password: String? by mutableStateOf(null)
    internal var reloadToken: Int by mutableIntStateOf(0)
    internal var pendingPage: Int? = initialPage.takeIf { it > 0 }
    internal var controller: ViewerController? = null
        set(value) {
            field = value
            // A viewer recreated for the same document (rotation, resize) keeps its hits and position.
            if (value != null && searchQuery.isNotEmpty() && searchHits.isEmpty() && !isSearching) search(searchQuery)
        }
    internal var scope: CoroutineScope? = null
    internal var lastSource: DocumentSource? = null
    private var searchJob: Job? = null
    private var navigationJob: Job? = null

    public fun search(query: String) {
        searchQuery = query
        searchJob?.cancel()
        searchHits = emptyList()
        searchResultCount = 0
        currentSearchResult = -1
        val controller = controller ?: return
        val scope = scope ?: return
        if (query.isBlank()) {
            isSearching = false
            return
        }
        isSearching = true
        searchJob = scope.launch {
            val self = coroutineContext[Job]
            try {
                controller.search(query).collect { hits ->
                    val first = searchHits.isEmpty() && hits.isNotEmpty()
                    searchHits = hits
                    searchResultCount = hits.size
                    if (first) {
                        currentSearchResult = 0
                        controller.reveal(hits[0])
                    }
                }
            } finally {
                if (searchJob === self) isSearching = false
            }
        }
    }

    public fun nextSearchResult(): Unit = moveSearch(+1)

    public fun previousSearchResult(): Unit = moveSearch(-1)

    public fun clearSearch(): Unit = search("")

    private fun moveSearch(delta: Int) {
        val hits = searchHits
        if (hits.isEmpty()) return
        val index = Math.floorMod(currentSearchResult + delta, hits.size)
        currentSearchResult = index
        navigate { controller?.reveal(hits[index]) }
    }

    public suspend fun scrollToPage(page: Int, animate: Boolean = true) {
        val controller = controller
        if (controller == null) {
            pendingPage = page
            return
        }
        controller.scrollToItem(page.coerceIn(0, (pageCount - 1).coerceAtLeast(0)), animate)
    }

    // Fire-and-forget variant for click listeners and Java callers.
    public fun goToPage(page: Int) {
        if (controller == null) {
            pendingPage = page
            return
        }
        navigate { scrollToPage(page) }
    }

    public fun zoomIn(): Unit = navigate { controller?.zoomBy(1.5f) }

    public fun zoomOut(): Unit = navigate { controller?.zoomBy(1f / 1.5f) }

    public fun resetZoom(): Unit = navigate { controller?.zoomTo(1f) }

    public fun submitPassword(password: String) {
        this.password = password
        reloadToken++
    }

    public fun retry() {
        reloadToken++
    }

    private fun navigate(block: suspend () -> Unit) {
        val scope = scope ?: return
        navigationJob?.cancel()
        navigationJob = scope.launch { block() }
    }

    // Page a freshly composed viewer should start at; consumed once.
    internal fun consumeInitialPage(): Int {
        val page = pendingPage ?: currentPage
        pendingPage = null
        return page
    }

    internal fun onSourceChanged(source: DocumentSource) {
        if (lastSource != null && lastSource != source) {
            password = null
            currentPage = 0
            pendingPage = null
            zoom = 1f
        }
        lastSource = source
    }

    internal fun resetForNewDocument() {
        searchJob?.cancel()
        searchHits = emptyList()
        searchResultCount = 0
        currentSearchResult = -1
        isSearching = false
        controller = null
    }

    public companion object {
        public val Saver: Saver<DocumentViewerState, List<Any>> = Saver(
            save = { listOf(it.currentPage, it.zoom, it.searchQuery) },
            restore = { saved ->
                DocumentViewerState(saved[0] as Int, saved[1] as Float).also { it.searchQuery = saved[2] as String }
            },
        )
    }
}

@Composable
public fun rememberDocumentViewerState(initialPage: Int = 0): DocumentViewerState =
    rememberSaveable(saver = DocumentViewerState.Saver) { DocumentViewerState(initialPage) }
