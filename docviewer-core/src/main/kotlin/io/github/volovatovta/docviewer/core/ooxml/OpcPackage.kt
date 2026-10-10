package io.github.volovatovta.docviewer.core.ooxml

import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.io.InputStream
import java.util.zip.ZipFile

/** An Office Open XML package: a ZIP of parts linked by relationships ([MS-OPC]), shared by Word and Excel. */
internal open class OpcPackage(protected val zip: ZipFile) {

    /** The document part the package points at: word/document.xml, xl/workbook.xml and so on. */
    fun mainPartPath(): String? = relationshipsOf(PACKAGE_ROOT).ofType(RelationshipTypes.OFFICE_DOCUMENT)?.target

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

    /** Unpacked size of a part, as declared by the archive. */
    fun sizeOf(path: String): Long = entry(path)?.size?.takeIf { it >= 0 } ?: 0

    // Part names are case-insensitive in OPC, and some writers do not match the case of their own references.
    private fun stream(path: String): InputStream? = entry(path)?.let(zip::getInputStream)

    private fun entry(path: String) =
        zip.getEntry(path) ?: zip.entries().asSequence().firstOrNull { it.name.equals(path, ignoreCase = true) }

    private companion object {
        const val PACKAGE_ROOT = ""
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
