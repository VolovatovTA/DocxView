package io.github.volovatovta.docviewer.core.format

import io.github.volovatovta.docviewer.core.CorruptedDocumentException
import io.github.volovatovta.docviewer.core.DocumentException
import io.github.volovatovta.docviewer.core.DocumentTooLargeException
import io.github.volovatovta.docviewer.core.FormatDetector
import io.github.volovatovta.docviewer.core.UnsupportedFormatException
import io.github.volovatovta.docviewer.core.model.DocumentFormat
import java.io.File
import java.io.IOException

/** Reads any supported document except PDF, which takes a platform renderer. Works on any JVM. */
public object DocumentReader {

    /** [fileName] helps with text files and names a CSV sheet; [format] skips detection when it is already known. */
    public fun read(
        file: File,
        fileName: String? = null,
        format: DocumentFormat? = null,
        options: ReadOptions = ReadOptions(),
    ): DocumentContent = translatingFailures(file, options) {
        val detected = format ?: FormatDetector.detect(file, fileName)
            ?: throw UnsupportedFormatException("Unknown document format")
        val reader = Formats.readerOf(detected)
            ?: throw UnsupportedFormatException("$detected needs a platform renderer")
        reader.read(ReadRequest(file, fileName, options))
    }

    // Files from strangers break parsers in unforeseeable ways; callers get a DocumentException or a real I/O error.
    @Suppress("TooGenericExceptionCaught")
    private inline fun translatingFailures(file: File, options: ReadOptions, read: () -> DocumentContent) = try {
        read()
    } catch (e: DocumentException) {
        throw e
    } catch (e: IOException) {
        throw e
    } catch (e: OutOfMemoryError) {
        throw DocumentTooLargeException(file.length(), options.memoryBudgetBytes ?: Runtime.getRuntime().maxMemory())
            .apply { initCause(e) }
    } catch (e: StackOverflowError) {
        throw CorruptedDocumentException("The document is nested too deeply", e)
    } catch (e: RuntimeException) {
        throw CorruptedDocumentException("The document is damaged", e)
    }
}
