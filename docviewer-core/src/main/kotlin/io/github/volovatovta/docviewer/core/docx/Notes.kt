package io.github.volovatovta.docviewer.core.docx

import io.github.volovatovta.docviewer.core.model.Block
import io.github.volovatovta.docviewer.core.model.Divider
import io.github.volovatovta.docviewer.core.model.Paragraph
import io.github.volovatovta.docviewer.core.model.RunStyle
import io.github.volovatovta.docviewer.core.model.TextRun
import io.github.volovatovta.docviewer.core.model.VerticalAlign

// Footnotes and endnotes are numbered in order of first reference, like Word displays them.
internal class NoteNumbers {
    private val numbers = LinkedHashMap<String, Int>()

    val isEmpty: Boolean get() = numbers.isEmpty()

    fun marker(kind: String, id: String): String = numbers.getOrPut(key(kind, id)) { numbers.size + 1 }.toString()

    fun inOrder(): List<Pair<String, Int>> = numbers.entries.sortedBy { it.value }.map { it.key to it.value }

    companion object {
        fun key(kind: String, id: String) = "$kind:$id"
    }
}

// The bodies of referenced notes, appended after a divider, each starting with its number.
internal class NotesSection(
    private val docx: DocxPackage,
    private val styles: StyleSheet,
    private val relationships: Relationships,
    private val numbers: NoteNumbers,
) {
    fun blocks(): List<Block> {
        val bodies = NOTE_KINDS.flatMap(::readNotes).toMap()
        if (bodies.isEmpty()) return emptyList()
        val numberedNotes = numbers.inOrder().flatMap { (key, number) ->
            bodies[key]?.let { numbered(it, number) }.orEmpty()
        }
        return listOf(Divider) + numberedNotes
    }

    private fun readNotes(kind: String): List<Pair<String, List<Block>>> {
        val part = relationships.ofType(RelationshipTypes.notes(kind))?.target ?: return emptyList()
        val handler = DocxBodyHandler(styles, Numbering.Empty, docx.relationshipsOf(part), numbers::marker)
        if (!docx.parse(part, handler)) return emptyList()
        return handler.notes.map { (id, blocks) -> NoteNumbers.key(kind, id) to blocks }
    }

    private fun numbered(blocks: List<Block>, number: Int): List<Block> {
        val first = blocks.firstOrNull() as? Paragraph ?: return blocks
        val firstRun = first.inlines.firstOrNull()
        if (firstRun is TextRun && firstRun.style.verticalAlign == VerticalAlign.Superscript) return blocks
        val marker = TextRun("$number", RunStyle(verticalAlign = VerticalAlign.Superscript))
        return listOf(Paragraph(listOf(marker) + first.inlines, first.style)) + blocks.drop(1)
    }

    private companion object {
        val NOTE_KINDS = listOf("footnote", "endnote")
    }
}
