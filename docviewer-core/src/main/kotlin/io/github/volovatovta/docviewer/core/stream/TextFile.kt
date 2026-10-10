package io.github.volovatovta.docviewer.core.stream

import java.io.Closeable
import java.io.File

/** A text file read line by line from disk: memory stays small no matter how large the file is. */
public class TextFile private constructor(private val records: RecordFile, public val charsetName: String) : Closeable {

    public val lineCount: Int get() = records.recordCount

    /** Long lines arrive as several consecutive parts of at most [MAX_LINE_BYTES]. */
    public fun line(index: Int): String = records.record(index)

    /** False when the line is cut short and continues as the next line. */
    public fun endsLine(index: Int): Boolean = records.endsLine(index)

    public fun forEachLine(action: (index: Int, line: String) -> Unit): Unit = records.forEachRecord(action)

    override fun close(): Unit = records.close()

    public companion object {
        /** Longer lines (prose paragraphs, minified JSON) are cut so that no single line grows with the file. */
        public const val MAX_LINE_BYTES: Int = 800
        private const val WRAP_AFTER_BYTES = 600

        /** Null for encodings that cannot be streamed byte by byte (UTF-16); parse those in memory instead. */
        public fun open(file: File): TextFile? {
            val encoding = StreamEncoding.of(file) ?: return null
            val records = RecordFile.index(
                file = file,
                firstByte = encoding.firstByte,
                charset = encoding.charset,
                keepBlank = true,
                newSplitter = { LineSplitter(WRAP_AFTER_BYTES, MAX_LINE_BYTES) },
            )
            return TextFile(records, encoding.charset.name())
        }
    }
}
