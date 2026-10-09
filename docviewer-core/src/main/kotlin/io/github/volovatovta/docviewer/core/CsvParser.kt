package io.github.volovatovta.docviewer.core

import io.github.volovatovta.docviewer.core.model.TableDocument
import java.io.File

public enum class CsvHeaderMode { Auto, FirstRow, None }

public data class CsvOptions(
    // null = detect from the content.
    val delimiter: Char? = null,
    val header: CsvHeaderMode = CsvHeaderMode.Auto,
    val maxRows: Int = 500_000,
)

public object CsvParser {

    private val CANDIDATES = charArrayOf(',', ';', '\t', '|')

    public fun parse(file: File, options: CsvOptions = CsvOptions()): TableDocument {
        val decoded = TextDecoding.decode(file.readBytes())
        return parse(decoded.text, options, decoded.charsetName, tsvHint = file.extension.equals("tsv", ignoreCase = true))
    }

    public fun parse(text: String, options: CsvOptions = CsvOptions(), charsetName: String? = null, tsvHint: Boolean = false): TableDocument {
        val delimiter = options.delimiter ?: detectDelimiter(text) ?: if (tsvHint) '\t' else ','
        val rows = ArrayList<List<String>>()
        var truncated = false
        tokenize(text, delimiter) { row ->
            if (rows.size >= options.maxRows + 1) {
                truncated = true
                false
            } else {
                rows += row
                true
            }
        }
        while (rows.isNotEmpty() && rows.last().all { it.isEmpty() }) rows.removeAt(rows.lastIndex)
        val columnCount = rows.maxOfOrNull { it.size } ?: 0
        val hasHeader = when (options.header) {
            CsvHeaderMode.FirstRow -> rows.isNotEmpty()
            CsvHeaderMode.None -> false
            CsvHeaderMode.Auto -> looksLikeHeader(rows)
        }
        val header = if (hasHeader) rows.first() else null
        val body = if (hasHeader) rows.subList(1, rows.size) else rows
        val limited = if (body.size > options.maxRows) body.subList(0, options.maxRows) else body
        return TableDocument(header, ArrayList(limited), delimiter, columnCount, truncated || body.size > options.maxRows, charsetName)
    }

    // Picks the candidate that splits sample lines into the same, non-trivial number of fields most consistently.
    public fun detectDelimiter(text: String): Char? {
        val sample = if (text.length > 64 * 1024) text.substring(0, 64 * 1024) else text
        var best: Char? = null
        var bestScore = 0.0
        for (candidate in CANDIDATES) {
            val counts = ArrayList<Int>()
            tokenize(sample, candidate) { row ->
                if (row.size > 1 || row.firstOrNull()?.isNotEmpty() == true) counts += row.size
                counts.size < 50
            }
            // The last row of a cut sample is probably incomplete.
            if (sample.length < text.length && counts.size in 2..49) counts.removeAt(counts.lastIndex)
            if (counts.isEmpty()) continue
            val mode = counts.groupingBy { it }.eachCount().maxByOrNull { it.value } ?: continue
            if (mode.key < 2) continue
            val consistency = mode.value.toDouble() / counts.size
            val score = consistency * 10 + minOf(mode.key, 10) * 0.1
            if (consistency >= 0.6 && score > bestScore) {
                best = candidate
                bestScore = score
            }
        }
        return best
    }

    // RFC 4180 tokenizer; onRow returns false to stop early.
    internal inline fun tokenize(text: String, delimiter: Char, onRow: (List<String>) -> Boolean) {
        var row = ArrayList<String>()
        val field = StringBuilder()
        var inQuotes = false
        var fieldStarted = false
        var i = 0
        val n = text.length
        while (i < n) {
            val c = text[i]
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < n && text[i + 1] == '"') {
                        field.append('"')
                        i++
                    } else {
                        inQuotes = false
                    }
                } else {
                    field.append(c)
                }
            } else when (c) {
                '"' -> if (field.isEmpty() && !fieldStarted) inQuotes = true else field.append(c)
                delimiter -> {
                    row.add(field.toString())
                    field.setLength(0)
                    fieldStarted = false
                    i++
                    continue
                }
                '\r', '\n' -> {
                    if (c == '\r' && i + 1 < n && text[i + 1] == '\n') i++
                    row.add(field.toString())
                    field.setLength(0)
                    fieldStarted = false
                    val emptyLine = row.size == 1 && row[0].isEmpty()
                    if (!emptyLine && !onRow(row)) return
                    row = ArrayList(row.size)
                    i++
                    continue
                }
                else -> field.append(c)
            }
            fieldStarted = true
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty() || fieldStarted) {
            row.add(field.toString())
            onRow(row)
        }
    }

    private fun looksLikeHeader(rows: List<List<String>>): Boolean {
        if (rows.size < 2) return false
        val first = rows[0]
        if (first.any { it.isBlank() || isNumeric(it) }) return false
        if (first.toSet().size != first.size) return false
        val data = rows.subList(1, minOf(rows.size, 101))
        // A column that is numeric in the data but textual in the first row is the strongest signal.
        val numericColumn = first.indices.any { col ->
            val values = data.mapNotNull { it.getOrNull(col)?.takeIf(String::isNotBlank) }
            values.isNotEmpty() && values.count(::isNumeric) >= values.size * 0.8
        }
        if (numericColumn) return true
        val avgFirst = first.sumOf { it.length }.toDouble() / first.size
        val avgData = data.flatten().let { cells -> if (cells.isEmpty()) 0.0 else cells.sumOf { it.length }.toDouble() / cells.size }
        return avgFirst <= avgData * 1.2 && first.all { it.length <= 40 }
    }

    public fun isNumeric(value: String): Boolean {
        val s = value.trim().removeSuffix("%").replace(" ", "").replace(" ", "")
        if (s.isEmpty() || s.length > 32) return false
        var digits = 0
        for ((index, ch) in s.withIndex()) {
            when {
                ch.isDigit() -> digits++
                (ch == '-' || ch == '+') && index == 0 -> Unit
                ch == '.' || ch == ',' || ch == 'e' || ch == 'E' -> Unit
                ch in "$€£₽¥" -> Unit
                else -> return false
            }
        }
        return digits > 0
    }
}
