package io.github.volovatovta.docviewer.core.ooxml

import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.io.FilterInputStream
import java.io.InputStream
import java.util.zip.ZipException
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
    // Memory budgets trust the sizes the archive declares, so a part may never unpack to more than that.
    private fun stream(path: String): InputStream? =
        entry(path)?.let { entry -> DeclaredSizeStream(zip.getInputStream(entry), entry.size) }

    private fun entry(path: String) =
        zip.getEntry(path) ?: zip.entries().asSequence().firstOrNull { it.name.equals(path, ignoreCase = true) }

    private companion object {
        const val PACKAGE_ROOT = ""
        const val CORE_PROPERTIES = "docProps/core.xml"
    }
}

// Fails a part that unpacks beyond the size its archive declares, as a ZIP bomb would.
private class DeclaredSizeStream(input: InputStream, private val declared: Long) : FilterInputStream(input) {
    private var count = 0L

    override fun read(): Int = super.read().also { if (it >= 0) count(1) }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        super.read(buffer, offset, length).also { if (it > 0) count(it) }

    private fun count(bytes: Int) {
        count += bytes
        if (declared >= 0 && count > declared) throw ZipException("A part unpacks beyond its declared size")
    }
}

// dc:title of docProps/core.xml.
private class TitleHandler : ElementHandler() {
    var title: String? = null
        private set

    override fun start(name: String, attributes: Attributes) {
        collecting = name == "title"
    }

    override fun end(name: String) {
        if (collecting) title = text.toString().trim().takeIf { it.isNotEmpty() }
        collecting = false
    }
}
