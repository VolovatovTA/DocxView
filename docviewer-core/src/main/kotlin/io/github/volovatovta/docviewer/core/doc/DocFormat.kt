package io.github.volovatovta.docviewer.core.doc

import io.github.volovatovta.docviewer.core.format.DocumentContent
import io.github.volovatovta.docviewer.core.format.FormatReader
import io.github.volovatovta.docviewer.core.format.ProbedFile
import io.github.volovatovta.docviewer.core.format.ReadRequest
import io.github.volovatovta.docviewer.core.model.DocumentFormat

/** Word 97–2003: an OLE container with a WordDocument stream. */
internal object DocFormat : FormatReader {
    override val format = DocumentFormat.DOC

    override val peakMemoryPerByte = 8

    // Password-protected Office files of any kind are OLE containers; only their name tells a workbook apart.
    override fun recognizes(file: ProbedFile): Boolean = file.isOle &&
        (WORD_STREAM in file.oleStreams || ENCRYPTED_PACKAGE in file.oleStreams && file.hint?.isSpreadsheet != true)

    override fun read(request: ReadRequest): DocumentContent {
        request.requireMemoryFor(this)
        return DocumentContent.Flow(DocParser.parse(request.file))
    }

    private const val WORD_STREAM = "worddocument"
    private const val ENCRYPTED_PACKAGE = "encryptedpackage"
}
