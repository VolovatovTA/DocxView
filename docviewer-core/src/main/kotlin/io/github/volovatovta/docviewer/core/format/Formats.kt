package io.github.volovatovta.docviewer.core.format

import io.github.volovatovta.docviewer.core.doc.DocFormat
import io.github.volovatovta.docviewer.core.docx.DocxFormat
import io.github.volovatovta.docviewer.core.model.DocumentFormat
import io.github.volovatovta.docviewer.core.sheet.xls.XlsFormat
import io.github.volovatovta.docviewer.core.sheet.xlsx.XlsxFormat
import io.github.volovatovta.docviewer.core.text.CsvFormat
import io.github.volovatovta.docviewer.core.text.TextFormat
import java.io.File

/** Every supported format, in the order a file is tried against them. */
internal object Formats {

    // A PDF signature may follow junk, Office files are told apart by what their container holds,
    // and anything that reads like text is plain text unless it reads like CSV.
    private val signatures: List<FormatSignature> = listOf(
        PdfSignature,
        DocxFormat,
        XlsxFormat,
        DocFormat,
        XlsFormat,
        CsvFormat,
        TextFormat,
    )

    private val readers: List<FormatReader> = signatures.filterIsInstance<FormatReader>()

    val recognized: Set<DocumentFormat> = signatures.map { it.format }.toSet()

    fun detect(file: File, hint: DocumentFormat?): DocumentFormat? {
        val probe = ProbedFile(file, hint)
        return signatures.firstOrNull { it.recognizes(probe) }?.format
    }

    fun readerOf(format: DocumentFormat): FormatReader? = readers.firstOrNull { it.format == format }
}
