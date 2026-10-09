package io.github.volovatovta.docviewer.core

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

public object TextDecoding {

    public class Decoded(public val text: String, public val charsetName: String)

    // BOM → strict UTF-8 → Windows-1251 / Windows-1252 picked by how high bytes cluster.
    public fun decode(bytes: ByteArray, length: Int = bytes.size): Decoded {
        val n = minOf(length, bytes.size)
        when {
            n >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte() ->
                return Decoded(String(bytes, 3, n - 3, Charsets.UTF_8), "UTF-8")
            n >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() ->
                return Decoded(String(bytes, 2, n - 2, Charsets.UTF_16LE), "UTF-16LE")
            n >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() ->
                return Decoded(String(bytes, 2, n - 2, Charsets.UTF_16BE), "UTF-16BE")
        }
        if (looksLikeUtf16Le(bytes, n)) return Decoded(String(bytes, 0, n, Charsets.UTF_16LE), "UTF-16LE")
        strictUtf8(bytes, n)?.let { return Decoded(it, "UTF-8") }
        val charset = guessSingleByteCharset(bytes, n)
        return Decoded(String(bytes, 0, n, charset), charset.name())
    }

    private fun strictUtf8(bytes: ByteArray, n: Int): String? {
        // A multi-byte sequence may be cut at the end of a sample, so tolerate up to 3 trailing bytes.
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        for (trim in 0..3) {
            if (trim > n) break
            try {
                val text = decoder.reset().decode(ByteBuffer.wrap(bytes, 0, n - trim)).toString()
                return if (trim == 0) text else text + String(bytes, n - trim, trim, Charsets.UTF_8)
            } catch (_: CharacterCodingException) {
            }
        }
        return null
    }

    private fun looksLikeUtf16Le(bytes: ByteArray, n: Int): Boolean {
        if (n < 8) return false
        var zerosAtOdd = 0
        val pairs = minOf(n, 2048) / 2
        for (i in 0 until pairs) if (bytes[i * 2 + 1] == 0.toByte() && bytes[i * 2] != 0.toByte()) zerosAtOdd++
        return zerosAtOdd > pairs * 0.6
    }

    internal fun guessSingleByteCharset(bytes: ByteArray, n: Int): Charset {
        // Cyrillic text in cp1251 is made of runs of high bytes; Latin-1 accents are isolated between ASCII letters.
        var high = 0
        var highWithHighNeighbour = 0
        for (i in 0 until n) {
            if (bytes[i].toInt() and 0x80 == 0) continue
            high++
            val prevHigh = i > 0 && bytes[i - 1].toInt() and 0x80 != 0
            val nextHigh = i + 1 < n && bytes[i + 1].toInt() and 0x80 != 0
            if (prevHigh || nextHigh) highWithHighNeighbour++
        }
        val cyrillic = high > 0 && highWithHighNeighbour > high / 2
        return Charset.forName(if (cyrillic) "windows-1251" else "windows-1252")
    }
}
