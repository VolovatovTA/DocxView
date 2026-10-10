package io.github.volovatovta.docviewer.core.doc

import io.github.volovatovta.docviewer.core.CorruptedDocumentException

// Position and length of a structure inside a stream, as stored in the FIB's fc/lcb pairs.
internal class StreamRange(val offset: Int, val length: Int) {
    val end: Int get() = offset + length

    fun isInside(stream: ByteArray): Boolean = length > 0 && offset >= 0 && end <= stream.size
}

// Entries of FibRgFcLcb97 the parser needs, by their index in the fc/lcb array.
internal enum class FibEntry(val index: Int) {
    PlcfBteChpx(12),
    PlcfBtePapx(13),
    Clx(33),
    PlfLst(73),
    PlfLfo(74),
}

// File Information Block ([MS-DOC] 2.5.1): the table of contents of the WordDocument stream.
internal class Fib(private val word: ByteArray) {

    init {
        if (word.size < FibBase.SIZE || word.uint16(FibBase.IDENTIFIER) != FibBase.WORD_IDENTIFIER) {
            throw CorruptedDocumentException("Bad Word FIB")
        }
    }

    private val flags = word.uint16(FibBase.FLAGS)

    val isWord97OrLater: Boolean = word.uint16(FibBase.VERSION) >= FibBase.FIRST_WORD_97_VERSION
    val isEncrypted: Boolean = flags and FibBase.ENCRYPTED_FLAG != 0
    val tableStreamName: String = if (flags and FibBase.TABLE_1_FLAG != 0) TABLE_1_STREAM else TABLE_0_STREAM

    // Word 6/95 stores its text contiguously between fcMin and fcMac.
    val legacyText: StreamRange
        get() {
            val start = word.int32(FibBase.LEGACY_TEXT_START)
            return StreamRange(start, word.int32(FibBase.LEGACY_TEXT_END) - start)
        }

    // The variable-length part only exists in Word 97+ files, so it is located on first use.
    private val layout by lazy { VariableLayout(word) }

    // Number of characters in the main document text (FibRgLw97.ccpText).
    val mainTextLength: Int get() = word.int32(layout.longsOffset + CCP_TEXT_INDEX * Int.SIZE_BYTES)

    fun range(entry: FibEntry): StreamRange {
        if (entry.index >= layout.fcLcbCount) return StreamRange(0, 0)
        val offset = layout.fcLcbOffset + entry.index * FC_LCB_PAIR_SIZE
        return StreamRange(word.int32(offset), word.int32(offset + Int.SIZE_BYTES))
    }

    // FibRgW97, FibRgLw97 and FibRgFcLcb follow FibBase, each prefixed with its element count.
    private class VariableLayout(word: ByteArray) {
        private val longsCountOffset = FibBase.SIZE + Short.SIZE_BYTES + word.uint16(FibBase.SIZE) * Short.SIZE_BYTES
        val longsOffset = longsCountOffset + Short.SIZE_BYTES
        private val fcLcbCountOffset = longsOffset + word.uint16(longsCountOffset) * Int.SIZE_BYTES
        val fcLcbCount = word.uint16(fcLcbCountOffset)
        val fcLcbOffset = fcLcbCountOffset + Short.SIZE_BYTES
    }

    private companion object {
        const val TABLE_0_STREAM = "0Table"
        const val TABLE_1_STREAM = "1Table"
        const val CCP_TEXT_INDEX = 3
        const val FC_LCB_PAIR_SIZE = 2 * Int.SIZE_BYTES
    }
}

private object FibBase {
    const val SIZE = 32
    const val IDENTIFIER = 0x00
    const val VERSION = 0x02
    const val FLAGS = 0x0A
    const val LEGACY_TEXT_START = 0x18
    const val LEGACY_TEXT_END = 0x1C
    const val WORD_IDENTIFIER = 0xA5EC
    const val FIRST_WORD_97_VERSION = 0xC1
    const val ENCRYPTED_FLAG = 0x0100
    const val TABLE_1_FLAG = 0x0200
}
