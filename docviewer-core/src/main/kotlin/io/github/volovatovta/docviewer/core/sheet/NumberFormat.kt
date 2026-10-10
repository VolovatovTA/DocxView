package io.github.volovatovta.docviewer.core.sheet

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/** What formatting needs besides the value: the workbook's date system and the reader's locale. */
internal class FormatContext(val dates: DateSystem, val locale: Locale = Locale.getDefault()) {
    val symbols: DecimalFormatSymbols = DecimalFormatSymbols.getInstance(locale)
}

/** An Excel number format such as `#,##0.00`, `0%` or `dd.mm.yyyy hh:mm`, applied to a numeric cell. */
internal sealed interface NumberFormat {

    fun format(value: Double, context: FormatContext): String

    /** Up to ten significant digits, like a cell Excel has not been told how to show. */
    data object General : NumberFormat {
        override fun format(value: Double, context: FormatContext): String {
            if (value == floor(value) && abs(value) < LONG_SAFE) return value.toLong().toString()
            val plain = BigDecimal(value).round(MathContext(SIGNIFICANT_DIGITS)).stripTrailingZeros()
            val text = if (abs(value) in SCIENTIFIC_BELOW..SCIENTIFIC_FROM) plain.toPlainString() else plain.toString()
            return text.replace('.', context.symbols.decimalSeparator)
        }

        private const val LONG_SAFE = 1e15
        private const val SIGNIFICANT_DIGITS = 10
        private const val SCIENTIFIC_BELOW = 1e-9
        private const val SCIENTIFIC_FROM = 1e11
    }

    /** Built-in formats 14 and 22 follow the reader's regional date style rather than a fixed pattern. */
    class RegionalDate(private val withTime: Boolean) : NumberFormat {
        override fun format(value: Double, context: FormatContext): String =
            context.dates.dateTime(value).regional(context.locale, withTime)
    }

    /** A format code with up to four `;`-separated sections: positive, negative, zero and text. */
    class Coded(code: String) : NumberFormat {
        private val sections = FormatCode.sections(code).map(Section::of)

        override fun format(value: Double, context: FormatContext): String {
            val negative = sections.getOrNull(1)
            val zero = sections.getOrNull(2)
            return when {
                value < 0 && negative != null -> negative.format(-value, context)
                value == 0.0 && zero != null -> zero.format(value, context)
                else -> sections.first().format(value, context)
            }
        }
    }

    companion object {
        /** Formats every spreadsheet writer may use by number without defining them ([ECMA-376] 18.8.30). */
        fun builtIn(id: Int): NumberFormat = when (id) {
            REGIONAL_DATE -> RegionalDate(withTime = false)
            REGIONAL_DATE_TIME -> RegionalDate(withTime = true)
            else -> builtInCodes[id]?.let(::Coded) ?: General
        }

        fun of(code: String): NumberFormat = if (code.equals(GENERAL, ignoreCase = true)) General else Coded(code)

        private const val GENERAL = "General"
        private const val REGIONAL_DATE = 14
        private const val REGIONAL_DATE_TIME = 22
        private val builtInCodes = mapOf(
            1 to "0", 2 to "0.00", 3 to "#,##0", 4 to "#,##0.00",
            5 to "#,##0", 6 to "#,##0", 7 to "#,##0.00", 8 to "#,##0.00",
            9 to "0%", 10 to "0.00%", 11 to "0.00E+00",
            15 to "d-mmm-yy", 16 to "d-mmm", 17 to "mmm-yy",
            18 to "h:mm AM/PM", 19 to "h:mm:ss AM/PM", 20 to "h:mm", 21 to "h:mm:ss",
            37 to "#,##0 ;(#,##0)", 38 to "#,##0 ;(#,##0)", 39 to "#,##0.00;(#,##0.00)", 40 to "#,##0.00;(#,##0.00)",
            41 to "#,##0", 42 to "#,##0", 43 to "#,##0.00", 44 to "#,##0.00",
            45 to "mm:ss", 46 to "[h]:mm:ss", 47 to "mm:ss", 48 to "##0.0E+0",
        )
    }
}

/** One section of a format code, already split into literal text and format characters. */
private sealed interface Section {
    fun format(value: Double, context: FormatContext): String

    companion object {
        fun of(code: String): Section {
            val parts = FormatCode.parts(code)
            val pattern = parts.filterIsInstance<Part.Code>().joinToString("") { it.text }
            return when {
                pattern.trim() == "@" -> TextSection
                pattern.any { it.lowercaseChar() in DATE_LETTERS } -> DateSection(parts)
                else -> NumberSection(parts)
            }
        }

        private const val DATE_LETTERS = "ydhsm"
    }
}

private data object TextSection : Section {
    override fun format(value: Double, context: FormatContext) = NumberFormat.General.format(value, context)
}

/** Digits with grouping, decimals, percent, thousands scaling or an exponent, between literal text. */
private class NumberSection(parts: List<Part>) : Section {
    private val text = parts.joinToString("") { it.text }
    private val first = text.indexOfFirst { it in PLACEHOLDERS }
    private val last = text.indexOfLast { it in PLACEHOLDERS }
    private val prefix = if (first < 0) text else text.substring(0, first)
    private val core = if (first < 0) "" else text.substring(first, last + 1)
    private val suffix = if (first < 0) "" else text.substring(last + 1)
    private val percents = text.count { it == '%' }
    private val isScientific = core.contains('E', ignoreCase = true)

    // Commas right after the last digit divide by a thousand each: "#,##0," shows thousands.
    private val thousandsScaling = suffix.takeWhile { it == ',' }.length

    override fun format(value: Double, context: FormatContext): String {
        if (core.isEmpty()) return text
        val scaled = value * HUNDRED.pow(percents) / THOUSAND.pow(thousandsScaling)
        val digits = if (isScientific) scientific(scaled, context) else decimal(core, scaled, context)
        return prefix + digits + suffix.drop(thousandsScaling)
    }

    // "0.00E+00": the mantissa keeps its decimals, the exponent its sign and at least its zeros.
    private fun scientific(value: Double, context: FormatContext): String {
        val mantissaCode = core.substringBefore('E').substringBefore('e')
        val exponentCode = core.substringAfter('E').substringAfter('e')
        val exponent = if (value == 0.0) 0 else floor(log10(abs(value))).toInt()
        val mantissa = decimal(mantissaCode, value / TEN.pow(exponent), context)
        val sign = when {
            exponent < 0 -> "-"
            exponentCode.startsWith('+') -> "+"
            else -> ""
        }
        val exponentDigits = abs(exponent).toString().padStart(exponentCode.count { it == '0' }, '0')
        return "${mantissa}E$sign$exponentDigits"
    }

    private fun decimal(code: String, value: Double, context: FormatContext): String {
        val integer = code.substringBefore('.')
        val fraction = code.substringAfter('.', "")
        // The group size is set explicitly: an empty pattern means 3 on the JVM but 0 on Android.
        val format = DecimalFormat("", context.symbols).apply {
            isGroupingUsed = ',' in integer
            groupingSize = DIGITS_PER_GROUP
            minimumIntegerDigits = integer.count { it == '0' }
            minimumFractionDigits = fraction.count { it == '0' }
            maximumFractionDigits = fraction.count { it in PLACEHOLDERS }
            roundingMode = RoundingMode.HALF_UP
        }
        return format.format(value)
    }

    private companion object {
        const val PLACEHOLDERS = "0#?"
        const val DIGITS_PER_GROUP = 3
        const val HUNDRED = 100.0
        const val THOUSAND = 1000.0
        const val TEN = 10.0
    }
}

/** Days, months, years, hours, minutes and seconds in the order the code writes them. */
private class DateSection(parts: List<Part>) : Section {
    private val tokens = DateToken.tokenize(parts)
    private val twelveHour = tokens.any { it is DateToken.Meridiem }

    override fun format(value: Double, context: FormatContext): String {
        val time = context.dates.dateTime(value)
        return tokens.joinToString("") { it.render(time, twelveHour, context.locale) }
    }
}

private sealed interface DateToken {
    fun render(time: CellDateTime, twelveHour: Boolean, locale: Locale): String

    class Literal(val text: String) : DateToken {
        override fun render(time: CellDateTime, twelveHour: Boolean, locale: Locale) = text
    }

    /** One date or time field; [length] is how many times the code repeats its letter. */
    class Field(val letter: Char, val length: Int) : DateToken {
        override fun render(time: CellDateTime, twelveHour: Boolean, locale: Locale): String = when (letter) {
            YEAR -> if (length <= 2) "%02d".format(time.year % CENTURY) else time.year.toString()
            MONTH -> name(time.month) { style -> time.monthName(locale, style) }
            DAY -> name(time.day) { style -> time.dayName(locale, style) }
            HOUR -> number(if (twelveHour) (time.hour + HALF_DAY - 1) % HALF_DAY + 1 else time.hour)
            MINUTE -> number(time.minute)
            else -> number(time.second)
        }

        // "m" and "mm" are numbers, "mmm" a short name, "mmmm" a full one, and "mmmmm" its first letter.
        private fun name(value: Int, named: (NameStyle) -> String): String = when (length) {
            in NUMERIC -> number(value)
            ABBREVIATED -> named(NameStyle.Short)
            FULL -> named(NameStyle.Full)
            else -> named(NameStyle.Narrow)
        }

        private fun number(value: Int) = if (length >= 2) "%02d".format(value) else value.toString()
    }

    class Meridiem(private val short: Boolean) : DateToken {
        override fun render(time: CellDateTime, twelveHour: Boolean, locale: Locale): String {
            val morning = time.hour < HALF_DAY
            return when {
                short -> if (morning) "A" else "P"
                else -> if (morning) "AM" else "PM"
            }
        }
    }

    companion object {
        private const val YEAR = 'y'
        private const val MONTH = 'M'
        private const val DAY = 'd'
        private const val HOUR = 'h'
        private const val MINUTE = 'm'
        private const val SECOND = 's'
        private const val FIELD_LETTERS = "ydhms"
        private const val CENTURY = 100
        private const val HALF_DAY = 12
        private const val ABBREVIATED = 3
        private const val FULL = 4
        private val NUMERIC = 1..2
        private val meridiem = Regex("^(AM/PM|A/P)", RegexOption.IGNORE_CASE)

        fun tokenize(parts: List<Part>): List<DateToken> {
            val tokens = parts.flatMap { part ->
                if (part is Part.Literal) listOf(Literal(part.text)) else fields(part.text)
            }
            return tokens.mapIndexed { index, token -> monthOrMinute(tokens, index, token) }
        }

        // Every "m" starts as a month; [monthOrMinute] decides once the neighbours are known.
        private fun fields(code: String): List<DateToken> {
            val tokens = ArrayList<DateToken>()
            var index = 0
            while (index < code.length) {
                val ampm = meridiem.find(code.substring(index))?.value
                val letter = code[index].lowercaseChar()
                val length = ampm?.length ?: code.drop(index).takeWhile { it.lowercaseChar() == letter }.length
                tokens += when {
                    ampm != null -> Meridiem(short = ampm.length == "A/P".length)
                    letter in FIELD_LETTERS -> Field(if (letter == MINUTE) MONTH else letter, length)
                    else -> Literal(code.substring(index, index + length))
                }
                index += length
            }
            return tokens
        }

        // "m" means minutes right after hours or right before seconds, and months everywhere else.
        private fun monthOrMinute(tokens: List<DateToken>, index: Int, token: DateToken): DateToken {
            if (token !is Field || token.letter != MONTH || token.length !in NUMERIC) return token
            val fields = tokens.filterIsInstance<Field>()
            val position = fields.indexOf(token)
            val afterHours = fields.getOrNull(position - 1)?.letter == HOUR
            val beforeSeconds = fields.getOrNull(position + 1)?.letter == SECOND
            return if (afterHours || beforeSeconds) Field(MINUTE, token.length) else token
        }
    }
}

/** A format code split into text shown as is and characters with a formatting meaning. */
private sealed interface Part {
    val text: String

    class Literal(override val text: String) : Part

    class Code(override val text: String) : Part
}

private object FormatCode {

    /** Splits on `;` that are not inside quotes, brackets or after a backslash. */
    fun sections(code: String): List<String> {
        val sections = ArrayList<String>()
        val current = StringBuilder()
        var index = 0
        while (index < code.length) {
            val char = code[index]
            when {
                char == ';' -> sections += current.toString().also { current.clear() }
                char == '"' -> index = copyUntil(code, index, '"', current)
                char == '[' -> index = copyUntil(code, index, ']', current)
                char == '\\' && index + 1 < code.length -> current.append(char).append(code[++index])
                else -> current.append(char)
            }
            index++
        }
        sections += current.toString()
        return sections
    }

    fun parts(section: String): List<Part> = SectionScanner(section).scan()

    private fun copyUntil(code: String, start: Int, end: Char, into: StringBuilder): Int {
        val close = code.indexOf(end, start + 1).let { if (it < 0) code.length - 1 else it }
        into.append(code, start, close + 1)
        return close
    }
}

/**
 * Reads one section left to right. Quoted text and escaped characters are literal, "_x" pads with a space,
 * "*x" fills and is dropped, and brackets hold a currency symbol or elapsed time to keep, or a color,
 * condition or locale to drop.
 */
private class SectionScanner(private val section: String) {
    private val parts = ArrayList<Part>()
    private var index = 0

    fun scan(): List<Part> {
        while (index < section.length) {
            when (val char = section[index++]) {
                '"' -> literal(until('"'))
                '\\' -> section.getOrNull(index++)?.let { literal(it.toString()) }
                '_' -> padding()
                '*' -> index++
                '[' -> bracket(until(']'))
                else -> code(char.toString())
            }
        }
        return parts
    }

    // The text up to [close]; the closing character is consumed as well.
    private fun until(close: Char): String {
        val end = section.indexOf(close, index).let { if (it < 0) section.length else it }
        return section.substring(index, end).also { index = end + 1 }
    }

    private fun padding() {
        literal(" ")
        index++
    }

    private fun bracket(content: String) {
        when {
            content.startsWith('$') -> literal(content.drop(1).substringBefore('-'))
            content.isNotEmpty() && content.all { it.lowercaseChar() in ELAPSED } -> code(content)
        }
    }

    private fun literal(text: String) {
        parts += Part.Literal(text)
    }

    // Neighbouring format characters stay one part, so "dd" reads as one two-letter day.
    private fun code(text: String) {
        val last = parts.lastOrNull()
        if (last is Part.Code) parts[parts.lastIndex] = Part.Code(last.text + text) else parts += Part.Code(text)
    }

    private companion object {
        const val ELAPSED = "hms"
    }
}
