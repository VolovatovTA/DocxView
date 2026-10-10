package io.github.volovatovta.docviewer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import io.github.volovatovta.docviewer.internal.ViewerController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch

/** Observable state of a [DocumentViewer] and the handle to search, navigate and zoom it. */
// The public facade of the viewer: every function here is documented API, so their number is by design.
@Suppress("TooManyFunctions")
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
    public var currentSearchResult: Int by mutableIntStateOf(NO_RESULT)
        private set

    public var isSearching: Boolean by mutableStateOf(false)
        private set

    internal var searchHits: List<Any> by mutableStateOf(emptyList())
        private set

    internal var password: String? by mutableStateOf(null)
    internal var reloadToken: Int by mutableIntStateOf(0)
    internal var pendingPage: Int? = initialPage.takeIf { it > 0 }
    internal var scope: CoroutineScope? = null
    internal var controller: ViewerController? = null
        set(value) {
            field = value
            if (value != null && searchNeedsRerun) search(searchQuery)
        }

    // A viewer recreated for the same document (rotation, resize) keeps its hits; a restored query has none yet.
    private val searchNeedsRerun: Boolean get() = searchQuery.isNotEmpty() && searchHits.isEmpty() && !isSearching

    private var lastSource: DocumentSource? = null
    private var searchJob: Job? = null

    // Scrolling and zooming are independent: a zoom right after goToPage must not cancel the scroll.
    private var scrollJob: Job? = null
    private var zoomJob: Job? = null

    public fun search(query: String) {
        searchQuery = query
        searchJob?.cancel()
        clearHits()
        val controller = controller ?: return
        val scope = scope ?: return
        isSearching = query.isNotBlank()
        if (isSearching) searchJob = scope.launch { collectHits(controller, query) }
    }

    public fun nextSearchResult(): Unit = moveSearch(+1)

    public fun previousSearchResult(): Unit = moveSearch(-1)

    public fun clearSearch(): Unit = search("")

    public suspend fun scrollToPage(page: Int, animate: Boolean = true) {
        val controller = controller
        if (controller == null) {
            pendingPage = page
            return
        }
        // Each viewer clamps to its own length: a workbook's sheets differ from the first sheet's row count.
        controller.scrollToItem(page.coerceAtLeast(0), animate)
    }

    // Fire-and-forget variant for click listeners and Java callers.
    public fun goToPage(page: Int) {
        if (controller == null) pendingPage = page else scrollJob = relaunch(scrollJob) { scrollToPage(page) }
    }

    public fun zoomIn(): Unit = zoom { controller?.zoomBy(ZOOM_STEP) }

    public fun zoomOut(): Unit = zoom { controller?.zoomBy(1f / ZOOM_STEP) }

    public fun resetZoom(): Unit = zoom { controller?.zoomTo(1f) }

    public fun submitPassword(password: String) {
        this.password = password
        reloadToken++
    }

    public fun retry() {
        reloadToken++
    }

    // Page a freshly composed viewer should start at; consumed once.
    internal fun consumeInitialPage(): Int {
        val page = pendingPage ?: currentPage
        pendingPage = null
        return page
    }

    // A new source starts from the top, unzoomed and without the previous document's password.
    internal fun beginLoading(source: DocumentSource) {
        resetForNewDocument()
        if (lastSource != null && lastSource != source) {
            password = null
            currentPage = 0
            pendingPage = null
            zoom = 1f
        }
        lastSource = source
        status = DocumentStatus.Loading()
    }

    internal fun resetForNewDocument() {
        searchJob?.cancel()
        clearHits()
        isSearching = false
        controller = null
    }

    // A search that fails on a damaged part of the file keeps the hits found so far instead of crashing the app.
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private suspend fun collectHits(controller: ViewerController, query: String) {
        val self = currentCoroutineContext()[Job]
        try {
            controller.search(query).collect { hits -> showHits(hits, controller) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (searchJob === self) isSearching = false
        } finally {
            if (searchJob === self) isSearching = false
        }
    }

    // The first hits found are revealed right away; later batches only extend the list.
    private suspend fun showHits(hits: List<Any>, controller: ViewerController) {
        val isFirstBatch = searchHits.isEmpty() && hits.isNotEmpty()
        searchHits = hits
        searchResultCount = hits.size
        if (isFirstBatch) {
            currentSearchResult = 0
            controller.reveal(hits.first())
        }
    }

    private fun clearHits() {
        searchHits = emptyList()
        searchResultCount = 0
        currentSearchResult = NO_RESULT
    }

    private fun moveSearch(delta: Int) {
        val hits = searchHits
        if (hits.isEmpty()) return
        val index = Math.floorMod(currentSearchResult + delta, hits.size)
        currentSearchResult = index
        scrollJob = relaunch(scrollJob) { controller?.reveal(hits[index]) }
    }

    private fun zoom(block: suspend () -> Unit) {
        zoomJob = relaunch(zoomJob, block)
    }

    // A newer request of the same kind replaces the one still running.
    private fun relaunch(previous: Job?, block: suspend () -> Unit): Job? {
        val scope = scope ?: return previous
        previous?.cancel()
        return scope.launch { block() }
    }

    public companion object {
        private const val ZOOM_STEP = 1.5f
        private const val NO_RESULT = -1

        public val Saver: Saver<DocumentViewerState, List<Any>> = Saver(
            save = { listOf(it.currentPage, it.zoom, it.searchQuery) },
            restore = { (page, zoom, query) ->
                DocumentViewerState(page as Int, zoom as Float).also { it.searchQuery = query as String }
            },
        )
    }
}

@Composable
public fun rememberDocumentViewerState(initialPage: Int = 0): DocumentViewerState =
    rememberSaveable(saver = DocumentViewerState.Saver) { DocumentViewerState(initialPage) }
