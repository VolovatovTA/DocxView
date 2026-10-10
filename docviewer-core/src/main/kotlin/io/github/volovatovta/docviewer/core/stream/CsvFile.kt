package io.github.volovatovta.docviewer.core.stream

import io.github.volovatovta.docviewer.core.CsvHeaderDetector
import io.github.volovatovta.docviewer.core.CsvHeaderMode
import io.github.volovatovta.docviewer.core.CsvOptions
import io.github.volovatovta.docviewer.core.CsvParser
import io.github.volovatovta.docviewer.core.CsvReader
import io.github.volovatovta.docviewer.core.model.TableSource
import java.io.File

/** A CSV/TSV file read row by row from disk; there is no row limit and memory stays small. */
public class CsvFile private constructor(
    private val records: RecordFile,
    override val header: List<String>?,
    override val columnCount: Int,
    override val delimiter: Char,
    public val charsetName: String,
) : TableSource {

    private val firstRow = if (header != null) 1 else 0

    override val rowCount: Int get() = records.recordCount - firstRow

    override fun row(index: Int): List<String> = parse(records.record(index + firstRow))

    override fun forEachRow(action: (index: Int, row: List<String>) -> Unit) {
        records.forEachRecord { index, record -> if (index >= firstRow) action(index - firstRow, parse(record)) }
    }

    override fun close(): Unit = records.close()

    private fun parse(record: String): List<String> = CsvReader(record, delimiter).rows().firstOrNull() ?: listOf("")

    public companion object {
        private const val HEADER_SAMPLE_ROWS = 101
        private const val ASCII_LIMIT = 0x80

        /** Null when the file cannot be streamed (UTF-16, a non-ASCII delimiter); parse it with [CsvParser] then. */
        public fun open(file: File, options: CsvOptions = CsvOptions()): CsvFile? {
            val encoding = StreamEncoding.of(file) ?: return null
            val tsvHint = file.extension.equals("tsv", ignoreCase = true)
            val delimiter = options.delimiter
                ?: CsvParser.detectDelimiter(encoding.sample)
                ?: CsvParser.fallbackDelimiter(tsvHint)
            if (delimiter.code >= ASCII_LIMIT) return null
            var columns = 0
            val records = RecordFile.index(
                file = file,
                firstByte = encoding.firstByte,
                charset = encoding.charset,
                keepBlank = false,
                newSplitter = { CsvRowSplitter(delimiter.code) },
                onRecordEnd = { splitter ->
                    columns = maxOf(columns, (splitter as CsvRowSplitter).finishedRecordFields)
                },
            )
            val header = detectHeader(records, delimiter, options.header)
            return CsvFile(records, header, columns, delimiter, encoding.charset.name())
        }

        private fun detectHeader(records: RecordFile, delimiter: Char, mode: CsvHeaderMode): List<String>? {
            if (records.recordCount == 0 || mode == CsvHeaderMode.None) return null
            val sample = (0 until minOf(records.recordCount, HEADER_SAMPLE_ROWS)).map { index ->
                CsvReader(records.record(index), delimiter).rows().firstOrNull() ?: listOf("")
            }
            val isHeader = mode == CsvHeaderMode.FirstRow || CsvHeaderDetector.looksLikeHeader(sample)
            return sample.first().takeIf { isHeader }
        }
    }
}
