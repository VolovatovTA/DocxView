package io.github.volovatovta.docviewer.core.docx

import io.github.volovatovta.docviewer.core.CorruptedDocumentException
import io.github.volovatovta.docviewer.core.SizeNormalizer
import io.github.volovatovta.docviewer.core.model.FlowDocument

// Reads the parts of a .docx in dependency order: styles and numbering first, then the body, then the notes.
internal class DocxReader(private val docx: DocxPackage) {

    fun read(): FlowDocument {
        val mainPart = docx.mainDocumentPath()
        val relationships = docx.relationshipsOf(mainPart)
        val styles = relationships.partOfType(RelationshipTypes.STYLES)
            ?.let { part -> docx.read(part) { StyleSheet.parse(it) } }
            ?: StyleSheet.Empty
        val numbering = relationships.partOfType(RelationshipTypes.NUMBERING)
            ?.let { part -> docx.read(part) { Numbering.parse(it) } }
            ?: Numbering.Empty
        val notes = NoteNumbers()
        val body = DocxBodyHandler(styles, numbering, relationships, notes::marker)
        if (!docx.parse(mainPart, body)) throw CorruptedDocumentException("Missing $mainPart")

        val noteBlocks = if (notes.isEmpty) emptyList() else NotesSection(docx, styles, relationships, notes).blocks()
        return FlowDocument(SizeNormalizer.normalize(body.blocks) + noteBlocks, docx.title(), docx.images())
    }
}
