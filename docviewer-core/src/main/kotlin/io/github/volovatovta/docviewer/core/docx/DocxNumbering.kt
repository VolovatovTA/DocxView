package io.github.volovatovta.docviewer.core.docx

import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.io.InputStream

internal class NumLevel(
    var start: Int = 1,
    var format: String = "decimal",
    var text: String = "%1.",
    var indLeftTw: Int? = null,
    var hangingTw: Int? = null,
    var font: String? = null,
)

internal class ListMarker(val text: String, val level: Int, val indLeftTw: Int?, val hangingTw: Int?)

internal class Numbering(
    private val abstracts: Map<String, Map<Int, NumLevel>>,
    private val nums: Map<String, Pair<String, Map<Int, Int>>>,
) {
    private val counters = HashMap<String, IntArray>()
    private val lastLevel = HashMap<String, Int>()

    fun next(numId: String, ilvl: Int): ListMarker? {
        if (numId == "0") return null
        val (abstractId, overrides) = nums[numId] ?: return null
        val levels = abstracts[abstractId] ?: return null
        val level = ilvl.coerceIn(0, 8)
        val def = levels[level] ?: NumLevel()
        val counter = counters.getOrPut(numId) { IntArray(9) { levels[it]?.start?.minus(1) ?: 0 } }
        overrides.forEach { (lvl, start) -> if (numId !in lastLevel && lvl in 0..8) counter[lvl] = start - 1 }
        counter[level]++
        // Starting a higher level restarts every deeper level.
        for (deeper in level + 1..8) counter[deeper] = (levels[deeper]?.start ?: 1) - 1
        lastLevel[numId] = level
        if (def.format == "none") return ListMarker("", level, def.indLeftTw, def.hangingTw)
        val text = if (def.format == "bullet") bullet(def.text, def.font) else {
            PLACEHOLDER.replace(def.text) { match ->
                val idx = match.groupValues[1].toInt() - 1
                val lvlDef = levels[idx] ?: NumLevel()
                format(counter.getOrElse(idx) { 1 }.coerceAtLeast(1), lvlDef.format)
            }
        }
        return ListMarker(text, level, def.indLeftTw, def.hangingTw)
    }

    companion object {
        private val PLACEHOLDER = Regex("%([1-9])")
        val Empty = Numbering(emptyMap(), emptyMap())

        fun parse(input: InputStream): Numbering {
            val handler = NumberingHandler()
            Xml.parse(input, handler)
            return Numbering(handler.abstracts, handler.nums)
        }

        // Symbol/Wingdings bullets live in the private use area and need a Unicode substitute.
        fun bullet(text: String, font: String?): String {
            val c = text.firstOrNull() ?: return "•"
            val f = font?.lowercase().orEmpty()
            return when {
                c == '' || c == '' || c == '·' -> "•"
                c == '' || c == '' || c == '§' && "wingdings" in f -> "▪"
                c == '' -> "➢"
                c == '' || c == '' -> "✓"
                c == '' -> "❖"
                c == 'o' && "courier" in f -> "◦"
                c == '-' || c == '–' -> "–"
                c in ''..'' -> "•"
                else -> text
            }
        }

        fun format(n: Int, fmt: String): String = when (fmt) {
            "lowerLetter" -> letters(n, 'a', 26)
            "upperLetter" -> letters(n, 'A', 26)
            "lowerRoman" -> roman(n).lowercase()
            "upperRoman" -> roman(n)
            "russianLower" -> russian(n).lowercase()
            "russianUpper" -> russian(n)
            "decimalZero" -> n.toString().padStart(2, '0')
            "ordinal" -> "$n."
            else -> n.toString()
        }

        private fun letters(n: Int, first: Char, size: Int): String {
            val letter = first + (n - 1) % size
            return letter.toString().repeat((n - 1) / size + 1)
        }

        private const val RUSSIAN = "АБВГДЕЖЗИКЛМНОПРСТУФХЦЧШЩЭЮЯ"

        private fun russian(n: Int): String = RUSSIAN[(n - 1) % RUSSIAN.length].toString().repeat((n - 1) / RUSSIAN.length + 1)

        private fun roman(number: Int): String {
            if (number <= 0 || number >= 4000) return number.toString()
            val values = intArrayOf(1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1)
            val symbols = arrayOf("M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I")
            var n = number
            return buildString {
                for (i in values.indices) while (n >= values[i]) {
                    append(symbols[i])
                    n -= values[i]
                }
            }
        }
    }
}

private class NumberingHandler : DefaultHandler() {
    val abstracts = HashMap<String, MutableMap<Int, NumLevel>>()
    val nums = HashMap<String, Pair<String, Map<Int, Int>>>()

    private var abstractId: String? = null
    private var level: NumLevel? = null
    private var numId: String? = null
    private var numAbstract: String? = null
    private var overrides = HashMap<Int, Int>()
    private var overrideLevel: Int? = null

    override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes) {
        when (localName(localName, qName)) {
            "abstractNum" -> abstractId = attributes["abstractNumId"]
            "lvl" -> if (overrideLevel == null) {
                val ilvl = attributes.int("ilvl") ?: 0
                level = NumLevel().also { lvl -> abstractId?.let { abstracts.getOrPut(it) { HashMap() }[ilvl] = lvl } }
            }
            "start" -> level?.start = attributes.int("val") ?: 1
            "numFmt" -> level?.format = attributes["val"] ?: "decimal"
            "lvlText" -> level?.text = attributes["val"] ?: ""
            "ind" -> level?.let { lvl ->
                lvl.indLeftTw = attributes.int("start") ?: attributes.int("left")
                lvl.hangingTw = attributes.int("hanging")
            }
            "rFonts" -> level?.font = attributes["ascii"] ?: attributes["hAnsi"]
            "num" -> {
                numId = attributes["numId"]
                numAbstract = null
                overrides = HashMap()
            }
            "abstractNumId" -> if (numId != null) numAbstract = attributes["val"]
            "lvlOverride" -> overrideLevel = attributes.int("ilvl")
            "startOverride" -> overrideLevel?.let { lvl -> attributes.int("val")?.let { overrides[lvl] = it } }
        }
    }

    override fun endElement(uri: String?, localName: String?, qName: String?) {
        when (localName(localName, qName)) {
            "abstractNum" -> abstractId = null
            "lvl" -> if (overrideLevel == null) level = null
            "lvlOverride" -> overrideLevel = null
            "num" -> {
                val id = numId
                val abs = numAbstract
                if (id != null && abs != null) nums[id] = abs to overrides
                numId = null
            }
        }
    }
}
