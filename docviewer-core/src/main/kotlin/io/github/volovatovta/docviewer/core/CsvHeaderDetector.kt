package io.github.volovatovta.docviewer.core

// Guesses whether the first CSV row names the columns rather than holding data.
internal object CsvHeaderDetector {

    private const val SAMPLE_ROWS = 100
    private const val NUMERIC_COLUMN_SHARE = 0.8
    private const val HEADER_LENGTH_TOLERANCE = 1.2
    private const val MAX_HEADER_CELL_LENGTH = 40

    fun looksLikeHeader(rows: List<List<String>>): Boolean {
        if (rows.size < 2) return false
        val first = rows.first()
        val plausibleNames = first.none { it.isBlank() || CsvParser.isNumeric(it) } && first.toSet().size == first.size
        if (!plausibleNames) return false
        val data = rows.subList(1, minOf(rows.size, SAMPLE_ROWS + 1))
        // A column that is numeric in the data but textual in the first row is the strongest signal.
        return first.indices.any { column -> isNumericColumn(data, column) } || isShortLikeData(first, data)
    }

    private fun isNumericColumn(data: List<List<String>>, column: Int): Boolean {
        val values = data.mapNotNull { row -> row.getOrNull(column)?.takeIf(String::isNotBlank) }
        return values.isNotEmpty() && values.count(CsvParser::isNumeric) >= values.size * NUMERIC_COLUMN_SHARE
    }

    private fun isShortLikeData(first: List<String>, data: List<List<String>>): Boolean {
        val firstAverage = first.averageLength()
        val dataAverage = data.flatten().averageLength()
        val shortCells = first.all { it.length <= MAX_HEADER_CELL_LENGTH }
        return shortCells && firstAverage <= dataAverage * HEADER_LENGTH_TOLERANCE
    }

    private fun List<String>.averageLength(): Double = if (isEmpty()) 0.0 else sumOf { it.length }.toDouble() / size
}
