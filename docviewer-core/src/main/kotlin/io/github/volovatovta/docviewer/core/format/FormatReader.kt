package io.github.volovatovta.docviewer.core.format

import io.github.volovatovta.docviewer.core.DocumentTooLargeException
import io.github.volovatovta.docviewer.core.model.DocumentFormat
import java.io.File

/** Recognizes one file format by its content. */
internal interface FormatSignature {
    val format: DocumentFormat

    fun recognizes(file: ProbedFile): Boolean
}

/** Reads one file format. Supporting a new format takes one reader and one line in [Formats]. */
internal interface FormatReader : FormatSignature {
    /** Peak heap per byte of [bytesToParse] while the document is parsed in memory. */
    val peakMemoryPerByte: Int

    /** Bytes the parser works through; container formats count what they unpack. */
    fun bytesToParse(file: File): Long = file.length()

    fun read(request: ReadRequest): DocumentContent
}

internal class ReadRequest(val file: File, fileName: String?, val options: ReadOptions) {
    val name: String = fileName ?: file.name

    /** Refuses a document the memory budget cannot parse, before a single byte of it is parsed. */
    fun requireMemoryFor(reader: FormatReader) {
        val limit = options.maxDocumentSizeBytes ?: options.memoryBudgetBytes?.div(reader.peakMemoryPerByte) ?: return
        val size = reader.bytesToParse(file)
        if (size > limit) throw DocumentTooLargeException(size, limit)
    }
}
