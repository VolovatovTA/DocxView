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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import io.github.volovatovta.docviewer.core.PasswordProtectedException
import io.github.volovatovta.docviewer.core.format.DocumentContent
import io.github.volovatovta.docviewer.internal.DocumentSession
import io.github.volovatovta.docviewer.internal.LoadedDocument
import io.github.volovatovta.docviewer.internal.RetainedFiles
import io.github.volovatovta.docviewer.internal.flow.FlowViewer
import io.github.volovatovta.docviewer.internal.grid.WorkbookViewer
import io.github.volovatovta.docviewer.internal.isChangingConfigurations
import io.github.volovatovta.docviewer.internal.pdf.PdfViewer
import io.github.volovatovta.docviewer.internal.ui.ErrorView
import io.github.volovatovta.docviewer.internal.ui.LoadingView
import io.github.volovatovta.docviewer.internal.ui.PasswordView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.UUID

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
    passwordPrompt: @Composable (wrongPassword: Boolean, submit: (String) -> Unit) -> Unit = { wrong, submit ->
        PasswordView(wrong, submit, colors)
    },
) {
    val session = rememberDocumentSession(source, state, config)
    val openLink = rememberLinkOpener(onLinkClick)
    Box(modifier.background(colors.background)) {
        AnimatedContent(
            targetState = state.status.pane,
            transitionSpec = { StatusTransition.FadeIn togetherWith StatusTransition.FadeOut },
            label = "documentStatus",
            modifier = Modifier.fillMaxSize(),
        ) { pane ->
            val status = state.status
            when (pane) {
                Pane.Loading -> if (source != null) loading((status as? DocumentStatus.Loading)?.progress)
                Pane.Document -> session?.document?.let { OpenedDocument(it, state, config, colors, openLink) }
                Pane.Password -> {
                    val wrongPassword = (status as? DocumentStatus.PasswordRequired)?.wrongPassword == true
                    passwordPrompt(wrongPassword, state::submitPassword)
                }
                Pane.Error -> (status as? DocumentStatus.Failed)?.let { error(it.error, state::retry) }
            }
        }
    }
}

private enum class Pane { Loading, Document, Password, Error }

private val DocumentStatus.pane: Pane
    get() = when (this) {
        DocumentStatus.Idle, is DocumentStatus.Loading -> Pane.Loading
        is DocumentStatus.Ready -> Pane.Document
        is DocumentStatus.PasswordRequired -> Pane.Password
        is DocumentStatus.Failed -> Pane.Error
    }

private object StatusTransition {
    private const val FADE_IN_MILLIS = 220
    private const val FADE_OUT_MILLIS = 120
    val FadeIn = fadeIn(tween(FADE_IN_MILLIS))
    val FadeOut = fadeOut(tween(FADE_OUT_MILLIS))
}

@Composable
private fun OpenedDocument(
    document: LoadedDocument,
    state: DocumentViewerState,
    config: DocumentViewerConfig,
    colors: DocumentViewerColors,
    onLink: (String) -> Unit,
) {
    when (document) {
        is LoadedDocument.Pdf -> PdfViewer(document.document, state, config, colors)
        is LoadedDocument.Parsed -> when (val content = document.content) {
            is DocumentContent.Flow -> FlowViewer(content.document, content.lines, state, config, colors, onLink)
            is DocumentContent.Sheets -> WorkbookViewer(content.workbook, state, config, colors)
        }
    }
}

// Loads the source whenever it changes or the state asks for a reload (retry, password).
@Composable
private fun rememberDocumentSession(
    source: DocumentSource?,
    state: DocumentViewerState,
    config: DocumentViewerConfig,
): DocumentSession? {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    SideEffect { state.scope = scope }
    val activityContext = LocalContext.current
    // Rotation recreates the composition; the downloaded or copied file is handed over instead of fetched again.
    val retainKey = rememberSaveable { UUID.randomUUID().toString() }
    val session = remember(source) {
        source?.let { DocumentSession(context, it, RetainedFiles.take(retainKey, it)) }
    }
    val currentConfig by rememberUpdatedState(config)
    DisposableEffect(session) {
        // Search is stopped before the files it reads are closed.
        onDispose {
            state.resetForNewDocument()
            if (session != null && source != null && activityContext.isChangingConfigurations) {
                session.detach()?.let { RetainedFiles.park(retainKey, source, it) }
            } else {
                session?.close()
            }
        }
    }
    LaunchedEffect(session, state.reloadToken) {
        if (source == null || session == null) {
            state.status = DocumentStatus.Idle
            return@LaunchedEffect
        }
        session.closeDocument()
        state.beginLoading(source)
        val effect = this
        state.status = statusOfLoading {
            // Progress is posted through the effect, so a cancelled load can never overwrite a newer status.
            val file = session.resolve { progress -> effect.launch { state.status = DocumentStatus.Loading(progress) } }
            state.status = DocumentStatus.Loading()
            DocumentStatus.Ready(session.open(file, state.password, currentConfig).info)
        }
    }
    return session
}

// Untrusted files can break a parser in unforeseeable ways; that must end on the error screen, never in a crash.
@Suppress("TooGenericExceptionCaught")
private suspend fun statusOfLoading(load: suspend () -> DocumentStatus): DocumentStatus = try {
    load()
} catch (e: CancellationException) {
    throw e
} catch (e: PasswordProtectedException) {
    DocumentStatus.PasswordRequired(e.wrongPassword)
} catch (e: Throwable) {
    DocumentStatus.Failed(e)
}

@Composable
private fun rememberLinkOpener(onLinkClick: ((String) -> Boolean)?): (String) -> Unit {
    val uriHandler = LocalUriHandler.current
    val currentOnLinkClick by rememberUpdatedState(onLinkClick)
    return remember(uriHandler) {
        {
                url ->
            if (currentOnLinkClick?.invoke(url) != true) uriHandler.openSafely(url)
        }
    }
}

// No app for the link or a malformed URI is not worth crashing the viewer over.
private fun UriHandler.openSafely(url: String) {
    try {
        openUri(url)
    } catch (_: ActivityNotFoundException) {
    } catch (_: IllegalArgumentException) {
    }
}
