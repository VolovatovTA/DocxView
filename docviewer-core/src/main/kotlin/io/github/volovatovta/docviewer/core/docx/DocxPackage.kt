package io.github.volovatovta.docviewer.core.docx

import io.github.volovatovta.docviewer.core.CorruptedDocumentException
import io.github.volovatovta.docviewer.core.model.ImageStore
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.io.InputStream
import java.util.zip.ZipFile

// The ZIP container of a .docx: its parts, their relationships and the document properties.
internal class DocxPackage(private val zip: ZipFile) {

    fun mainDocumentPath(): String =
        relationshipsOf(PACKAGE_ROOT).ofType(RelationshipTypes.OFFICE_DOCUMENT)?.target
            ?: DEFAULT_MAIN_PART.takeIf { zip.getEntry(it) != null }
            ?: throw CorruptedDocumentException("DOCX has no main document part")

    fun relationshipsOf(partPath: String): Relationships {
        val ownerDirectory = partPath.substringBeforeLast('/', "")
        val relationshipsPath = listOf(ownerDirectory, "_rels", "${partPath.substringAfterLast('/')}.rels")
            .filter { it.isNotEmpty() }
            .joinToString("/")
        return read(relationshipsPath) { Relationships.parse(it, ownerDirectory) } ?: Relationships.None
    }

    fun <T> read(path: String, reader: (InputStream) -> T): T? = stream(path)?.use(reader)

    fun parse(path: String, handler: DefaultHandler): Boolean = read(path) { Xml.parse(it, handler) } != null

    fun title(): String? = read(CORE_PROPERTIES) { input -> TitleHandler().also { Xml.parse(input, it) }.title }

    fun images(): ImageStore = ZipImageStore(zip)

    // Part names are case-insensitive in OPC, and some writers do not match the case of their own references.
    private fun stream(path: String): InputStream? {
        val entry = zip.getEntry(path)
            ?: zip.entries().asSequence().firstOrNull { it.name.equals(path, ignoreCase = true) }
        return entry?.let(zip::getInputStream)
    }

    private companion object {
        const val PACKAGE_ROOT = ""
        const val DEFAULT_MAIN_PART = "word/document.xml"
        const val CORE_PROPERTIES = "docProps/core.xml"
    }
}

// dc:title of docProps/core.xml.
private class TitleHandler : DefaultHandler() {
    var title: String? = null
        private set

    private var insideTitle = false
    private val text = StringBuilder()

    override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes) {
        insideTitle = localName(localName, qName) == "title"
    }

    override fun characters(ch: CharArray, start: Int, length: Int) {
        if (insideTitle) text.appendRange(ch, start, start + length)
    }

    override fun endElement(uri: String?, localName: String?, qName: String?) {
        if (insideTitle) title = text.toString().trim().takeIf { it.isNotEmpty() }
        insideTitle = false
    }
}
