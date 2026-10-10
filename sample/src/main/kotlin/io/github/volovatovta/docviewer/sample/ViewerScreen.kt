package io.github.volovatovta.docviewer.sample

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import io.github.volovatovta.docviewer.DocumentInfo
import io.github.volovatovta.docviewer.DocumentSource
import io.github.volovatovta.docviewer.DocumentViewer
import io.github.volovatovta.docviewer.DocumentViewerConfig
import io.github.volovatovta.docviewer.DocumentViewerState
import io.github.volovatovta.docviewer.rememberDocumentViewerState
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

// Debounced so that every keystroke does not restart a page-by-page PDF search.
private val SearchDebounce = 300.milliseconds

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ViewerScreen(location: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val source = remember(location) { DocumentSource.parse(location) }
    val state = rememberDocumentViewerState()
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var nightPdf by rememberSaveable { mutableStateOf(false) }
    var selectable by rememberSaveable { mutableStateOf(true) }

    DebouncedSearch(state, query, active = searching)

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                title = {
                    if (searching) SearchField(query, onQueryChange = { query = it }) else DocumentTitle(state.info)
                },
                actions = {
                    if (searching) {
                        SearchActions(
                            state = state,
                            queryIsBlank = query.isBlank(),
                            onClose = {
                                searching = false
                                query = ""
                            },
                        )
                    } else {
                        ViewerActions(
                            state = state,
                            onSearch = { searching = true },
                            nightPdf = nightPdf,
                            onToggleNightPdf = { nightPdf = !nightPdf },
                            selectable = selectable,
                            onToggleSelectable = { selectable = !selectable },
                        )
                    }
                },
            )
        },
    ) { padding ->
        DocumentViewer(
            source = source,
            state = state,
            config = DocumentViewerConfig(pdfNightMode = nightPdf, textSelectable = selectable),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding(),
        )
    }
}

// Waits for a pause in typing so a page-by-page PDF search is not restarted on every keystroke.
@Composable
private fun DebouncedSearch(state: DocumentViewerState, query: String, active: Boolean) {
    LaunchedEffect(query, active) {
        if (active) {
            delay(SearchDebounce)
            state.search(query)
        } else {
            state.clearSearch()
        }
    }
}

@Composable
private fun DocumentTitle(info: DocumentInfo?) {
    Column {
        Text(info?.title ?: info?.fileName ?: "Document", maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (info != null) {
            Text(
                text = "${info.format} · ${formatSize(info.sizeBytes)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(focus) { focus.requestFocus() }
    TextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        placeholder = { Text("Search the document") },
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focus),
    )
}

@Composable
private fun RowScope.SearchActions(state: DocumentViewerState, queryIsBlank: Boolean, onClose: () -> Unit) {
    val count = state.searchResultCount
    Text(searchCounter(state, queryIsBlank), style = MaterialTheme.typography.labelLarge)
    IconButton(onClick = state::previousSearchResult, enabled = count > 0) {
        Icon(Icons.Default.KeyboardArrowUp, "Previous match")
    }
    IconButton(onClick = state::nextSearchResult, enabled = count > 0) {
        Icon(Icons.Default.KeyboardArrowDown, "Next match")
    }
    IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close search") }
}

private fun searchCounter(state: DocumentViewerState, queryIsBlank: Boolean): String {
    val count = state.searchResultCount
    return when {
        state.isSearching && count == 0 -> "…"
        queryIsBlank -> ""
        count == 0 -> "0/0"
        else -> "${state.currentSearchResult + 1}/$count"
    }
}

@Composable
private fun RowScope.ViewerActions(
    state: DocumentViewerState,
    onSearch: () -> Unit,
    nightPdf: Boolean,
    onToggleNightPdf: () -> Unit,
    selectable: Boolean,
    onToggleSelectable: () -> Unit,
) {
    val info = state.info
    if (info?.isSearchSupported != false) {
        IconButton(onClick = onSearch, enabled = info != null) { Icon(Icons.Default.Search, "Search") }
    }
    var menuOpen by remember { mutableStateOf(false) }
    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, "More") }
    val closeMenu = { menuOpen = false }
    DropdownMenu(menuOpen, onDismissRequest = closeMenu) {
        MenuItem("Zoom in", closeMenu, state::zoomIn)
        MenuItem("Zoom out", closeMenu, state::zoomOut)
        MenuItem("Reset zoom", closeMenu, state::resetZoom)
        MenuItem(if (nightPdf) "Normal PDF pages" else "PDF night mode", closeMenu, onToggleNightPdf)
        MenuItem(if (selectable) "Disable text selection" else "Enable text selection", closeMenu, onToggleSelectable)
        MenuItem("Go to start", closeMenu) { state.goToPage(0) }
    }
}

// Every item closes the menu before acting.
@Composable
private fun MenuItem(label: String, onClose: () -> Unit, action: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label) },
        onClick = {
            onClose()
            action()
        },
    )
}

private fun formatSize(bytes: Long): String = when {
    bytes >= BytesPerMb -> "%.1f MB".format(bytes.toFloat() / BytesPerMb)
    bytes >= BytesPerKb -> "${bytes / BytesPerKb} KB"
    else -> "$bytes B"
}

private const val BytesPerKb = 1024L
private const val BytesPerMb = BytesPerKb * BytesPerKb
