package io.github.volovatovta.docviewer.internal

import io.github.volovatovta.docviewer.DocumentInfo
import io.github.volovatovta.docviewer.core.format.DocumentContent
import io.github.volovatovta.docviewer.internal.pdf.PdfDocument
import java.io.Closeable

// An opened document: PDF pages drawn by the platform, or content read by docviewer-core.
internal sealed interface LoadedDocument : Closeable {
    val info: DocumentInfo

    class Pdf(val document: PdfDocument, override val info: DocumentInfo) : LoadedDocument {
        override fun close() = document.close()
    }

    class Parsed(val content: DocumentContent, override val info: DocumentInfo) : LoadedDocument {
        override fun close() = content.close()
    }
}
