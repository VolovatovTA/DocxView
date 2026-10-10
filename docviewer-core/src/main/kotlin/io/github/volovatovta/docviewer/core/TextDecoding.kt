package io.github.volovatovta.docviewer.core

import java.nio.Buffer
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

public object TextDecoding {

    public class Decoded(public val text: String, public val charsetName: String)

    private class ByteOrderMark(val bytes: ByteArray, val charset: Charset, val label: String)

    private val byteOrderMarks = listOf(
        ByteOrderMark(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()), Charsets.UTF_8, "UTF-8"),
        ByteOrderMark(byteArrayOf(0xFF.toByte(), 0xFE.toByte()), Charsets.UTF_16LE, "UTF-16LE"),
        ByteOrderMark(byteArrayOf(0xFE.toByte(), 0xFF.toByte()), Charsets.UTF_16BE, "UTF-16BE"),
    )

    private const val CYRILLIC_CHARSET = "windows-1251"
    private const val WESTERN_CHARSET = "windows-1252"
    private const val HIGH_BIT = 0x80
    private const val UTF16_MIN_BYTES = 8
    private const val UTF16_SAMPLE_BYTES = 2048
    private const val UTF16_ZERO_HIGH_BYTE_SHARE = 0.6

    // BOM → UTF-16 without BOM → strict UTF-8 → Windows-1251 / Windows-1252 picked by how high bytes cluster.
    public fun decode(bytes: ByteArray, length: Int = bytes.size): Decoded {
        val size = minOf(length, bytes.size)
        return decodeByByteOrderMark(bytes, size)
            ?: decodeUtf16WithoutMark(bytes, size)
            ?: decodeStrictUtf8(bytes, size)?.let { Decoded(it, "UTF-8") }
            ?: decodeSingleByte(bytes, size)
    }

    private fun decodeByByteOrderMark(bytes: ByteArray, size: Int): Decoded? {
        val mark = byteOrderMarks.firstOrNull { bytes.startsWith(it.bytes, size) } ?: return null
        val text = String(bytes, mark.bytes.size, size - mark.bytes.size, mark.charset)
        return Decoded(text, mark.label)
    }

    private fun decodeUtf16WithoutMark(bytes: ByteArray, size: Int): Decoded? {
        if (size < UTF16_MIN_BYTES) return null
        val pairs = minOf(size, UTF16_SAMPLE_BYTES) / 2
        // Latin text in UTF-16LE has a zero high byte after almost every character.
        val zeroHighBytes = (0 until pairs).count { bytes[it * 2 + 1] == ZERO && bytes[it * 2] != ZERO }
        if (zeroHighBytes <= pairs * UTF16_ZERO_HIGH_BYTE_SHARE) return null
        return Decoded(String(bytes, 0, size, Charsets.UTF_16LE), "UTF-16LE")
    }

    private fun decodeStrictUtf8(bytes: ByteArray, size: Int): String? {
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val input = ByteBuffer.wrap(bytes, 0, size)
        val output = CharBuffer.allocate(size)
        // Not the end of input: a character cut off by the end of a sample is left unconsumed instead of failing.
        if (decoder.decode(input, output, false).isError) return null
        val cutCharacter = String(bytes, input.position(), input.remaining(), Charsets.UTF_8)
        // Through Buffer: the covariant CharBuffer.flip() of JDK 9+ does not exist on older Android.
        val buffer: Buffer = output
        buffer.flip()
        return output.toString() + cutCharacter
    }

    private fun decodeSingleByte(bytes: ByteArray, size: Int): Decoded {
        val charset = guessSingleByteCharset(bytes, size)
        return Decoded(String(bytes, 0, size, charset), charset.name())
    }

    // Cyrillic text in cp1251 is made of runs of high bytes; Latin-1 accents are isolated between ASCII letters.
    internal fun guessSingleByteCharset(bytes: ByteArray, n: Int): Charset {
        var high = 0
        var clustered = 0
        for (index in 0 until n) {
            if (bytes[index].isHigh()) {
                high++
                if (bytes.hasHighNeighbour(index, n)) clustered++
            }
        }
        val cyrillic = high > 0 && clustered > high / 2
        return Charset.forName(if (cyrillic) CYRILLIC_CHARSET else WESTERN_CHARSET)
    }

    private fun Byte.isHigh() = toInt() and HIGH_BIT != 0

    private fun ByteArray.hasHighNeighbour(index: Int, size: Int): Boolean =
        (index > 0 && this[index - 1].isHigh()) || (index + 1 < size && this[index + 1].isHigh())

    private fun ByteArray.startsWith(prefix: ByteArray, size: Int): Boolean =
        size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }

    private const val ZERO: Byte = 0
}
