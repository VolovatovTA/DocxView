package io.github.volovatovta.docviewer.core.doc

import io.github.volovatovta.docviewer.core.CorruptedDocumentException
import io.github.volovatovta.docviewer.core.binary.int32
import io.github.volovatovta.docviewer.core.binary.uint16
import io.github.volovatovta.docviewer.core.binary.uint8

// The document text with, for every character, its byte offset in the WordDocument stream.
internal class DecodedText(val chars: CharArray, val offsets: IntArray)

// The piece table ([MS-DOC] 2.9.38 Clx): maps character positions to where the text bytes live.
internal class PieceTable private constructor(
    private val pieceStarts: IntArray,
    private val pieces: List<Piece>,
) {
    private class Piece(val streamOffset: Int, val compressed: Boolean) {
        val bytesPerChar: Int get() = if (compressed) Byte.SIZE_BYTES else Char.SIZE_BYTES
    }

    fun decode(word: ByteArray, textLength: Int): DecodedText {
        val total = minOf(textLength.coerceAtLeast(0), pieceStarts.last())
        val chars = CharArray(total)
        val offsets = IntArray(total)
        pieces.forEachIndexed { index, piece ->
            val start = pieceStarts[index]
            for (position in start until minOf(pieceStarts[index + 1], total)) {
                val offset = piece.streamOffset + (position - start) * piece.bytesPerChar
                offsets[position] = offset
                chars[position] = word.charAt(offset, piece.compressed)
            }
        }
        return DecodedText(chars, offsets)
    }

    // Characters outside the stream become spaces, as Word itself would show nothing there.
    private fun ByteArray.charAt(offset: Int, compressed: Boolean): Char = when {
        compressed && offset < size -> Windows1252.decode(this[offset])
        !compressed && offset + 1 < size -> uint16(offset).toChar()
        else -> ' '
    }

    companion object {
        private const val CLXT_PRC = 1
        private const val CLXT_PCDT = 2
        private const val PRC_HEADER_SIZE = 3
        private const val PCDT_HEADER_SIZE = 5
        private const val PCD_SIZE = 8
        private const val PCD_FC_OFFSET = 2
        private const val FC_COMPRESSED_FLAG = 0x40000000
        private const val FC_VALUE_MASK = 0x3FFFFFFF

        fun read(table: ByteArray, clx: StreamRange): PieceTable {
            if (!clx.isInside(table)) throw CorruptedDocumentException("Missing piece table")
            val pcdt = skipPropertyModifiers(table, clx)
            val plcLength = table.int32(pcdt + 1)
            val plc = StreamRange(pcdt + PCDT_HEADER_SIZE, plcLength)
            val pieceCount = (plcLength - Int.SIZE_BYTES) / (Int.SIZE_BYTES + PCD_SIZE)
            if (pieceCount <= 0 || !plc.isInside(table)) throw CorruptedDocumentException("Bad piece table size")
            val starts = IntArray(pieceCount + 1) { table.int32(plc.offset + it * Int.SIZE_BYTES) }.requireAscending()
            val descriptors = plc.offset + starts.size * Int.SIZE_BYTES
            val pieces = List(pieceCount) { readPiece(table, descriptors + it * PCD_SIZE) }
            return PieceTable(starts, pieces)
        }

        // A Clx is a run of Prc entries (clxt 1) followed by exactly one Pcdt (clxt 2).
        private fun skipPropertyModifiers(table: ByteArray, clx: StreamRange): Int {
            var position = clx.offset
            while (position < clx.end && table.uint8(position) == CLXT_PRC) {
                position += PRC_HEADER_SIZE + table.uint16(position + 1)
            }
            if (position >= clx.end || table.uint8(position) != CLXT_PCDT) {
                throw CorruptedDocumentException("Bad piece table")
            }
            return position
        }

        private fun IntArray.requireAscending(): IntArray {
            if (first() < 0 || asList().zipWithNext().any { (previous, next) -> next < previous }) {
                throw CorruptedDocumentException("Piece table positions are out of order")
            }
            return this
        }

        // FcCompressed: compressed text is addressed in half-byte units, hence the division.
        private fun readPiece(table: ByteArray, descriptor: Int): Piece {
            val fc = table.int32(descriptor + PCD_FC_OFFSET)
            val compressed = fc and FC_COMPRESSED_FLAG != 0
            val value = fc and FC_VALUE_MASK
            return Piece(if (compressed) value / 2 else value, compressed)
        }
    }
}

private object Windows1252 {
    private val table: CharArray = String(ByteArray(1 shl Byte.SIZE_BITS) { it.toByte() }, charset("windows-1252"))
        .toCharArray()

    fun decode(byte: Byte): Char = table[byte.toInt() and (table.size - 1)]
}
