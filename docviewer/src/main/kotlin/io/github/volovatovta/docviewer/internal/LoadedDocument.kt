package io.github.volovatovta.docviewer.internal

import io.github.volovatovta.docviewer.DocumentInfo
import io.github.volovatovta.docviewer.core.model.FlowDocument
import io.github.volovatovta.docviewer.core.model.TableDocument
import io.github.volovatovta.docviewer.internal.pdf.PdfDocument
import java.io.Closeable

// An opened document, one kind per viewer.
internal sealed interface LoadedDocument : Closeable {
    val info: DocumentInfo

    class Pdf(val document: PdfDocument, override val info: DocumentInfo) : LoadedDocument {
        override fun close() = document.close()
    }

    class Flow(val document: FlowDocument, override val info: DocumentInfo) : LoadedDocument {
        override fun close() = document.close()
    }

    class Grid(val document: TableDocument, override val info: DocumentInfo) : LoadedDocument {
        override fun close() = Unit
    }
}
