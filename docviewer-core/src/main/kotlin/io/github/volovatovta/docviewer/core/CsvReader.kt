package io.github.volovatovta.docviewer.core

// RFC 4180 reader: quoted fields may contain delimiters, line breaks and doubled quotes; blank lines are skipped.
internal class CsvReader(private val text: String, private val delimiter: Char) {

    private var position = 0

    fun rows(): Sequence<List<String>> = generateSequence { nextRow() }

    private fun nextRow(): List<String>? {
        while (position < text.length) {
            val row = readRow()
            if (!row.isBlankLine()) return row
        }
        return null
    }

    private fun readRow(): List<String> {
        val fields = ArrayList<String>()
        do {
            fields += readField()
        } while (consumeDelimiter())
        consumeLineBreak()
        return fields
    }

    private fun readField(): String = if (peek() == QUOTE) readQuotedField() else readPlainField()

    private fun readPlainField(): String {
        val start = position
        while (position < text.length && !isFieldEnd(text[position])) position++
        return text.substring(start, position)
    }

    private fun readQuotedField(): String {
        val field = StringBuilder()
        position++
        while (position < text.length) {
            val char = text[position++]
            when {
                char != QUOTE -> field.append(char)
                peek() == QUOTE -> field.append(text[position++])
                // Anything after the closing quote still belongs to the field, as spreadsheet apps read it.
                else -> return field.append(readPlainField()).toString()
            }
        }
        return field.toString()
    }

    private fun consumeDelimiter(): Boolean {
        if (peek() != delimiter) return false
        position++
        return true
    }

    private fun consumeLineBreak() {
        if (peek() == CARRIAGE_RETURN) position++
        if (peek() == LINE_FEED) position++
    }

    private fun peek(): Char? = text.getOrNull(position)

    private fun isFieldEnd(char: Char) = char == delimiter || char == LINE_FEED || char == CARRIAGE_RETURN

    private fun List<String>.isBlankLine() = size == 1 && first().isEmpty()

    private companion object {
        const val QUOTE = '"'
        const val LINE_FEED = '\n'
        const val CARRIAGE_RETURN = '\r'
    }
}
