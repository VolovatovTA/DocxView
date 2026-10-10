package io.github.volovatovta.docviewer.core.doc

import io.github.volovatovta.docviewer.core.docx.Numbering

// List definitions ([MS-DOC] PlfLst and PlfLfo) that turn a paragraph's list reference into its visible marker.
internal class ListTables private constructor(
    private val levelsByListId: Map<Int, List<ListLevel>>,
    private val listIdByOverride: IntArray,
) {
    private class ListLevel(val start: Int, val numberFormat: Int, val template: String)

    private val counters = HashMap<Int, IntArray>()

    // List format overrides are 1-based; 0 means "not in a list".
    fun nextMarker(formatOverride: Int, level: Int): String? {
        val listId = listIdByOverride.getOrNull(formatOverride - 1) ?: return null
        val levels = levelsByListId[listId] ?: return null
        val depth = level.coerceIn(0, levels.lastIndex)
        val counter = counters.getOrPut(listId) { IntArray(MAX_LEVELS) { (levels.getOrNull(it)?.start ?: 1) - 1 } }
        counter[depth]++
        // Starting a higher level restarts every deeper one.
        for (deeper in depth + 1 until MAX_LEVELS) counter[deeper] = (levels.getOrNull(deeper)?.start ?: 1) - 1
        return levels[depth].marker(counter, levels)
    }

    private fun ListLevel.marker(counter: IntArray, levels: List<ListLevel>): String? = when (numberFormat) {
        NumberFormat.BULLET -> Numbering.bullet(template, SYMBOL_FONT)
        NumberFormat.NONE -> null
        else -> buildString { template.forEach { append(expand(it, counter, levels)) } }.ifEmpty { null }
    }

    // Characters below 9 in a number template are placeholders for the counter of that level.
    private fun expand(char: Char, counter: IntArray, levels: List<ListLevel>): String {
        val level = char.code
        if (level >= MAX_LEVELS) return char.toString()
        val format = levels.getOrNull(level)?.numberFormat ?: NumberFormat.DECIMAL
        return Numbering.format(counter[level].coerceAtLeast(1), NumberFormat.ooxmlName(format))
    }

    // Number format codes (nfc) of [MS-DOC] 2.9.170, mapped onto the OOXML names the shared formatter knows.
    private object NumberFormat {
        const val DECIMAL = 0
        const val UPPER_ROMAN = 1
        const val LOWER_ROMAN = 2
        const val UPPER_LETTER = 3
        const val LOWER_LETTER = 4
        const val DECIMAL_ZERO = 22
        const val BULLET = 23
        const val NONE = 255

        private val OOXML_NAMES = mapOf(
            UPPER_ROMAN to "upperRoman",
            LOWER_ROMAN to "lowerRoman",
            UPPER_LETTER to "upperLetter",
            LOWER_LETTER to "lowerLetter",
            DECIMAL_ZERO to "decimalZero",
        )

        fun ooxmlName(format: Int): String = OOXML_NAMES[format] ?: "decimal"
    }

    companion object {
        private const val MAX_LEVELS = 9
        private const val SYMBOL_FONT = "symbol"
        private const val LIST_HEADER_SIZE = 28
        private const val SIMPLE_LIST_FLAGS = 26
        private const val LEVEL_HEADER_SIZE = 28
        private const val LEVEL_NUMBER_FORMAT = 4
        private const val LEVEL_CHPX_SIZE = 24
        private const val LEVEL_PAPX_SIZE = 25
        private const val OVERRIDE_SIZE = 16
        private const val MAX_OVERRIDES = 10_000

        fun read(table: ByteArray, lists: StreamRange, overrides: StreamRange): ListTables? {
            if (lists.length <= 0 || overrides.length <= 0) return null
            val cursor = Cursor(table, lists.offset + Short.SIZE_BYTES)
            val headers = List(table.uint16(lists.offset)) { cursor.readListHeader() }
            val levels = headers.associate { (id, simple) ->
                id to List(if (simple) 1 else MAX_LEVELS) { cursor.readLevel() }
            }
            val overrideCount = table.int32(overrides.offset).coerceIn(0, MAX_OVERRIDES)
            val firstOverride = overrides.offset + Int.SIZE_BYTES
            val listIds = IntArray(overrideCount) { table.int32(firstOverride + it * OVERRIDE_SIZE) }
            return ListTables(levels, listIds)
        }

        // LSTF: the list id and whether it is a one-level ("simple") list.
        private fun Cursor.readListHeader(): Pair<Int, Boolean> {
            val id = bytes.int32(position)
            val simple = bytes.uint8(position + SIMPLE_LIST_FLAGS) and 1 != 0
            position += LIST_HEADER_SIZE
            return id to simple
        }

        // LVL: a fixed LVLF header, its formatting grpprls, then the number template as a counted UTF-16 string.
        private fun Cursor.readLevel(): ListLevel {
            val start = bytes.int32(position)
            val format = bytes.uint8(position + LEVEL_NUMBER_FORMAT)
            val formattingSize = bytes.uint8(position + LEVEL_CHPX_SIZE) + bytes.uint8(position + LEVEL_PAPX_SIZE)
            position += LEVEL_HEADER_SIZE + formattingSize
            val length = bytes.uint16(position)
            val template = bytes.utf16(position + Short.SIZE_BYTES, length)
            position += Short.SIZE_BYTES + length * Char.SIZE_BYTES
            return ListLevel(start, format, template)
        }
    }

    private class Cursor(val bytes: ByteArray, var position: Int)
}
