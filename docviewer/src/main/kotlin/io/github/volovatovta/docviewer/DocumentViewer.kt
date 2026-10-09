package io.github.volovatovta.docviewer

import android.content.ActivityNotFoundException
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import io.github.volovatovta.docviewer.core.PasswordProtectedException
import io.github.volovatovta.docviewer.internal.DocumentLoader
import io.github.volovatovta.docviewer.internal.LoadedDocument
import io.github.volovatovta.docviewer.internal.ResolvedFile
import io.github.volovatovta.docviewer.internal.flow.FlowViewer
import io.github.volovatovta.docviewer.internal.grid.GridViewer
import io.github.volovatovta.docviewer.internal.pdf.PdfViewer
import io.github.volovatovta.docviewer.internal.ui.ErrorView
import io.github.volovatovta.docviewer.internal.ui.LoadingView
import io.github.volovatovta.docviewer.internal.ui.PasswordView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Shows a PDF, DOCX, DOC, CSV/TSV or plain-text document; a remembered [state] drives search, navigation and zoom. */
@Composable
public fun DocumentViewer(
    source: DocumentSource?,
    modifier: Modifier = Modifier,
    state: DocumentViewerState = rememberDocumentViewerState(),
    config: DocumentViewerConfig = DocumentViewerConfig(),
    colors: DocumentViewerColors = DocumentViewerDefaults.colors(),
    // Return true to consume the click; otherwise the link opens in the system browser.
    onLinkClick: ((String) -> Boolean)? = null,
    loading: @Composable (progress: Float?) -> Unit = { LoadingView(it, colors) },
    error: @Composable (error: Throwable, retry: () -> Unit) -> Unit = { e, retry -> ErrorView(e, retry, colors) },
    passwordPrompt: @Composable (wrongPassword: Boolean, submit: (String) -> Unit) -> Unit = { wrong, submit -> PasswordView(wrong, submit, colors) },
) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    SideEffect { state.scope = scope }

    var resolved by remember(source) { mutableStateOf<ResolvedFile?>(null) }
    var document by remember(source) { mutableStateOf<LoadedDocument?>(null) }
    val currentConfig by rememberUpdatedState(config)

    DisposableEffect(source) {
        onDispose {
            document?.close()
            resolved?.release()
            state.resetForNewDocument()
        }
    }

    LaunchedEffect(source, state.reloadToken) {
        if (source == null) {
            state.status = DocumentStatus.Idle
            return@LaunchedEffect
        }
        document?.close()
        document = null
        state.resetForNewDocument()
        state.onSourceChanged(source)
        state.status = DocumentStatus.Loading()
        val effectScope = this
        try {
            // Progress is posted through the effect scope so a cancelled load can never overwrite a newer status.
            // Downloads stay cancellable; a copy orphaned by a late cancellation is swept from the cache within a day.
            val file = resolved ?: DocumentLoader.resolve(context, source) { progress ->
                effectScope.launch { state.status = DocumentStatus.Loading(progress) }
            }.also { resolved = it }
            state.status = DocumentStatus.Loading()
            // Opening is not cancellable midway, so a result that arrives after cancellation is closed here instead of leaking.
            val loaded = withContext(NonCancellable) { DocumentLoader.open(file, source, state.password, currentConfig.csvOptions) }
            if (!isActive) {
                loaded.close()
                throw CancellationException()
            }
            document = loaded
            state.status = DocumentStatus.Ready(loaded.info)
        } catch (e: CancellationException) {
            throw e
        } catch (e: PasswordProtectedException) {
            state.status = DocumentStatus.PasswordRequired(e.wrongPassword)
        } catch (e: Throwable) {
            state.status = DocumentStatus.Failed(e)
        }
    }

    val uriHandler = LocalUriHandler.current
    val openLink: (String) -> Unit = { url ->
        if (onLinkClick?.invoke(url) != true) {
            try {
                uriHandler.openUri(url)
            } catch (_: ActivityNotFoundException) {
            } catch (_: IllegalArgumentException) {
            }
        }
    }

    Box(modifier.background(colors.background)) {
        val status = state.status
        val key = when (status) {
            is DocumentStatus.Loading, DocumentStatus.Idle -> 0
            is DocumentStatus.Ready -> 1
            is DocumentStatus.PasswordRequired -> 2
            is DocumentStatus.Failed -> 3
        }
        AnimatedContent(
            targetState = key,
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) },
            label = "documentStatus",
            modifier = Modifier.fillMaxSize(),
        ) { target ->
            when (target) {
                0 -> if (source != null) loading((state.status as? DocumentStatus.Loading)?.progress)
                1 -> when (val doc = document) {
                    is LoadedDocument.Pdf -> PdfViewer(doc.document, state, config, colors)
                    is LoadedDocument.Flow -> FlowViewer(doc.document, state, config, colors, openLink)
                    is LoadedDocument.Grid -> GridViewer(doc.document, state, config, colors)
                    null -> Unit
                }
                2 -> passwordPrompt((state.status as? DocumentStatus.PasswordRequired)?.wrongPassword == true) { state.submitPassword(it) }
                else -> (state.status as? DocumentStatus.Failed)?.let { error(it.error) { state.retry() } }
            }
        }
    }
}
