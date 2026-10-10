package io.github.volovatovta.docviewer.core

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

// Builds minimal .docx archives for parser tests: a package skeleton plus the parts a test cares about.
internal object DocxFixtures {

    private const val PACKAGE_RELATIONSHIPS = "http://schemas.openxmlformats.org/package/2006/relationships"
    private const val RELATIONSHIP_TYPES = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    private const val CONTENT_TYPES = "http://schemas.openxmlformats.org/package/2006/content-types"

    val namespaces = listOf(
        "w" to "http://schemas.openxmlformats.org/wordprocessingml/2006/main",
        "r" to RELATIONSHIP_TYPES,
        "wp" to "http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing",
        "a" to "http://schemas.openxmlformats.org/drawingml/2006/main",
        "mc" to "http://schemas.openxmlformats.org/markup-compatibility/2006",
    ).joinToString(" ") { (prefix, uri) -> "xmlns:$prefix=\"$uri\"" }

    fun relationship(id: String, type: String, target: String, external: Boolean = false): String {
        val mode = if (external) " TargetMode=\"External\"" else ""
        return "<Relationship Id=\"$id\" Type=\"$RELATIONSHIP_TYPES/$type\" Target=\"$target\"$mode/>"
    }

    fun relationships(vararg entries: String): String =
        "<Relationships xmlns=\"$PACKAGE_RELATIONSHIPS\">${entries.joinToString("")}</Relationships>"

    fun document(body: String): String = "<w:document $namespaces><w:body>$body<w:sectPr/></w:body></w:document>"

    fun packageParts(): Map<String, String> = mapOf(
        "[Content_Types].xml" to "<Types xmlns=\"$CONTENT_TYPES\"/>",
        "_rels/.rels" to relationships(relationship("rId1", "officeDocument", "word/document.xml")),
    )

    fun write(file: File, parts: Map<String, String>) {
        ZipOutputStream(file.outputStream()).use { zip ->
            parts.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
    }
}
