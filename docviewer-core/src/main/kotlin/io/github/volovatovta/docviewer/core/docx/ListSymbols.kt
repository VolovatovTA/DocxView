package io.github.volovatovta.docviewer.core.docx

// How list markers look: bullets mapped from symbol fonts, and numbers in Word's numbering formats.
internal object ListSymbols {

    private const val BULLET = "•"
    private const val SQUARE = "▪"
    private const val WHITE_BULLET = "◦"
    private const val EN_DASH = "–"
    private const val ALPHABET_SIZE = 26
    private const val RUSSIAN_ALPHABET = "АБВГДЕЖЗИКЛМНОПРСТУФХЦЧШЩЭЮЯ"
    private const val MAX_ROMAN = 3999
    private const val ZERO_PADDED_WIDTH = 2

    private val bulletSubstitutes = mapOf(
        '\uF0B7' to BULLET,
        '\uF0B6' to BULLET,
        '\u00B7' to BULLET,
        '\uF0A7' to SQUARE,
        '\uF06E' to SQUARE,
        '\uF0D8' to "➢",
        '\uF0FC' to "✓",
        '\uF0FE' to "✓",
        '\uF076' to "❖",
        '-' to EN_DASH,
        '–' to EN_DASH,
    )
    private val privateUseSymbols = '\uF000'..'\uF0FF'

    private val romanDigits = listOf(
        1000 to "M",
        900 to "CM",
        500 to "D",
        400 to "CD",
        100 to "C",
        90 to "XC",
        50 to "L",
        40 to "XL",
        10 to "X",
        9 to "IX",
        5 to "V",
        4 to "IV",
        1 to "I",
    )

    fun bullet(text: String, font: String?): String {
        val symbol = text.firstOrNull() ?: return BULLET
        val fontName = font?.lowercase().orEmpty()
        return when {
            symbol == '§' && "wingdings" in fontName -> SQUARE
            symbol == 'o' && "courier" in fontName -> WHITE_BULLET
            else -> bulletSubstitutes[symbol] ?: if (symbol in privateUseSymbols) BULLET else text
        }
    }

    fun number(n: Int, format: String): String = when (format) {
        "lowerLetter" -> letters(n, 'a', ALPHABET_SIZE)
        "upperLetter" -> letters(n, 'A', ALPHABET_SIZE)
        "lowerRoman" -> roman(n).lowercase()
        "upperRoman" -> roman(n)
        "russianLower" -> russian(n).lowercase()
        "russianUpper" -> russian(n)
        "decimalZero" -> n.toString().padStart(ZERO_PADDED_WIDTH, '0')
        "ordinal" -> "$n."
        else -> n.toString()
    }

    // Word continues letter lists as aa, bb, … after the alphabet runs out.
    private fun letters(n: Int, first: Char, alphabetSize: Int): String =
        (first + (n - 1) % alphabetSize).toString().repeat((n - 1) / alphabetSize + 1)

    private fun russian(n: Int): String =
        RUSSIAN_ALPHABET[(n - 1) % RUSSIAN_ALPHABET.length].toString().repeat((n - 1) / RUSSIAN_ALPHABET.length + 1)

    private fun roman(number: Int): String {
        if (number !in 1..MAX_ROMAN) return number.toString()
        var rest = number
        return buildString {
            for ((value, symbol) in romanDigits) {
                repeat(rest / value) { append(symbol) }
                rest %= value
            }
        }
    }
}
