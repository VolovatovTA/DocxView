package io.github.volovatovta.docviewer.core

import io.github.volovatovta.docviewer.core.format.Formats
import io.github.volovatovta.docviewer.core.model.DocumentFormat
import java.io.File

/** How much a document costs to parse, so callers can refuse files that would not fit in memory. */
public object ParsingCost {

    /** Bytes the parser works through: the file itself, or what a container format unpacks. */
    public fun bytesToParse(file: File, format: DocumentFormat): Long =
        Formats.readerOf(format)?.bytesToParse(file) ?: file.length()

    /** Peak heap needed per byte of [bytesToParse]; 0 for PDF, which is rendered page by page from disk. */
    public fun peakMemoryPerByte(format: DocumentFormat): Int = Formats.readerOf(format)?.peakMemoryPerByte ?: 0
}
