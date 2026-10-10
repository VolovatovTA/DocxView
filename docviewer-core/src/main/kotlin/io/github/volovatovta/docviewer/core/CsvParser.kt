package io.github.volovatovta.docviewer.core

import io.github.volovatovta.docviewer.core.model.TableDocument
import java.io.File

public object CsvParser {

    private val delimiterCandidates = listOf(',', ';', '\t', '|')
    private const val DEFAULT_DELIMITER = ','
    private const val TAB = '\t'

    public fun parse(file: File, options: CsvOptions = CsvOptions()): TableDocument {
        val decoded = TextDecoding.decode(file.readBytes())
        val tsvHint = file.extension.equals("tsv", ignoreCase = true)
        return parse(decoded.text, options, decoded.charsetName, tsvHint)
    }

    public fun parse(
        text: String,
        options: CsvOptions = CsvOptions(),
        charsetName: String? = null,
        tsvHint: Boolean = false,
    ): TableDocument {
        val delimiter = options.delimiter
            ?: detectDelimiter(text)
            ?: if (tsvHint) TAB else DEFAULT_DELIMITER
        // One row more than header + maxRows tells whether the file was cut.
        val rows = CsvReader(text, delimiter).rows().take(options.maxRows + 2).toMutableList()
        val overflow = rows.size > options.maxRows + 1
        if (overflow) rows.removeAt(rows.lastIndex)
        while (rows.lastOrNull()?.all { it.isEmpty() } == true) rows.removeAt(rows.lastIndex)

        val hasHeader = when (options.header) {
            CsvHeaderMode.FirstRow -> rows.isNotEmpty()
            CsvHeaderMode.None -> false
            CsvHeaderMode.Auto -> CsvHeaderDetector.looksLikeHeader(rows)
        }
        val body = if (hasHeader) rows.drop(1) else rows
        return TableDocument(
            header = rows.firstOrNull()?.takeIf { hasHeader },
            rows = body.take(options.maxRows),
            delimiter = delimiter,
            columnCount = rows.maxOfOrNull { it.size } ?: 0,
            truncated = overflow || body.size > options.maxRows,
            charsetName = charsetName,
        )
    }

    // Picks the candidate that splits sample lines into the same, non-trivial number of fields most consistently.
    public fun detectDelimiter(text: String): Char? {
        val sample = text.take(DelimiterSniffing.SAMPLE_CHARS)
        val sampleIsCut = sample.length < text.length
        return delimiterCandidates
            .mapNotNull { candidate -> score(sample, candidate, sampleIsCut)?.let { candidate to it } }
            .maxByOrNull { (_, score) -> score }
            ?.first
    }

    private fun score(sample: String, candidate: Char, sampleIsCut: Boolean): Double? {
        val fieldCounts = CsvReader(sample, candidate).rows()
            .take(DelimiterSniffing.SAMPLE_ROWS)
            .map { it.size }
            .toMutableList()
        // The last row of a cut sample is probably incomplete.
        if (sampleIsCut && fieldCounts.size in 2 until DelimiterSniffing.SAMPLE_ROWS) {
            fieldCounts.removeAt(fieldCounts.lastIndex)
        }
        val (typicalCount, occurrences) = fieldCounts.groupingBy { it }.eachCount().maxByOrNull { it.value }
            ?.toPair()
            ?: return null
        val consistency = occurrences.toDouble() / fieldCounts.size
        if (typicalCount < 2 || consistency < DelimiterSniffing.MIN_CONSISTENCY) return null
        val fieldBonus = minOf(typicalCount, DelimiterSniffing.FIELD_COUNT_CAP) * DelimiterSniffing.FIELD_COUNT_WEIGHT
        return consistency * DelimiterSniffing.CONSISTENCY_WEIGHT + fieldBonus
    }

    public fun isNumeric(value: String): Boolean {
        val compact = value.trim().removeSuffix("%").filterNot { it == ' ' || it == NON_BREAKING_SPACE }
        if (compact.isEmpty() || compact.length > MAX_NUMBER_LENGTH) return false
        val allowedCharacters = compact.withIndex().all { (index, char) ->
            char.isDigit() || char in NUMBER_PUNCTUATION || char in CURRENCY_SYMBOLS || (char in SIGNS && index == 0)
        }
        return allowedCharacters && compact.any(Char::isDigit)
    }

    private const val NON_BREAKING_SPACE = '\u00A0'
    private const val MAX_NUMBER_LENGTH = 32
    private const val NUMBER_PUNCTUATION = ".,eE"
    private const val CURRENCY_SYMBOLS = "$€£₽¥"
    private const val SIGNS = "+-"

    private object DelimiterSniffing {
        const val SAMPLE_CHARS = 64 * 1024
        const val SAMPLE_ROWS = 50
        const val MIN_CONSISTENCY = 0.6
        const val CONSISTENCY_WEIGHT = 10.0
        const val FIELD_COUNT_CAP = 10
        const val FIELD_COUNT_WEIGHT = 0.1
    }
}
