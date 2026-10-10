package io.github.volovatovta.docviewer.core.stream

import io.github.volovatovta.docviewer.core.TextDecoding
import io.github.volovatovta.docviewer.core.readPrefix
import java.io.File
import java.nio.charset.Charset

// Encoding of a file that can be streamed byte-wise: line breaks must be single bytes, which rules out UTF-16.
internal class StreamEncoding(val charset: Charset, val firstByte: Long, val sample: String) {
    companion object {
        private const val SAMPLE_BYTES = 64 * 1024
        private val Utf8Bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())

        fun of(file: File): StreamEncoding? {
            val head = file.readPrefix(SAMPLE_BYTES)
            val decoded = TextDecoding.decode(head)
            if (decoded.charsetName.startsWith("UTF-16")) return null
            val bom = head.size >= Utf8Bom.size && Utf8Bom.indices.all { head[it] == Utf8Bom[it] }
            return StreamEncoding(
                Charset.forName(decoded.charsetName),
                if (bom) Utf8Bom.size.toLong() else 0L,
                decoded.text
            )
        }
    }
}
