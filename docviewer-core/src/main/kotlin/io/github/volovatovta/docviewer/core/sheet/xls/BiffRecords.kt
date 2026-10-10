package io.github.volovatovta.docviewer.core.sheet.xls

import io.github.volovatovta.docviewer.core.binary.uint16
import io.github.volovatovta.docviewer.core.binary.uint8

/**
 * A BIFF8 record: its type and its data, followed by the CONTINUE records that carry the rest of it
 * when the data does not fit in one record ([MS-XLS] 2.1.4).
 */
internal class BiffRecord(val type: Int, val segments: List<ByteArray>) {
    val data: ByteArray get() = segments.first()

    /** Reads strings that may cross from one segment into the next. */
    fun reader(offset: Int = 0) = SegmentedReader(segments, offset)
}

/** The records of a BIFF8 stream from [start] to the end of the substream (EOF) or of the stream. */
internal fun ByteArray.biffRecords(start: Int = 0): Sequence<BiffRecord> = sequence {
    var offset = start
    while (offset + HEADER_SIZE <= size) {
        val type = uint16(offset)
        val length = uint16(offset + 2)
        val segments = arrayListOf(copyOfRange(offset + HEADER_SIZE, minOf(size, offset + HEADER_SIZE + length)))
        offset += HEADER_SIZE + length
        while (offset + HEADER_SIZE <= size && uint16(offset) == CONTINUE) {
            val continuation = uint16(offset + 2)
            segments += copyOfRange(offset + HEADER_SIZE, minOf(size, offset + HEADER_SIZE + continuation))
            offset += HEADER_SIZE + continuation
        }
        yield(BiffRecord(type, segments))
        if (type == BiffType.EOF) break
    }
}

/**
 * A cursor over the segments of one record. Character data split across a CONTINUE boundary resumes with
 * a fresh one-byte flag that says whether the rest is one or two bytes per character.
 */
internal class SegmentedReader(private val segments: List<ByteArray>, private var offset: Int) {
    private var segment = 0

    val exhausted: Boolean
        get() = segment > segments.lastIndex || segment == segments.lastIndex && offset >= segments[segment].size

    fun uint8(): Int = advance(1).let { (bytes, at) -> bytes.uint8(at) }

    fun uint16(): Int = advance(2).let { (bytes, at) -> bytes.uint16(at) }

    fun int32(): Int = uint16() or (uint16() shl Short.SIZE_BITS)

    // The count comes from the file, so skipping stops where the data ends.
    fun skip(count: Int) {
        var left = count
        while (left-- > 0 && !exhausted) advance(1)
    }

    /** XLUnicodeString: a character count, a flags byte and the characters ([MS-XLS] 2.5.294). */
    fun unicodeString(lengthBytes: Int = 2): String {
        val length = if (lengthBytes == 1) uint8() else uint16()
        return characters(length, uint8())
    }

    /** XLUnicodeRichExtendedString of the shared string table ([MS-XLS] 2.5.293). */
    fun richString(): String {
        val length = uint16()
        val flags = uint8()
        val runs = if (flags and RICH != 0) uint16() else 0
        val extension = if (flags and EXTENDED != 0) int32() else 0
        val text = characters(length, flags)
        skip(runs * RUN_SIZE + extension)
        return text
    }

    private fun characters(length: Int, firstFlags: Int): String {
        val text = StringBuilder(length)
        var wide = firstFlags and HIGH_BYTE != 0
        while (text.length < length) {
            if (atSegmentEnd()) {
                nextSegment()
                wide = uint8() and HIGH_BYTE != 0
            }
            text.append((if (wide) uint16() else uint8()).toChar())
        }
        return text.toString()
    }

    private fun atSegmentEnd() = offset >= segments[segment].size && segment < segments.lastIndex

    private fun nextSegment() {
        segment++
        offset = 0
    }

    private fun advance(count: Int): Pair<ByteArray, Int> {
        if (atSegmentEnd()) nextSegment()
        val bytes = segments[segment]
        return (bytes to offset).also { offset += count }
    }

    private companion object {
        const val HIGH_BYTE = 0x01
        const val EXTENDED = 0x04
        const val RICH = 0x08
        const val RUN_SIZE = 4
    }
}

internal object BiffType {
    const val BOF = 0x0809
    const val EOF = 0x000A
    const val FILEPASS = 0x002F
    const val DATEMODE = 0x0022
    const val FORMAT = 0x041E
    const val XF = 0x00E0
    const val BOUNDSHEET = 0x0085
    const val SST = 0x00FC
    const val LABELSST = 0x00FD
    const val LABEL = 0x0204
    const val NUMBER = 0x0203
    const val RK = 0x027E
    const val MULRK = 0x00BD
    const val BOOLERR = 0x0205
    const val FORMULA = 0x0006
    const val STRING = 0x0207
}

private const val HEADER_SIZE = 4
private const val CONTINUE = 0x003C
