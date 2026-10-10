package io.github.volovatovta.docviewer

/** Callbacks of [DocumentView]; every method has a default, so implement only what you need. */
public interface DocumentViewListener {
    public fun onStatusChanged(status: DocumentStatus) {}

    public fun onPageChanged(page: Int, pageCount: Int) {}

    public fun onSearchResultsChanged(count: Int, current: Int) {}

    // Return true to handle the link yourself.
    public fun onLinkClicked(url: String): Boolean = false
}
