package io.github.volovatovta.docviewer.core.format

import io.github.volovatovta.docviewer.core.binary.startsWith
import io.github.volovatovta.docviewer.core.model.DocumentFormat

/** PDF is only recognized here; drawing its pages takes a platform renderer, not a parser. */
internal object PdfSignature : FormatSignature {
    override val format = DocumentFormat.PDF

    private val signature = "%PDF".toByteArray()

    // The specification allows garbage before the header within the first kilobyte.
    override fun recognizes(file: ProbedFile): Boolean =
        file.header.indices.any { offset -> file.header.startsWith(signature, at = offset) }
}
