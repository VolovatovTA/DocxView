package io.github.volovatovta.docviewer.core.doc

import io.github.volovatovta.docviewer.core.model.ParagraphStyle
import io.github.volovatovta.docviewer.core.model.TextAlignment

// Paragraph formatting decoded from a PAPX: style index plus the sprms the viewer understands.
internal data class ParagraphProperties(
    val styleIndex: Int = 0,
    val inTable: Boolean = false,
    val tableDepth: Int = 0,
    val endsTableRow: Boolean = false,
    val alignment: TextAlignment = TextAlignment.Start,
    val listFormatOverride: Int = 0,
    val listLevel: Int = 0,
    val outlineLevel: Int? = null,
    val leftIndentTwips: Int = 0,
    val firstLineIndentTwips: Int = 0,
    val spaceBeforeTwips: Int? = null,
    val spaceAfterTwips: Int? = null,
) {
    val isInTable: Boolean get() = inTable || tableDepth > 0

    val isListItem: Boolean get() = listFormatOverride > 0

    // Built-in styles 1..9 are "heading 1".."heading 9" in every Word 97+ stylesheet.
    private val headingLevel: Int
        get() = when {
            styleIndex in HEADING_STYLES -> minOf(styleIndex, MAX_HEADING_LEVEL)
            outlineLevel != null -> minOf(outlineLevel + 1, MAX_HEADING_LEVEL)
            else -> 0
        }

    fun toParagraphStyle(listMarker: String?): ParagraphStyle {
        // A list item hangs its marker into the first-line indent instead of indenting the text.
        val hangingIndent = if (listMarker != null) minOf(firstLineIndentTwips, 0) else 0
        return ParagraphStyle(
            alignment = alignment,
            headingLevel = headingLevel,
            indentStartPt = (leftIndentTwips + hangingIndent).coerceAtLeast(0) / TWIPS_PER_POINT,
            firstLineIndentPt = if (listMarker != null) 0f else firstLineIndentTwips / TWIPS_PER_POINT,
            spaceBeforePt = spaceBeforeTwips?.let { it / TWIPS_PER_POINT },
            spaceAfterPt = spaceAfterTwips?.let { it / TWIPS_PER_POINT },
            listMarker = listMarker,
            listLevel = listLevel,
        )
    }

    companion object {
        private val HEADING_STYLES = 1..9
        private const val MAX_HEADING_LEVEL = 6
        private const val TWIPS_PER_POINT = 20f

        fun read(papx: ByteArray, range: IntRange): ParagraphProperties {
            if (range.last - range.first + 1 < Short.SIZE_BYTES) return ParagraphProperties()
            var properties = ParagraphProperties(styleIndex = papx.uint16(range.first))
            val sprms = range.first + Short.SIZE_BYTES..range.last
            papx.forEachSprm(sprms) { sprm, operand -> properties = properties.with(sprm, papx, operand) }
            return properties
        }
    }
}

private fun ParagraphProperties.with(sprm: Int, bytes: ByteArray, operand: Int): ParagraphProperties = when (sprm) {
    Sprm.PARA_IN_TABLE -> copy(inTable = bytes.uint8(operand) != 0)
    Sprm.PARA_TABLE_ROW_END -> copy(endsTableRow = bytes.uint8(operand) != 0)
    Sprm.PARA_TABLE_DEPTH -> copy(tableDepth = bytes.int32(operand))
    Sprm.PARA_JUSTIFICATION_80, Sprm.PARA_JUSTIFICATION -> copy(alignment = alignmentOf(bytes.uint8(operand)))
    Sprm.PARA_LIST_FORMAT_OVERRIDE -> copy(listFormatOverride = bytes.int16(operand))
    Sprm.PARA_LIST_LEVEL -> copy(listLevel = bytes.uint8(operand))
    Sprm.PARA_OUTLINE_LEVEL -> copy(outlineLevel = bytes.uint8(operand).takeIf { it < OutlineLevel.BODY_TEXT })
    Sprm.PARA_LEFT_INDENT_80, Sprm.PARA_LEFT_INDENT -> copy(leftIndentTwips = bytes.int16(operand))
    Sprm.PARA_FIRST_LINE_INDENT_80, Sprm.PARA_FIRST_LINE_INDENT -> copy(firstLineIndentTwips = bytes.int16(operand))
    Sprm.PARA_SPACE_BEFORE -> copy(spaceBeforeTwips = bytes.uint16(operand))
    Sprm.PARA_SPACE_AFTER -> copy(spaceAfterTwips = bytes.uint16(operand))
    else -> this
}

private object Justification {
    const val CENTER = 1
    const val RIGHT = 2
    const val JUSTIFIED = 3
    const val DISTRIBUTED = 4
    const val THAI_DISTRIBUTED = 5
}

private object OutlineLevel {
    // Level 9 marks body text rather than a heading.
    const val BODY_TEXT = 9
}

private fun alignmentOf(justification: Int): TextAlignment = when (justification) {
    Justification.CENTER -> TextAlignment.Center
    Justification.RIGHT -> TextAlignment.End
    Justification.JUSTIFIED, Justification.DISTRIBUTED, Justification.THAI_DISTRIBUTED -> TextAlignment.Justify
    else -> TextAlignment.Start
}
