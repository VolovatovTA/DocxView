package io.github.volovatovta.docviewer.core.doc

import io.github.volovatovta.docviewer.core.binary.startsWith
import io.github.volovatovta.docviewer.core.model.Block
import io.github.volovatovta.docviewer.core.model.ImageRun
import io.github.volovatovta.docviewer.core.model.Inline
import io.github.volovatovta.docviewer.core.model.PageBreak
import io.github.volovatovta.docviewer.core.model.Paragraph
import io.github.volovatovta.docviewer.core.model.RunStyle
import io.github.volovatovta.docviewer.core.model.Table
import io.github.volovatovta.docviewer.core.model.TableCell
import io.github.volovatovta.docviewer.core.model.TableRow
import io.github.volovatovta.docviewer.core.model.TextRun

// Walks the decoded text once and turns paragraph, cell and field marks into model blocks.
internal class DocumentBuilder(
    private val text: DecodedText,
    private val characterRuns: PropertyRuns<CharacterProperties>,
    private val paragraphRuns: PropertyRuns<ParagraphProperties>,
    private val lists: ListTables?,
    private val bodySizeHalfPoints: Int,
    private val images: DocImageStore,
) {
    private val blocks = ArrayList<Block>()
    private val inlines = InlineCollector()
    private val fields = FieldStack()
    private val tables = TableAssembler()

    private var lastProperties: CharacterProperties? = null
    private var lastStyle = RunStyle.Default

    fun build(): List<Block> {
        text.chars.forEachIndexed { position, char ->
            if (!fields.consume(char)) consumeText(char, text.offsets[position])
        }
        if (inlines.hasContent) endParagraph(text.offsets.lastOrNull() ?: 0, ControlChar.PARAGRAPH_END)
        tables.flushInto(blocks)
        return blocks
    }

    private fun consumeText(char: Char, offset: Int) {
        when (char) {
            ControlChar.PARAGRAPH_END, ControlChar.CELL_MARK -> endParagraph(offset, char)
            ControlChar.PAGE_BREAK -> {
                endParagraph(offset, char)
                addPageBreak()
            }
            ControlChar.LINE_BREAK, ControlChar.COLUMN_BREAK -> append('\n', offset)
            ControlChar.NON_BREAKING_HYPHEN -> append(VISIBLE_NON_BREAKING_HYPHEN, offset)
            ControlChar.TAB, ControlChar.NO_BREAK_SPACE, ControlChar.PICTURE -> append(char, offset)
            else -> if (char >= ' ') append(char, offset)
        }
    }

    private fun append(char: Char, offset: Int) {
        val properties = characterRuns.at(offset)
        when {
            char == ControlChar.PICTURE && properties.isVisiblePicture -> appendPicture(properties.pictureLocation)
            properties.hidden || properties.special -> Unit
            else -> {
                val shown = if (properties.caps) char.uppercaseChar() else char
                inlines.appendText(shown, styleOf(properties), fields.link)
            }
        }
    }

    private fun appendPicture(location: Int) {
        val picture = images.describe(location) ?: return
        inlines.appendImage(ImageRun(picture.id, picture.widthPt, picture.heightPt))
    }

    // Runs share property objects, so an identity check avoids rebuilding the same style for every character.
    private fun styleOf(properties: CharacterProperties): RunStyle {
        if (properties !== lastProperties) {
            lastProperties = properties
            lastStyle = properties.toRunStyle(bodySizeHalfPoints)
        }
        return lastStyle
    }

    private fun endParagraph(markOffset: Int, mark: Char) {
        val properties = paragraphRuns.at(markOffset)
        val marker = if (properties.isListItem) listMarker(properties) else null
        val paragraph = Paragraph(inlines.take(), properties.toParagraphStyle(marker))
        if (properties.isInTable) {
            tables.add(paragraph, endsCell = mark == ControlChar.CELL_MARK, endsRow = properties.endsTableRow)
        } else {
            tables.flushInto(blocks)
            blocks += paragraph
        }
    }

    private fun listMarker(properties: ParagraphProperties): String =
        lists?.nextMarker(properties.listFormatOverride, properties.listLevel) ?: DEFAULT_BULLET

    // Page breaks only make sense in the main flow, and never twice in a row.
    private fun addPageBreak() {
        if (tables.isEmpty && blocks.isNotEmpty() && blocks.last() != PageBreak) blocks += PageBreak
    }

    private companion object {
        const val DEFAULT_BULLET = "•"
        const val VISIBLE_NON_BREAKING_HYPHEN = '\u2011'
    }
}

// Characters with a structural meaning in Word text ([MS-DOC] 2.8.25).
private object ControlChar {
    const val PICTURE = '\u0001'
    const val CELL_MARK = '\u0007'
    const val TAB = '\t'
    const val LINE_BREAK = '\u000B'
    const val PAGE_BREAK = '\u000C'
    const val PARAGRAPH_END = '\r'
    const val COLUMN_BREAK = '\u000E'
    const val FIELD_BEGIN = '\u0013'
    const val FIELD_SEPARATOR = '\u0014'
    const val FIELD_END = '\u0015'
    const val NON_BREAKING_HYPHEN = '\u001E'
    const val NO_BREAK_SPACE = '\u00A0'
}

// Fields look like BEGIN instruction SEPARATOR result END; only the result is visible text.
private class FieldStack {
    private class Field {
        var inInstruction = true
        val instruction = StringBuilder()
        var link: String? = null
    }

    private val fields = ArrayList<Field>()

    val link: String? get() = fields.lastOrNull { it.link != null }?.link

    // Returns true when the character belongs to field syntax or to an instruction rather than to visible text.
    fun consume(char: Char): Boolean {
        when (char) {
            ControlChar.FIELD_BEGIN -> fields += Field()
            ControlChar.FIELD_SEPARATOR -> fields.lastOrNull()?.let { field ->
                field.inInstruction = false
                field.link = FieldInstruction.hyperlinkTarget(field.instruction)
            }
            ControlChar.FIELD_END -> fields.removeLastOrNull()
            else -> fields.lastOrNull { it.inInstruction }?.instruction?.append(char) ?: return false
        }
        return true
    }
}

private object FieldInstruction {
    private const val HYPERLINK = "HYPERLINK"
    private val LOCAL_BOOKMARK_SWITCH = Regex("\\\\l\\b")
    private val QUOTED_TARGET = Regex("\"([^\"]+)\"")

    // HYPERLINK "url" links outside; the \l switch points at a bookmark inside the document.
    fun hyperlinkTarget(instruction: CharSequence): String? {
        val trimmed = instruction.trim()
        val isExternalLink = trimmed.startsWith(HYPERLINK, ignoreCase = true) &&
            !LOCAL_BOOKMARK_SWITCH.containsMatchIn(trimmed)
        return if (isExternalLink) QUOTED_TARGET.find(trimmed)?.groupValues?.get(1) else null
    }
}

// Collects the inlines of one paragraph, merging consecutive characters with the same style and link.
private class InlineCollector {
    private val inlines = ArrayList<Inline>()
    private val runText = StringBuilder()
    private var runStyle: RunStyle? = null
    private var runLink: String? = null

    val hasContent: Boolean get() = inlines.isNotEmpty() || runText.isNotEmpty()

    fun appendText(char: Char, style: RunStyle, link: String?) {
        if (style != runStyle || link != runLink) flushRun()
        runStyle = style
        runLink = link
        runText.append(char)
    }

    fun appendImage(image: ImageRun) {
        flushRun()
        inlines += image
    }

    fun take(): List<Inline> {
        flushRun()
        return inlines.toList().also { inlines.clear() }
    }

    private fun flushRun() {
        val style = runStyle
        if (runText.isNotEmpty() && style != null) inlines += TextRun(runText.toString(), style, runLink)
        runText.setLength(0)
    }
}

// Word marks the end of every cell, and the end of a row with an extra "row end" paragraph.
private class TableAssembler {
    private val rows = ArrayList<TableRow>()
    private val cells = ArrayList<TableCell>()
    private val cellBlocks = ArrayList<Block>()

    val isEmpty: Boolean get() = rows.isEmpty() && cells.isEmpty() && cellBlocks.isEmpty()

    fun add(paragraph: Paragraph, endsCell: Boolean, endsRow: Boolean) {
        if (endsCell && endsRow) {
            endRow()
        } else {
            cellBlocks += paragraph
            if (endsCell) closeCell()
        }
    }

    fun flushInto(blocks: MutableList<Block>) {
        endRow()
        if (rows.isNotEmpty()) blocks += Table(rows.toList())
        rows.clear()
    }

    private fun endRow() {
        if (cellBlocks.isNotEmpty()) closeCell()
        if (cells.isNotEmpty()) rows += TableRow(cells.toList())
        cells.clear()
    }

    private fun closeCell() {
        cells += TableCell(cellBlocks.toList())
        cellBlocks.clear()
    }
}
