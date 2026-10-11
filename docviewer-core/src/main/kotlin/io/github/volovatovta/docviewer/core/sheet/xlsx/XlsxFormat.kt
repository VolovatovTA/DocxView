package io.github.volovatovta.docviewer.core.sheet.xlsx

import io.github.volovatovta.docviewer.core.CorruptedDocumentException
import io.github.volovatovta.docviewer.core.format.DocumentContent
import io.github.volovatovta.docviewer.core.format.FormatReader
import io.github.volovatovta.docviewer.core.format.ProbedFile
import io.github.volovatovta.docviewer.core.format.ReadRequest
import io.github.volovatovta.docviewer.core.model.DocumentFormat
import io.github.volovatovta.docviewer.core.ooxml.OpcPackage
import org.xml.sax.SAXException
import java.io.File
import java.io.IOException
import java.util.zip.ZipFile

/** Excel 2007 and later: a ZIP package around SpreadsheetML worksheets. */
internal object XlsxFormat : FormatReader {
    override val format = DocumentFormat.XLSX

    // Per byte of unpacked worksheet and shared-string XML.
    override val peakMemoryPerByte = 6

    override fun recognizes(file: ProbedFile): Boolean =
        file.isZip && (file.mainFolder?.let { it == FOLDER } ?: (MAIN_PART in file.zipEntries))

    // The worksheets and shared strings unpack to many times the archive size; pictures are never parsed.
    override fun bytesToParse(file: File): Long = try {
        ZipFile(file).use { zip ->
            zip.entries().asSequence()
                .filter { it.name.startsWith(XML_FOLDER) && it.name.endsWith(XML_EXTENSION) }
                .sumOf { it.size.coerceAtLeast(0) }
        }
    } catch (_: IOException) {
        file.length()
    }

    override fun read(request: ReadRequest): DocumentContent {
        request.requireMemoryFor(this)
        val workbook = try {
            ZipFile(request.file).use { zip -> XlsxWorkbook(OpcPackage(zip)).read() }
        } catch (e: IOException) {
            throw CorruptedDocumentException("Not a valid XLSX archive", e)
        } catch (e: SAXException) {
            throw CorruptedDocumentException("Malformed XML in XLSX", e)
        }
        return DocumentContent.Sheets(workbook)
    }

    private const val MAIN_PART = "xl/workbook.xml"
    private const val XML_FOLDER = "xl/"
    private const val XML_EXTENSION = ".xml"
    private const val FOLDER = "xl"
}
