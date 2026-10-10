package io.github.volovatovta.docviewer.core.doc

// Formatted disk pages ([MS-DOC] 2.9.71-72): 512-byte pages that map text ranges to CHPX/PAPX formatting.
internal object FormattedDiskPages {

    fun readCharacterRuns(word: ByteArray, table: ByteArray, binTable: StreamRange): PropertyRuns<CharacterProperties> =
        readRuns(word, table, binTable, CharacterProperties()) { page, runCount, index ->
            val grpprlOffset = word.uint8(page + (runCount + 1) * Int.SIZE_BYTES + index) * Short.SIZE_BYTES
            if (grpprlOffset == 0) {
                CharacterProperties()
            } else {
                val start = page + grpprlOffset + 1
                CharacterProperties.read(word, start until start + word.uint8(page + grpprlOffset))
            }
        }

    fun readParagraphRuns(word: ByteArray, table: ByteArray, binTable: StreamRange): PropertyRuns<ParagraphProperties> =
        readRuns(word, table, binTable, ParagraphProperties()) { page, runCount, index ->
            val entry = page + (runCount + 1) * Int.SIZE_BYTES + index * Fkp.PAPX_ENTRY_SIZE
            val papxOffset = word.uint8(entry) * Short.SIZE_BYTES
            if (papxOffset == 0) {
                ParagraphProperties()
            } else {
                ParagraphProperties.read(word, papxRange(word, page, page + papxOffset))
            }
        }

    // PapxInFkp: a zero count byte means the real count follows; counts are in 2-byte words.
    private fun papxRange(word: ByteArray, page: Int, papx: Int): IntRange {
        val count = word.uint8(papx)
        val start = if (count == 0) papx + 2 else papx + 1
        val size = if (count == 0) word.uint8(papx + 1) * Short.SIZE_BYTES else count * Short.SIZE_BYTES - 1
        return start until minOf(start + size, page + Fkp.RUN_COUNT_OFFSET)
    }

    private fun <T> readRuns(
        word: ByteArray,
        table: ByteArray,
        binTable: StreamRange,
        default: T,
        readProperties: (page: Int, runCount: Int, index: Int) -> T,
    ): PropertyRuns<T> {
        val runs = pageOffsets(word, table, binTable).flatMap { page ->
            val runCount = word.uint8(page + Fkp.RUN_COUNT_OFFSET)
            List(runCount) { index ->
                PropertyRuns.Run(
                    start = word.int32(page + index * Int.SIZE_BYTES),
                    end = word.int32(page + (index + 1) * Int.SIZE_BYTES),
                    properties = readProperties(page, runCount, index),
                )
            }
        }
        return PropertyRuns(runs, default)
    }

    // PlcBteChpx/PlcBtePapx: n + 1 text offsets followed by n page numbers.
    private fun pageOffsets(word: ByteArray, table: ByteArray, binTable: StreamRange): List<Int> {
        if (binTable.length < Fkp.MIN_BIN_TABLE_LENGTH || !binTable.isInside(table)) return emptyList()
        val pageCount = (binTable.length - Int.SIZE_BYTES) / (2 * Int.SIZE_BYTES)
        val pageNumbers = binTable.offset + (pageCount + 1) * Int.SIZE_BYTES
        return (0 until pageCount)
            .map { (table.int32(pageNumbers + it * Int.SIZE_BYTES) and Fkp.PAGE_NUMBER_MASK) * Fkp.PAGE_SIZE }
            .filter { it in 0..word.size - Fkp.PAGE_SIZE }
    }

    private object Fkp {
        const val PAGE_SIZE = 512
        const val RUN_COUNT_OFFSET = 511
        const val PAPX_ENTRY_SIZE = 13
        const val PAGE_NUMBER_MASK = 0x3FFFFF
        const val MIN_BIN_TABLE_LENGTH = 12
    }
}
