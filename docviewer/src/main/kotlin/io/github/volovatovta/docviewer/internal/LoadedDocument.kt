package io.github.volovatovta.docviewer.internal

import io.github.volovatovta.docviewer.DocumentInfo
import io.github.volovatovta.docviewer.core.model.FlowDocument
import io.github.volovatovta.docviewer.core.model.TableSource
import io.github.volovatovta.docviewer.core.stream.TextFile
import io.github.volovatovta.docviewer.internal.pdf.PdfDocument
import java.io.Closeable

// An opened document, one kind per viewer.
internal sealed interface LoadedDocument : Closeable {
    val info: DocumentInfo

    class Pdf(val document: PdfDocument, override val info: DocumentInfo) : LoadedDocument {
        override fun close() = document.close()
    }

    // Plain text read from disk keeps its lines, so the viewer can lay them out without parsing the whole file.
    class Flow(
        val document: FlowDocument,
        override val info: DocumentInfo,
        val lines: TextFile? = null
    ) : LoadedDocument {
        override fun close() {
            document.close()
            lines?.close()
        }
    }

    class Grid(val document: TableSource, override val info: DocumentInfo) : LoadedDocument {
        override fun close() = document.close()
    }
}
