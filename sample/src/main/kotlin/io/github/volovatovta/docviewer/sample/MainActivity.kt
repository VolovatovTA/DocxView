package io.github.volovatovta.docviewer.sample

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.volovatovta.docviewer.DocumentSource
import io.github.volovatovta.docviewer.DocumentStatus
import io.github.volovatovta.docviewer.DocumentViewer
import io.github.volovatovta.docviewer.DocumentViewerConfig
import io.github.volovatovta.docviewer.DocumentViewerDefaults
import io.github.volovatovta.docviewer.rememberDocumentViewerState
import kotlinx.coroutines.delay

private class Sample(val title: String, val subtitle: String, val badge: String, val color: Color, val location: String)

private val samples = listOf(
    Sample("PDF · 41 pages", "Pinch zoom, double tap, fast scroll, search", "PDF", Color(0xFFE5484D), "asset:///sample.pdf"),
    Sample("Word · DOCX", "Styles, lists, a table, a link, an image", "DOCX", Color(0xFF2F6FEB), "asset:///sample.docx"),
    Sample("Word 97 · DOC", "The same report in the legacy binary format", "DOC", Color(0xFF1D3F91), "asset:///sample.doc"),
    Sample("CSV · 2,000 rows", "Windows-1251 and \";\", like an Excel export", "CSV", Color(0xFF12A150), "asset:///sample.csv"),
    Sample("Text · TXT", "A plain text file", "TXT", Color(0xFF6B7280), "asset:///sample.txt"),
)

class MainActivity : ComponentActivity() {
    private var incoming by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) incoming = intent?.data?.toString()
        setContent {
            val dark = isSystemInDarkTheme()
            val accent = if (dark) Color(0xFF7AA7FF) else Color(0xFF2F6FEB)
            MaterialTheme(colorScheme = if (dark) darkColorScheme(primary = accent) else lightColorScheme(primary = accent)) {
                var opened by rememberSaveable { mutableStateOf<String?>(null) }
                LaunchedEffect(incoming) {
                    incoming?.let { opened = it }
                    incoming = null
                }
                val current = opened
                if (current == null) {
                    HomeScreen(onOpen = { opened = it })
                } else {
                    BackHandler { opened = null }
                    ViewerScreen(location = current, onBack = { opened = null })
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        incoming = intent.data?.toString()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(onOpen: (String) -> Unit) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) onOpen(uri.toString())
    }
    var askUrl by remember { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = { LargeTopAppBar(title = { Text("DocViewer SDK") }, scrollBehavior = scrollBehavior) },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    "One component for PDF, DOCX, DOC, CSV and TXT — in Compose and in XML.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            items(samples) { sample -> SampleCard(sample) { onOpen(sample.location) } }
            item {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = {
                            picker.launch(
                                arrayOf(
                                    "application/pdf",
                                    "application/msword",
                                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                    "text/*",
                                ),
                            )
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.Add, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Open file")
                    }
                    OutlinedButton(onClick = { askUrl = true }, modifier = Modifier.weight(1f)) { Text("Open URL") }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { context.startActivity(Intent(context, XmlDemoActivity::class.java)) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("View / XML demo") }
            }
        }
    }
    if (askUrl) {
        var url by remember { mutableStateOf("https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy.pdf") }
        AlertDialog(
            onDismissRequest = { askUrl = false },
            title = { Text("Open a URL") },
            text = { OutlinedTextField(url, { url = it }, singleLine = true, label = { Text("Document URL") }) },
            confirmButton = {
                TextButton(onClick = {
                    askUrl = false
                    onOpen(url.trim())
                }) { Text("Open") }
            },
            dismissButton = { TextButton(onClick = { askUrl = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SampleCard(sample: Sample, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).background(sample.color, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                Text(sample.badge, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(sample.title, style = MaterialTheme.typography.titleMedium)
                Text(sample.subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ViewerScreen(location: String, onBack: () -> Unit) {
    val source = remember(location) { DocumentSource.parse(location) }
    val state = rememberDocumentViewerState()
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var nightPdf by rememberSaveable { mutableStateOf(false) }
    var selectable by rememberSaveable { mutableStateOf(true) }
    var menu by remember { mutableStateOf(false) }
    val ready = state.status as? DocumentStatus.Ready

    // Debounced so that every keystroke does not restart a page-by-page PDF search.
    LaunchedEffect(query, searching) {
        if (!searching) {
            state.clearSearch()
            return@LaunchedEffect
        }
        delay(300)
        state.search(query)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                title = {
                    if (searching) {
                        val focus = remember { FocusRequester() }
                        LaunchedEffect(Unit) { focus.requestFocus() }
                        TextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            placeholder = { Text("Search the document") },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                            modifier = Modifier.fillMaxWidth().focusRequester(focus),
                        )
                    } else {
                        Column {
                            Text(ready?.info?.title ?: ready?.info?.fileName ?: "Document", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            ready?.info?.let {
                                Text(
                                    "${it.format} · ${formatSize(it.sizeBytes)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                },
                actions = {
                    if (searching) {
                        val count = state.searchResultCount
                        Text(
                            when {
                                state.isSearching && count == 0 -> "…"
                                query.isBlank() -> ""
                                else -> "${if (count == 0) 0 else state.currentSearchResult + 1}/$count"
                            },
                            style = MaterialTheme.typography.labelLarge,
                        )
                        IconButton(onClick = state::previousSearchResult, enabled = count > 0) { Icon(Icons.Default.KeyboardArrowUp, "Back") }
                        IconButton(onClick = state::nextSearchResult, enabled = count > 0) { Icon(Icons.Default.KeyboardArrowDown, "Next") }
                        IconButton(onClick = {
                            searching = false
                            query = ""
                        }) { Icon(Icons.Default.Close, "Close search") }
                    } else {
                        if (ready?.info?.isSearchSupported != false) {
                            IconButton(onClick = { searching = true }, enabled = ready != null) { Icon(Icons.Default.Search, "Search") }
                        }
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "More") }
                        DropdownMenu(menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Zoom in") }, onClick = { menu = false; state.zoomIn() })
                            DropdownMenuItem(text = { Text("Zoom out") }, onClick = { menu = false; state.zoomOut() })
                            DropdownMenuItem(text = { Text("Reset zoom") }, onClick = { menu = false; state.resetZoom() })
                            DropdownMenuItem(text = { Text(if (nightPdf) "Normal PDF pages" else "PDF night mode") }, onClick = {
                                menu = false
                                nightPdf = !nightPdf
                            })
                            DropdownMenuItem(text = { Text(if (selectable) "Disable text selection" else "Enable text selection") }, onClick = {
                                menu = false
                                selectable = !selectable
                            })
                            DropdownMenuItem(text = { Text("Go to start") }, onClick = { menu = false; state.goToPage(0) })
                        }
                    }
                },
            )
        },
    ) { padding ->
        DocumentViewer(
            source = source,
            state = state,
            config = DocumentViewerConfig(pdfNightMode = nightPdf, textSelectable = selectable),
            colors = DocumentViewerDefaults.colors(),
            modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding(),
        )
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / 1024f / 1024f)
    bytes >= 1024 -> "${bytes / 1024} KB"
    else -> "$bytes B"
}
