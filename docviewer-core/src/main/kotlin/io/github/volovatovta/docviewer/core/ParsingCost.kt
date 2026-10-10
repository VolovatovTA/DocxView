package io.github.volovatovta.docviewer.core

import io.github.volovatovta.docviewer.core.docx.DocxPackage
import io.github.volovatovta.docviewer.core.model.DocumentFormat
import java.io.File
import java.io.IOException
import java.util.zip.ZipException
import java.util.zip.ZipFile

/** How much a document costs to parse, so callers can refuse files that would not fit in memory. */
public object ParsingCost {

    // Peak heap per byte to parse, measured on large real-world-like files (see README, "Size limits").
    private const val TEXT_PEAK_PER_BYTE = 5
    private const val CSV_PEAK_PER_BYTE = 9
    private const val DOCX_PEAK_PER_XML_BYTE = 7
    private const val DOC_PEAK_PER_BYTE = 8

    /** Bytes the parser works through: the file itself, or the unpacked main XML part for DOCX. */
    public fun bytesToParse(file: File, format: DocumentFormat): Long = when (format) {
        DocumentFormat.DOCX -> unpackedMainPart(file) ?: file.length()
        else -> file.length()
    }

    /** Peak heap needed per byte of [bytesToParse]; 0 for PDF, which is rendered page by page from disk. */
    public fun peakMemoryPerByte(format: DocumentFormat): Int = when (format) {
        DocumentFormat.PDF -> 0
        DocumentFormat.TXT -> TEXT_PEAK_PER_BYTE
        DocumentFormat.CSV -> CSV_PEAK_PER_BYTE
        DocumentFormat.DOCX -> DOCX_PEAK_PER_XML_BYTE
        DocumentFormat.DOC -> DOC_PEAK_PER_BYTE
    }

    // The declared size of a zip entry is what inflating it will produce; a 2 MB DOCX can hold 30 MB of XML.
    private fun unpackedMainPart(file: File): Long? = try {
        ZipFile(file).use { zip ->
            zip.getEntry(DocxPackage(zip).mainDocumentPath())?.size?.takeIf { it >= 0 }
        }
    } catch (ignored: ZipException) {
        null
    } catch (ignored: CorruptedDocumentException) {
        null
    } catch (ignored: IOException) {
        null
    }
}
