package io.github.volovatovta.docviewer.internal

import kotlinx.coroutines.flow.Flow

// Implemented by each format-specific viewer; DocumentViewerState delegates navigation and search to it.
internal interface ViewerController {
    // Emits the growing list of hits; PDF search streams page by page.
    fun search(query: String): Flow<List<Any>>

    suspend fun reveal(hit: Any)

    suspend fun scrollToItem(index: Int, animate: Boolean)

    suspend fun zoomBy(factor: Float)

    suspend fun zoomTo(zoom: Float)

    companion object {
        // Past this many matches nobody steps through them one by one, and a list without end runs out of memory.
        const val MAX_SEARCH_HITS = 10_000
    }
}
