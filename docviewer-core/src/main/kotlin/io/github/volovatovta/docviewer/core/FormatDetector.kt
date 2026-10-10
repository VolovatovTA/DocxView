package io.github.volovatovta.docviewer.core

import io.github.volovatovta.docviewer.core.format.Formats
import io.github.volovatovta.docviewer.core.model.DocumentFormat
import java.io.File

public object FormatDetector {

    /** Identifies a file by its content; the name or MIME type only settles text files that could be either. */
    public fun detect(file: File, fileName: String? = null, mimeType: String? = null): DocumentFormat? {
        val extension = fileName?.substringAfterLast('.', "")
        val hint = DocumentFormat.fromExtension(extension) ?: DocumentFormat.fromMimeType(mimeType)
        return Formats.detect(file, hint)
    }
}
