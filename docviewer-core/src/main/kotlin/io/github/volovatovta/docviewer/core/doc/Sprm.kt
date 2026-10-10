package io.github.volovatovta.docviewer.core.doc

import io.github.volovatovta.docviewer.core.binary.uint16
import io.github.volovatovta.docviewer.core.binary.uint8

// Single property modifiers ([MS-DOC] 2.6); names mirror the spec, e.g. sprmCFBold is CHAR_BOLD.
internal object Sprm {
    const val CHAR_BOLD = 0x0835
    const val CHAR_ITALIC = 0x0836
    const val CHAR_STRIKE = 0x0837
    const val CHAR_DOUBLE_STRIKE = 0x2A53
    const val CHAR_HIDDEN = 0x083C
    const val CHAR_SMALL_CAPS = 0x083A
    const val CHAR_CAPS = 0x083B
    const val CHAR_SPECIAL = 0x0855
    const val CHAR_PICTURE_LOCATION = 0x6A03
    const val CHAR_UNDERLINE = 0x2A3E
    const val CHAR_FONT_SIZE = 0x4A43
    const val CHAR_COLOR_INDEX = 0x2A42
    const val CHAR_COLOR = 0x6870
    const val CHAR_HIGHLIGHT = 0x2A0C
    const val CHAR_VERTICAL_POSITION = 0x2A48

    const val PARA_IN_TABLE = 0x2416
    const val PARA_TABLE_ROW_END = 0x2417
    const val PARA_TABLE_DEPTH = 0x6649
    const val PARA_JUSTIFICATION_80 = 0x2403
    const val PARA_JUSTIFICATION = 0x2461
    const val PARA_LIST_FORMAT_OVERRIDE = 0x460B
    const val PARA_LIST_LEVEL = 0x260A
    const val PARA_OUTLINE_LEVEL = 0x2640
    const val PARA_LEFT_INDENT_80 = 0x840F
    const val PARA_LEFT_INDENT = 0x845E
    const val PARA_FIRST_LINE_INDENT_80 = 0x8411
    const val PARA_FIRST_LINE_INDENT = 0x8460
    const val PARA_SPACE_BEFORE = 0xA413
    const val PARA_SPACE_AFTER = 0xA414

    const val TABLE_DEFINITION = 0xD608
    const val TABLE_DEFINITION_10 = 0xD606
    const val PARA_CHANGE_TABS = 0xC615
}

// Calls [action] with each sprm in [range] and the offset of its operand.
internal fun ByteArray.forEachSprm(range: IntRange, action: (sprm: Int, operand: Int) -> Unit) {
    var position = range.first
    while (true) {
        val modifier = readSprm(position, range.last + 1) ?: break
        action(modifier.code, modifier.operand)
        position = modifier.operand + modifier.operandLength
    }
}

private class SprmAt(val code: Int, val operand: Int, val operandLength: Int)

private object SprmEncoding {
    const val SPRA_SHIFT = 13
    const val VARIABLE_LENGTH = -1
    const val LONG_FORM_MARKER = 0xFF

    // Operand size in bytes by the sprm's spra field (bits 13-15); spra 6 means "length-prefixed".
    val OPERAND_SIZE_BY_SPRA = intArrayOf(1, 1, 2, 4, 2, 2, VARIABLE_LENGTH, 3)

    val LONG_LENGTH_SPRMS = setOf(Sprm.TABLE_DEFINITION, Sprm.TABLE_DEFINITION_10)
}

private fun ByteArray.readSprm(position: Int, end: Int): SprmAt? {
    if (position + Short.SIZE_BYTES > end) return null
    val code = uint16(position)
    val afterCode = position + Short.SIZE_BYTES
    val fixedSize = SprmEncoding.OPERAND_SIZE_BY_SPRA[code ushr SprmEncoding.SPRA_SHIFT]
    val sprm = if (fixedSize == SprmEncoding.VARIABLE_LENGTH) {
        readVariableSprm(code, afterCode, end)
    } else {
        SprmAt(code, afterCode, fixedSize)
    }
    return sprm?.takeIf { it.operandLength >= 0 && it.operand + it.operandLength <= end }
}

private fun ByteArray.readVariableSprm(code: Int, afterCode: Int, end: Int): SprmAt? {
    if (afterCode >= end) return null
    if (code in SprmEncoding.LONG_LENGTH_SPRMS) {
        // The two-byte length of sprmTDefTable counts one byte more than follows it.
        return SprmAt(code, afterCode + Short.SIZE_BYTES, uint16(afterCode) - 1)
    }
    val length = uint8(afterCode)
    // The rare long form of sprmPChgTabs is not needed for text, so parsing stops there.
    if (length == SprmEncoding.LONG_FORM_MARKER && code == Sprm.PARA_CHANGE_TABS) return null
    return SprmAt(code, afterCode + Byte.SIZE_BYTES, length)
}
