package io.github.volovatovta.docviewer.core.docx

import io.github.volovatovta.docviewer.core.CorruptedDocumentException
import io.github.volovatovta.docviewer.core.model.FlowDocument
import org.xml.sax.SAXException
import java.io.File
import java.io.IOException
import java.util.zip.ZipException
import java.util.zip.ZipFile

public object DocxParser {

    public fun parse(file: File): FlowDocument {
        val zip = openArchive(file)
        // The archive stays open for lazily loaded images; it is closed only if reading fails.
        return zip.closeOnFailure { translatingErrors { DocxReader(DocxPackage(zip)).read() } }
    }

    private fun openArchive(file: File): ZipFile = try {
        ZipFile(file)
    } catch (e: ZipException) {
        // Encrypted OOXML is an OLE container, not a zip; FormatDetector routes it to DOC, which reports it.
        throw CorruptedDocumentException("Not a valid DOCX archive", e)
    }

    private inline fun <T> ZipFile.closeOnFailure(block: () -> T): T {
        var succeeded = false
        try {
            return block().also { succeeded = true }
        } finally {
            if (!succeeded) close()
        }
    }

    private inline fun <T> translatingErrors(block: () -> T): T = try {
        block()
    } catch (e: SAXException) {
        throw CorruptedDocumentException("Malformed XML in DOCX", e)
    } catch (e: IOException) {
        throw CorruptedDocumentException("Cannot read DOCX", e)
    }
}
