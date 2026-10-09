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
}
