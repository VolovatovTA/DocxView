package io.github.volovatovta.docviewer.core.format

import io.github.volovatovta.docviewer.core.FormatDetector
import io.github.volovatovta.docviewer.core.UnsupportedFormatException
import io.github.volovatovta.docviewer.core.model.DocumentFormat
import java.io.File

/** Reads any supported document except PDF, which takes a platform renderer. Works on any JVM. */
public object DocumentReader {

    /** [fileName] helps with text files and names a CSV sheet; [format] skips detection when it is already known. */
    public fun read(
        file: File,
        fileName: String? = null,
        format: DocumentFormat? = null,
        options: ReadOptions = ReadOptions(),
    ): DocumentContent {
        val detected = format ?: FormatDetector.detect(file, fileName)
            ?: throw UnsupportedFormatException("Unknown document format")
        val reader = Formats.readerOf(detected)
            ?: throw UnsupportedFormatException("$detected needs a platform renderer")
        return reader.read(ReadRequest(file, fileName, options))
    }
}
