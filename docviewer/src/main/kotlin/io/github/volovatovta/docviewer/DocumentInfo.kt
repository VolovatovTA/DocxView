package io.github.volovatovta.docviewer

import androidx.compose.runtime.Immutable
import io.github.volovatovta.docviewer.core.model.DocumentFormat

/** What is known about an opened document. */
@Immutable
public data class DocumentInfo(
    val format: DocumentFormat,
    val fileName: String?,
    val title: String?,
    // Pages for PDF, rows for CSV, top-level blocks for text documents.
    val itemCount: Int,
    val sizeBytes: Long,
    val isSearchSupported: Boolean,
)
