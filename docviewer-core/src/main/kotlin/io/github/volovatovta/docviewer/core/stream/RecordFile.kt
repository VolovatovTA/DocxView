package io.github.volovatovta.docviewer.core.stream

import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.charset.Charset

// Decides, byte by byte, where records (lines or CSV rows) start; a fresh splitter may start at any record start.
internal interface RecordSplitter {
    // True when a new record starts at this byte; the byte then belongs to the new record.
    fun startsRecordAt(byte: Int): Boolean

    fun isLineBreak(byte: Int): Boolean = byte == LF || byte == CR

    // Called once after the last byte, so the final record can be completed like the others.
    fun endOfInput() {}

    companion object {
        const val LF = '\n'.code
        const val CR = '\r'.code
    }
}

// Lines end at LF, CRLF or a lone CR; long lines wrap after a space past softLimit, or hard at hardLimit
// but never inside a UTF-8 sequence.
internal class LineSplitter(private val softLimit: Int, private val hardLimit: Int) : RecordSplitter {
    private var previous = NONE
    private var length = 0

    override fun startsRecordAt(byte: Int): Boolean {
        val endedLine = previous == RecordSplitter.LF || previous == RecordSplitter.CR && byte != RecordSplitter.LF
        val atSpace = length >= softLimit && previous == SPACE
        val atLimit = length >= hardLimit && !isUtf8Continuation(byte)
        val starts = endedLine || !isLineBreak(byte) && (atSpace || atLimit)
        length = if (starts) 1 else length + 1
        previous = byte
        return starts
    }

    private fun isUtf8Continuation(byte: Int) = byte and UTF8_LEAD_MASK == UTF8_CONTINUATION

    private companion object {
        const val NONE = -1
        const val SPACE = ' '.code
        const val UTF8_LEAD_MASK = 0xC0
        const val UTF8_CONTINUATION = 0x80
    }
}

// CSV rows end at a line break outside a quoted field. Quotes follow CsvReader exactly: only a quote that opens
// a field starts a quoted value, "" inside it is a literal quote, and text after the closing quote stays in the field.
// A quote never closed is given up on after MAX_RECORD_BYTES, and a record without line breaks is cut there,
// so one stray quote or one endless line cannot turn the rest of the file into a single record.
internal class CsvRowSplitter(private val delimiter: Int) : RecordSplitter {
    private enum class State { FieldStart, Plain, Quoted, AfterQuote }

    private var state = State.FieldStart
    private var previous = NONE
    private var lineBroken = false
    private var quotedBytes = 0
    private var recordBytes = 0
    private var fields = 1

    // Fields of the record that has just ended, for the table's column count.
    var finishedRecordFields = 0
        private set

    override fun endOfInput() {
        finishedRecordFields = fields
    }

    override fun startsRecordAt(byte: Int): Boolean {
        val tooLong = recordBytes >= MAX_RECORD_BYTES && state != State.Quoted
        val endedRow = tooLong || lineBroken && !(previous == RecordSplitter.CR && byte == RecordSplitter.LF)
        recordBytes = if (endedRow) 1 else recordBytes + 1
        if (endedRow) {
            finishedRecordFields = fields
            fields = 1
            state = State.FieldStart
        }
        lineBroken = false
        when (state) {
            State.FieldStart -> if (byte == QUOTE) openQuote() else unquoted(byte)
            State.Plain -> unquoted(byte)
            State.Quoted -> quoted(byte)
            State.AfterQuote -> if (byte == QUOTE) state = State.Quoted else unquoted(byte)
        }
        previous = byte
        return endedRow
    }

    private fun openQuote() {
        state = State.Quoted
        quotedBytes = 0
    }

    private fun quoted(byte: Int) {
        when {
            byte == QUOTE -> state = State.AfterQuote
            ++quotedBytes > MAX_RECORD_BYTES -> state = State.Plain
        }
    }

    private fun unquoted(byte: Int) {
        when {
            byte == delimiter -> {
                fields++
                state = State.FieldStart
            }
            isLineBreak(byte) -> lineBroken = true
            else -> state = State.Plain
        }
    }

    private companion object {
        const val NONE = -1
        const val QUOTE = '"'.code
        const val MAX_RECORD_BYTES = 1 shl 20
    }
}

// A file read record by record without loading it: every BLOCK-th record start is remembered, the rest is
// re-split on demand from the nearest checkpoint. Blank records are kept or dropped the same way in both passes.
internal class RecordFile(
    private val file: File,
    private val charset: Charset,
    private val checkpoints: LongArray,
    val recordCount: Int,
    private val keepBlank: Boolean,
    private val newSplitter: () -> RecordSplitter,
) : Closeable {

    private val input = RandomAccessFile(file, "r")

    // Records of one block; endsLine tells a real line end from a wrap of an over-long line.
    private class Block(val texts: List<String>, val endsLine: BooleanArray)

    private val cache = object : LinkedHashMap<Int, Block>(CACHE_CAPACITY, LOAD_FACTOR, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, Block>) = size > CACHED_BLOCKS
    }

    // A file edited after indexing may hold fewer records in a block than were counted.
    fun record(index: Int): String = blockOf(index).texts.getOrElse(index % BLOCK) { "" }

    fun endsLine(index: Int): Boolean = blockOf(index).endsLine.getOrElse(index % BLOCK) { true }

    // Sequential pass for search; it bypasses the cache so the on-screen blocks stay cached.
    fun forEachRecord(action: (index: Int, record: String) -> Unit) {
        for (block in checkpoints.indices) {
            val records = synchronized(this) { readBlock(block) }
            records.texts.forEachIndexed { offset, record -> action(block * BLOCK + offset, record) }
        }
    }

    private fun blockOf(index: Int): Block {
        require(index in 0 until recordCount) { "record $index of $recordCount" }
        val block = index / BLOCK
        return synchronized(this) { cache.getOrPut(block) { readBlock(block) } }
    }

    // A file that shrank or vanished while on screen reads as blank records rather than failing the composition.
    private fun readBlock(block: Int): Block {
        val start = checkpoints[block]
        val end = if (block + 1 < checkpoints.size) checkpoints[block + 1] else file.length()
        val bytes = ByteArray((end - start).coerceAtLeast(0).toInt())
        try {
            input.seek(start)
            input.readFully(bytes)
        } catch (_: IOException) {
            return Block(List(BLOCK) { "" }, BooleanArray(BLOCK) { true })
        }
        val ranges = splitRecords(bytes, newSplitter(), keepBlank)
        val endsLine = BooleanArray(ranges.size) { bytes.isLineBreakAt(ranges[it].last) }
        if (ranges.isNotEmpty() && end == file.length()) endsLine[ranges.lastIndex] = true
        return Block(ranges.map { range -> decode(bytes, range) }, endsLine)
    }

    private fun decode(bytes: ByteArray, range: IntRange): String {
        var last = range.last
        while (last >= range.first && bytes.isLineBreakAt(last)) last--
        return String(bytes, range.first, last - range.first + 1, charset)
    }

    private fun ByteArray.isLineBreakAt(index: Int): Boolean =
        this[index].toInt() == RecordSplitter.LF || this[index].toInt() == RecordSplitter.CR

    override fun close() = synchronized(this) { input.close() }

    companion object {
        const val BLOCK = 64
        private const val CACHED_BLOCKS = 48
        private const val CACHE_CAPACITY = 64
        private const val LOAD_FACTOR = 0.75f
        private const val READ_BUFFER = 64 * 1024

        // One pass over the file; onRecordEnd sees every non-blank record's splitter state before it resets.
        fun index(
            file: File,
            firstByte: Long,
            charset: Charset,
            keepBlank: Boolean,
            newSplitter: () -> RecordSplitter,
            onRecordEnd: (RecordSplitter) -> Unit = {},
        ): RecordFile {
            val indexer = RecordIndexer(firstByte, keepBlank, newSplitter(), onRecordEnd)
            val buffer = ByteArray(READ_BUFFER)
            RandomAccessFile(file, "r").use { input ->
                input.seek(firstByte)
                var read = input.read(buffer)
                while (read > 0) {
                    for (index in 0 until read) indexer.accept(buffer[index].toInt() and BYTE_MASK)
                    read = input.read(buffer)
                }
            }
            indexer.finish()
            return RecordFile(file, charset, indexer.checkpoints.toArray(), indexer.count, keepBlank, newSplitter)
        }

        // Re-splits one block exactly as indexing did, dropping blank records the same way.
        internal fun splitRecords(bytes: ByteArray, splitter: RecordSplitter, keepBlank: Boolean): List<IntRange> {
            val ranges = ArrayList<IntRange>(BLOCK)
            var start = 0
            var hasContent = false
            for (index in bytes.indices) {
                val byte = bytes[index].toInt() and BYTE_MASK
                if (splitter.startsRecordAt(byte) && index > start) {
                    if (hasContent || keepBlank) ranges += start until index
                    start = index
                    hasContent = false
                }
                if (!splitter.isLineBreak(byte)) hasContent = true
            }
            if (hasContent || keepBlank && start < bytes.size) ranges += start until bytes.size
            return ranges
        }

        private const val BYTE_MASK = 0xFF
    }
}

// Counts records byte by byte and remembers where every BLOCK-th one starts.
private class RecordIndexer(
    private var recordStart: Long,
    private val keepBlank: Boolean,
    private val splitter: RecordSplitter,
    private val onRecordEnd: (RecordSplitter) -> Unit,
) {
    val checkpoints = LongArrayBuilder()
    var count = 0
        private set
    private var position = recordStart
    private var hasContent = false

    fun accept(byte: Int) {
        if (splitter.startsRecordAt(byte) && position > recordStart) {
            finishRecord()
            recordStart = position
            hasContent = false
        }
        if (!splitter.isLineBreak(byte)) hasContent = true
        position++
    }

    fun finish() {
        splitter.endOfInput()
        finishRecord()
    }

    private fun finishRecord() {
        if (!hasContent && !(keepBlank && position > recordStart)) return
        if (count % RecordFile.BLOCK == 0) checkpoints += recordStart
        onRecordEnd(splitter)
        count++
    }
}

private class LongArrayBuilder {
    private var values = LongArray(INITIAL_CAPACITY)
    private var size = 0

    operator fun plusAssign(value: Long) {
        if (size == values.size) values = values.copyOf(size * 2)
        values[size++] = value
    }

    fun toArray(): LongArray = values.copyOf(size)

    private companion object {
        const val INITIAL_CAPACITY = 256
    }
}
