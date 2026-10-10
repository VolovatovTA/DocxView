package io.github.volovatovta.docviewer.core.doc

import io.github.volovatovta.docviewer.core.CorruptedDocumentException

// Every multi-byte value in [MS-CFB] and [MS-DOC] is little-endian.
private object Bits {
    const val BYTE_MASK = 0xFF
}

// Reading past the end of a stream means the file is damaged, so it is reported as such rather than as a crash.
private fun ByteArray.requireReadable(offset: Int, length: Int) {
    if (offset < 0 || offset > size - length) {
        throw CorruptedDocumentException("Structure at offset $offset runs past the end of a $size-byte stream")
    }
}

internal fun ByteArray.uint8(offset: Int): Int {
    requireReadable(offset, Byte.SIZE_BYTES)
    return this[offset].toInt() and Bits.BYTE_MASK
}

internal fun ByteArray.uint16(offset: Int): Int {
    requireReadable(offset, Short.SIZE_BYTES)
    val low = this[offset].toInt() and Bits.BYTE_MASK
    val high = this[offset + 1].toInt() and Bits.BYTE_MASK
    return low or (high shl Byte.SIZE_BITS)
}

internal fun ByteArray.int16(offset: Int): Int = uint16(offset).toShort().toInt()

internal fun ByteArray.int32(offset: Int): Int {
    requireReadable(offset, Int.SIZE_BYTES)
    var value = 0
    for (byte in Int.SIZE_BYTES - 1 downTo 0) {
        value = (value shl Byte.SIZE_BITS) or (this[offset + byte].toInt() and Bits.BYTE_MASK)
    }
    return value
}

internal fun ByteArray.uint32(offset: Int): Long = int32(offset).toUInt().toLong()

internal fun ByteArray.startsWith(prefix: ByteArray, at: Int = 0): Boolean =
    at >= 0 && at <= size - prefix.size && prefix.indices.all { this[at + it] == prefix[it] }

// UTF-16LE text stored as [length] code units.
internal fun ByteArray.utf16(offset: Int, length: Int): String =
    String(CharArray(length) { uint16(offset + it * Char.SIZE_BYTES).toChar() })
