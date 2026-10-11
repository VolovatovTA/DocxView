package io.github.volovatovta.docviewer.core.docx

import io.github.volovatovta.docviewer.core.CorruptedDocumentException
import io.github.volovatovta.docviewer.core.model.ImageStore
import io.github.volovatovta.docviewer.core.ooxml.OpcPackage
import java.util.zip.ZipFile

// The ZIP container of a .docx: the generic package plus the Word main part and its images.
internal class DocxPackage(zip: ZipFile) : OpcPackage(zip) {

    fun mainDocumentPath(): String =
        mainPartPath()
            ?: DEFAULT_MAIN_PART.takeIf { zip.getEntry(it) != null }
            ?: throw CorruptedDocumentException("DOCX has no main document part")

    fun images(): ImageStore = ZipImageStore(zip)

    private companion object {
        const val DEFAULT_MAIN_PART = "word/document.xml"
    }
}
