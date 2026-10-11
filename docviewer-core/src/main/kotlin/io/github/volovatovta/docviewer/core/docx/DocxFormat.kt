package io.github.volovatovta.docviewer.core.docx

import io.github.volovatovta.docviewer.core.format.DocumentContent
import io.github.volovatovta.docviewer.core.format.FormatReader
import io.github.volovatovta.docviewer.core.format.ProbedFile
import io.github.volovatovta.docviewer.core.format.ReadRequest
import io.github.volovatovta.docviewer.core.model.DocumentFormat
import java.io.File
import java.io.IOException
import java.util.zip.ZipFile

/** Word 2007 and later: a ZIP package around a WordprocessingML document. */
internal object DocxFormat : FormatReader {
    override val format = DocumentFormat.DOCX

    // Measured per byte of the unpacked document part; see README, "Size limits".
    override val peakMemoryPerByte = 7

    override fun recognizes(file: ProbedFile): Boolean =
        file.isZip && (file.mainFolder?.let { it == FOLDER } ?: (MAIN_PART in file.zipEntries))

    // A 2 MB file can unpack into 30 MB of XML, so the archive size says little about the work.
    override fun bytesToParse(file: File): Long = try {
        ZipFile(file).use { zip -> DocxPackage(zip).run { sizeOf(mainDocumentPath()) } }
    } catch (_: IOException) {
        null
    } ?: file.length()

    override fun read(request: ReadRequest): DocumentContent {
        request.requireMemoryFor(this)
        return DocumentContent.Flow(DocxParser.parse(request.file))
    }

    private const val MAIN_PART = "word/document.xml"
    private const val FOLDER = "word"
}
