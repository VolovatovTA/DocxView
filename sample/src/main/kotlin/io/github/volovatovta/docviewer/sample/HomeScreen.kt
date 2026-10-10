package io.github.volovatovta.docviewer.sample

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext

private const val DemoUrl = "https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy.pdf"

private val PickableMimeTypes = arrayOf(
    "application/pdf",
    "application/msword",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "text/*",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeScreen(onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    var askingForUrl by rememberSaveable { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = { LargeTopAppBar(title = { Text("DocxView") }, scrollBehavior = scrollBehavior) },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = SampleDimens.ScreenPadding,
                end = SampleDimens.ScreenPadding,
                top = padding.calculateTopPadding() + SampleDimens.SectionGap,
                bottom = padding.calculateBottomPadding() + SampleDimens.ListBottomPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(SampleDimens.ItemSpacing),
        ) {
            item {
                Text(
                    text = "One component for PDF, DOCX, DOC, CSV and TXT — in Compose and in XML.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = SampleDimens.SectionGap),
                )
            }
            items(BundledSamples) { sample -> SampleCard(sample, onClick = { onOpen(sample.location) }) }
            item { OpenActions(onOpen = onOpen, onAskForUrl = { askingForUrl = true }) }
        }
    }
    if (askingForUrl) {
        OpenUrlDialog(
            onOpen = { url ->
                askingForUrl = false
                onOpen(url)
            },
            onDismiss = { askingForUrl = false },
        )
    }
}

@Composable
private fun OpenActions(onOpen: (String) -> Unit, onAskForUrl: () -> Unit) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) onOpen(uri.toString())
    }
    Column(
        modifier = Modifier.padding(top = SampleDimens.SectionGap),
        verticalArrangement = Arrangement.spacedBy(SampleDimens.ItemSpacing),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(SampleDimens.ItemSpacing)) {
            OutlinedButton(onClick = { picker.launch(PickableMimeTypes) }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(SampleDimens.SmallGap))
                Text("Open file")
            }
            OutlinedButton(onClick = onAskForUrl, modifier = Modifier.weight(1f)) { Text("Open URL") }
        }
        OutlinedButton(
            onClick = { context.startActivity(Intent(context, XmlDemoActivity::class.java)) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("View / XML demo")
        }
    }
}

@Composable
private fun OpenUrlDialog(onOpen: (String) -> Unit, onDismiss: () -> Unit) {
    var url by remember { mutableStateOf(DemoUrl) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Open a URL") },
        text = {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                singleLine = true,
                label = { Text("Document URL") },
            )
        },
        confirmButton = { TextButton(onClick = { onOpen(url.trim()) }) { Text("Open") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
