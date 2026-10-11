package io.github.volovatovta.docviewer.core.docx

import io.github.volovatovta.docviewer.core.ooxml.Xml
import java.io.InputStream

internal class NumberingLevel(
    var start: Int = 1,
    var format: String = DECIMAL,
    var text: String = "%1.",
    var indentLeft: Int? = null,
    var hanging: Int? = null,
    var font: String? = null,
) {
    companion object {
        const val DECIMAL = "decimal"
        const val BULLET = "bullet"
        const val NONE = "none"
    }
}

// A concrete list (w:num) pointing at its abstract definition, with optional restarts per level.
internal class NumberingInstance(val abstractId: String, val startOverrides: Map<Int, Int>)

internal class ListMarker(val text: String, val level: Int, val indentLeft: Int?, val hanging: Int?)

// word/numbering.xml plus the running counters of every list in the document.
internal class Numbering(
    private val abstractLevels: Map<String, Map<Int, NumberingLevel>>,
    private val instances: Map<String, NumberingInstance>,
) {
    private val counters = HashMap<String, IntArray>()

    fun next(numberingId: String, level: Int): ListMarker? {
        if (numberingId == NO_LIST) return null
        val instance = instances[numberingId] ?: return null
        val levels = abstractLevels[instance.abstractId] ?: return null
        val listLevel = level.coerceIn(0, MAX_LEVEL)
        val counter = advance(numberingId, instance, levels, listLevel)
        val definition = levels[listLevel] ?: NumberingLevel()
        return ListMarker(markerText(definition, levels, counter), listLevel, definition.indentLeft, definition.hanging)
    }

    private fun advance(
        id: String,
        instance: NumberingInstance,
        levels: Map<Int, NumberingLevel>,
        level: Int,
    ): IntArray {
        val counter = counters.getOrPut(id) { initialCounter(instance, levels) }
        counter[level]++
        // Starting a higher level restarts every deeper level.
        for (deeper in level + 1..MAX_LEVEL) counter[deeper] = levels.startOf(deeper) - 1
        return counter
    }

    // A list starts one below each level's start value, then honours w:startOverride of this instance.
    private fun initialCounter(instance: NumberingInstance, levels: Map<Int, NumberingLevel>): IntArray {
        val counter = IntArray(LEVEL_COUNT) { levels.startOf(it) - 1 }
        for ((level, start) in instance.startOverrides) {
            if (level in counter.indices) counter[level] = start - 1
        }
        return counter
    }

    private fun markerText(definition: NumberingLevel, levels: Map<Int, NumberingLevel>, counter: IntArray): String =
        when (definition.format) {
            NumberingLevel.NONE -> ""
            NumberingLevel.BULLET -> bullet(definition.text, definition.font)
            else -> levelPlaceholder.replace(definition.text) { match ->
                val placeholderLevel = match.groupValues[1].toInt() - 1
                val placeholderFormat = levels[placeholderLevel]?.format ?: NumberingLevel.DECIMAL
                format(counter.getOrElse(placeholderLevel) { 1 }.coerceAtLeast(1), placeholderFormat)
            }
        }

    private fun Map<Int, NumberingLevel>.startOf(level: Int) = this[level]?.start ?: 1

    companion object {
        private const val NO_LIST = "0"
        private const val LEVEL_COUNT = 9
        private const val MAX_LEVEL = LEVEL_COUNT - 1
        private val levelPlaceholder = Regex("%([1-9])")

        val Empty = Numbering(emptyMap(), emptyMap())

        fun parse(input: InputStream): Numbering {
            val handler = NumberingHandler()
            Xml.parse(input, handler)
            return Numbering(handler.abstractLevels, handler.instances)
        }

        // Symbol/Wingdings bullets live in the private use area and need a Unicode substitute.
        fun bullet(text: String, font: String?): String = ListSymbols.bullet(text, font)

        fun format(n: Int, fmt: String): String = ListSymbols.number(n, fmt)
    }
}
