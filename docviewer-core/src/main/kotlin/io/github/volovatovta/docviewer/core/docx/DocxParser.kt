package io.github.volovatovta.docviewer.core.docx

import io.github.volovatovta.docviewer.core.CorruptedDocumentException
import io.github.volovatovta.docviewer.core.SizeNormalizer
import io.github.volovatovta.docviewer.core.model.Block
import io.github.volovatovta.docviewer.core.model.Divider
import io.github.volovatovta.docviewer.core.model.FlowDocument
import io.github.volovatovta.docviewer.core.model.ImageStore
import io.github.volovatovta.docviewer.core.model.Paragraph
import io.github.volovatovta.docviewer.core.model.RunStyle
import io.github.volovatovta.docviewer.core.model.TextRun
import io.github.volovatovta.docviewer.core.model.VerticalAlign
import org.xml.sax.Attributes
import org.xml.sax.SAXException
import org.xml.sax.helpers.DefaultHandler
import java.io.File
import java.io.IOException
import java.util.zip.ZipException
import java.util.zip.ZipFile

public object DocxParser {

    public fun parse(file: File): FlowDocument {
        val zip = try {
            ZipFile(file)
        } catch (e: ZipException) {
            // Encrypted OOXML is an OLE container, not a zip; FormatDetector routes it to DOC, which reports it.
            throw CorruptedDocumentException("Not a valid DOCX archive", e)
        }
        try {
            return parse(zip)
        } catch (e: SAXException) {
            zip.close()
            throw CorruptedDocumentException("Malformed XML in DOCX", e)
        } catch (e: IOException) {
            zip.close()
            throw CorruptedDocumentException("Cannot read DOCX", e)
        } catch (e: Throwable) {
            zip.close()
            throw e
        }
    }

    private fun parse(zip: ZipFile): FlowDocument {
        val mainPath = readRels(zip, "_rels/.rels").values.firstOrNull { it.type.endsWith("/officeDocument") }?.target
            ?: "word/document.xml".takeIf { zip.getEntry(it) != null }
            ?: throw CorruptedDocumentException("DOCX has no main document part")
        val rels = readRels(zip, relsPathFor(mainPath))

        fun part(typeSuffix: String) = rels.values.firstOrNull { it.type.endsWith(typeSuffix) && !it.external }?.target

        val styles = part("/styles")?.let { path -> zip.stream(path)?.use { StyleSheet.parse(it) } } ?: StyleSheet.Empty
        val numbering = part("/numbering")?.let { path -> zip.stream(path)?.use { Numbering.parse(it) } } ?: Numbering.Empty

        // Notes are numbered in order of first reference, like Word displays them.
        val noteNumbers = LinkedHashMap<String, Int>()
        val marker: (String, String) -> String? = { kind, id -> "${noteNumbers.getOrPut("$kind:$id") { noteNumbers.size + 1 }}" }

        val body = DocxBodyHandler(styles, numbering, rels, marker)
        zip.stream(mainPath)?.use { Xml.parse(it, body) } ?: throw CorruptedDocumentException("Missing $mainPath")

        val blocks = ArrayList(SizeNormalizer.normalize(body.blocks))
        if (noteNumbers.isNotEmpty()) appendNotes(zip, styles, rels, noteNumbers, marker, blocks)

        val title = zip.stream("docProps/core.xml")?.use { readTitle(it) }
        return FlowDocument(blocks, title, ZipImageStore(zip))
    }

    private fun appendNotes(
        zip: ZipFile,
        styles: StyleSheet,
        rels: Map<String, Relationship>,
        numbers: Map<String, Int>,
        marker: (String, String) -> String?,
        blocks: MutableList<Block>,
    ) {
        val collected = HashMap<String, List<Block>>()
        for (kind in listOf("footnote", "endnote")) {
            val rel = rels.values.firstOrNull { it.type.endsWith("/${kind}s") } ?: continue
            val noteRels = readRels(zip, relsPathFor(rel.target))
            val handler = DocxBodyHandler(styles, Numbering.Empty, noteRels, marker)
            zip.stream(rel.target)?.use { Xml.parse(it, handler) } ?: continue
            handler.notes.forEach { (id, noteBlocks) -> collected["$kind:$id"] = noteBlocks }
        }
        if (collected.isEmpty()) return
        blocks += Divider
        for ((key, number) in numbers.entries.sortedBy { it.value }) {
            val noteBlocks = collected[key] ?: continue
            val first = noteBlocks.firstOrNull() as? Paragraph
            val hasMarker = first?.inlines?.firstOrNull()?.let { it is TextRun && it.style.verticalAlign == VerticalAlign.Superscript } == true
            if (first != null && !hasMarker) {
                blocks += Paragraph(listOf(TextRun("$number", RunStyle(verticalAlign = VerticalAlign.Superscript))) + first.inlines, first.style)
                blocks.addAll(noteBlocks.drop(1))
            } else {
                blocks.addAll(noteBlocks)
            }
        }
    }

    private fun relsPathFor(partPath: String): String {
        val dir = partPath.substringBeforeLast('/', "")
        val name = partPath.substringAfterLast('/')
        return if (dir.isEmpty()) "_rels/$name.rels" else "$dir/_rels/$name.rels"
    }

    private fun readRels(zip: ZipFile, relsPath: String): Map<String, Relationship> {
        val stream = zip.stream(relsPath) ?: return emptyMap()
        // Targets are relative to the directory of the part that owns the .rels file.
        val ownerDir = relsPath.substringBeforeLast("_rels/", "").removeSuffix("/")
        val result = HashMap<String, Relationship>()
        stream.use {
            Xml.parse(it, object : DefaultHandler() {
                override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes) {
                    if (localName(localName, qName) != "Relationship") return
                    val id = attributes["Id"] ?: return
                    val target = attributes["Target"] ?: return
                    val external = attributes["TargetMode"].equals("External", ignoreCase = true)
                    result[id] = Relationship(attributes["Type"].orEmpty(), if (external) target else resolvePath(ownerDir, target), external)
                }
            })
        }
        return result
    }

    internal fun resolvePath(baseDir: String, target: String): String {
        if (target.startsWith("/")) return target.removePrefix("/")
        val parts = if (baseDir.isEmpty()) ArrayList() else baseDir.split('/').toMutableList()
        for (segment in target.split('/')) {
            when (segment) {
                "", "." -> Unit
                ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.lastIndex)
                else -> parts += segment
            }
        }
        return parts.joinToString("/")
    }

    private fun readTitle(input: java.io.InputStream): String? {
        var title: String? = null
        var inTitle = false
        val text = StringBuilder()
        Xml.parse(input, object : DefaultHandler() {
            override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes) {
                inTitle = localName(localName, qName) == "title"
            }

            override fun characters(ch: CharArray, start: Int, length: Int) {
                if (inTitle) text.append(ch, start, length)
            }

            override fun endElement(uri: String?, localName: String?, qName: String?) {
                if (inTitle) title = text.toString().trim().takeIf { it.isNotEmpty() }
                inTitle = false
            }
        })
        return title
    }

    private fun ZipFile.stream(path: String) = (getEntry(path) ?: entries().asSequence().firstOrNull { it.name.equals(path, ignoreCase = true) })
        ?.let { getInputStream(it) }

    private class ZipImageStore(private val zip: ZipFile) : ImageStore {
        @Volatile private var closed = false

        override fun open(imageId: String): ByteArray? = synchronized(this) {
            if (closed) return null
            val entry = zip.getEntry(imageId) ?: return null
            // Guard against zip bombs posing as images.
            if (entry.size > MAX_IMAGE_BYTES) return null
            zip.getInputStream(entry).use { it.readBytes() }
        }

        override fun close() = synchronized(this) {
            closed = true
            zip.close()
        }
    }

    private const val MAX_IMAGE_BYTES = 40L * 1024 * 1024
}
