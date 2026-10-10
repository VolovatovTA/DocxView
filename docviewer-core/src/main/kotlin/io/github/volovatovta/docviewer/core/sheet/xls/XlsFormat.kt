package io.github.volovatovta.docviewer.core.sheet.xls

import io.github.volovatovta.docviewer.core.format.DocumentContent
import io.github.volovatovta.docviewer.core.format.FormatReader
import io.github.volovatovta.docviewer.core.format.ProbedFile
import io.github.volovatovta.docviewer.core.format.ReadRequest
import io.github.volovatovta.docviewer.core.model.DocumentFormat
import io.github.volovatovta.docviewer.core.ole.CompoundFile

/** Excel 97–2003: an OLE container with a BIFF8 Workbook stream. */
internal object XlsFormat : FormatReader {
    override val format = DocumentFormat.XLS

    override val peakMemoryPerByte = 8

    // A password-protected .xlsx is an OLE container too; DocFormat leaves it here when the name says spreadsheet.
    override fun recognizes(file: ProbedFile): Boolean = file.isOle &&
        (WORKBOOK in file.oleStreams || LEGACY_BOOK in file.oleStreams || ENCRYPTED_PACKAGE in file.oleStreams)

    override fun read(request: ReadRequest): DocumentContent {
        request.requireMemoryFor(this)
        val stream = CompoundFile(request.file.readBytes()).workbookStream()
        return DocumentContent.Sheets(XlsWorkbook(stream).read())
    }

    private const val WORKBOOK = "workbook"
    private const val LEGACY_BOOK = "book"
    private const val ENCRYPTED_PACKAGE = "encryptedpackage"
}
