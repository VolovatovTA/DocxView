package io.github.volovatovta.docviewer.internal.ui

import android.text.format.Formatter
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.volovatovta.docviewer.DocumentViewerColors
import io.github.volovatovta.docviewer.R
import io.github.volovatovta.docviewer.core.CorruptedDocumentException
import io.github.volovatovta.docviewer.core.DocumentTooLargeException
import io.github.volovatovta.docviewer.core.UnsupportedFormatException
import io.github.volovatovta.docviewer.internal.NetworkException
import io.github.volovatovta.docviewer.internal.PasswordUnsupportedException

@Composable
internal fun ErrorView(
    error: Throwable,
    onRetry: () -> Unit,
    colors: DocumentViewerColors,
    modifier: Modifier = Modifier,
) {
    StatusScreen(colors = colors, glyph = { DocumentGlyph(colors) }, modifier = modifier) {
        StatusTitle(stringResource(R.string.dv_error_title), colors)
        StatusMessage(error.message(), colors.secondaryText)
        if (error.isRetryable()) {
            Spacer(Modifier.height(StatusScreenSpec.ActionGap))
            PillButton(stringResource(R.string.dv_retry), colors, onClick = onRetry)
        }
    }
}

@Composable
private fun Throwable.message(): String {
    if (this !is DocumentTooLargeException) return stringResource(messageRes())
    val context = LocalContext.current
    return stringResource(
        R.string.dv_error_too_large,
        Formatter.formatShortFileSize(context, sizeBytes),
        Formatter.formatShortFileSize(context, limitBytes),
    )
}

@StringRes
private fun Throwable.messageRes(): Int = when (this) {
    is PasswordUnsupportedException ->
        if (isPdf) R.string.dv_error_pdf_password_old_android else R.string.dv_error_doc_password
    is UnsupportedFormatException -> R.string.dv_error_unsupported
    is CorruptedDocumentException -> R.string.dv_error_corrupted
    is NetworkException -> R.string.dv_error_network
    else -> R.string.dv_error_io
}

// Reading the same file again cannot fix its format or its contents.
private fun Throwable.isRetryable(): Boolean =
    this !is UnsupportedFormatException && this !is CorruptedDocumentException && this !is DocumentTooLargeException
