package io.github.volovatovta.docviewer.core.docx

import io.github.volovatovta.docviewer.core.model.TextAlignment
import io.github.volovatovta.docviewer.core.model.VerticalAlign
import org.xml.sax.Attributes

private typealias RunPropertyReader = (Attributes, RunProperties) -> Unit
private typealias ParagraphPropertyReader = (Attributes, ParagraphProperties) -> Unit

// Reads children of w:rPr and w:pPr; one table row per OOXML element.
internal object PropertyReader {

    private val runReaders: Map<String, RunPropertyReader> = mapOf(
        "b" to { attributes, run -> run.bold = attributes.onOff() },
        "i" to { attributes, run -> run.italic = attributes.onOff() },
        "u" to { attributes, run -> run.underline = attributes["val"]?.lowercase() != "none" },
        "strike" to { attributes, run -> run.strike = attributes.onOff() },
        "dstrike" to { attributes, run -> run.strike = attributes.onOff() },
        "color" to ::readColor,
        "highlight" to { attributes, run -> run.highlight = HighlightColors[attributes["val"]] },
        "shd" to { attributes, run -> run.shading = parseHexColor(attributes["fill"]) },
        "sz" to { attributes, run -> run.sizeHalfPoints = attributes.int("val") },
        "vertAlign" to ::readVerticalAlign,
        "position" to ::readPosition,
        "rFonts" to ::readFonts,
        "caps" to { attributes, run -> run.caps = attributes.onOff() },
        "smallCaps" to { attributes, run -> run.smallCaps = attributes.onOff() },
        "vanish" to { attributes, run -> run.hidden = attributes.onOff() },
        "specVanish" to { attributes, run -> run.hidden = attributes.onOff() },
        "webHidden" to { attributes, run -> run.hidden = attributes.onOff() },
        "rStyle" to { attributes, run -> run.styleId = attributes["val"] },
    )

    private val paragraphReaders: Map<String, ParagraphPropertyReader> = mapOf(
        "pStyle" to { attributes, paragraph -> paragraph.styleId = attributes["val"] },
        "jc" to ::readJustification,
        "ind" to ::readIndent,
        "spacing" to ::readSpacing,
        "ilvl" to { attributes, paragraph -> paragraph.listLevel = attributes.int("val") },
        "numId" to { attributes, paragraph -> paragraph.numberingId = attributes["val"] },
        "outlineLvl" to { attributes, paragraph -> paragraph.outlineLevel = attributes.int("val") },
        "shd" to { attributes, paragraph -> paragraph.shading = parseHexColor(attributes["fill"]) },
        "pageBreakBefore" to { attributes, paragraph -> paragraph.pageBreakBefore = attributes.onOff() },
    )

    private val verticalAlignments = mapOf(
        "superscript" to VerticalAlign.Superscript,
        "subscript" to VerticalAlign.Subscript,
    )

    private val justifications = mapOf(
        "center" to TextAlignment.Center,
        "right" to TextAlignment.End,
        "end" to TextAlignment.End,
        "both" to TextAlignment.Justify,
        "distribute" to TextAlignment.Justify,
        "thaiDistribute" to TextAlignment.Justify,
        "lowKashida" to TextAlignment.Justify,
        "mediumKashida" to TextAlignment.Justify,
        "highKashida" to TextAlignment.Justify,
    )

    private val monospaceFonts = listOf(
        "courier",
        "consolas",
        "mono",
        "menlo",
        "lucida console",
        "source code",
        "fira code",
    )

    fun applyRun(element: String, attributes: Attributes, run: RunProperties) {
        runReaders[element]?.invoke(attributes, run)
    }

    fun applyParagraph(element: String, attributes: Attributes, paragraph: ParagraphProperties) {
        paragraphReaders[element]?.invoke(attributes, paragraph)
    }

    private fun readColor(attributes: Attributes, run: RunProperties) {
        val value = attributes["val"]
        val isAuto = value.equals("auto", ignoreCase = true)
        run.colorAuto = isAuto
        if (!isAuto) run.color = parseHexColor(value)
    }

    private fun readVerticalAlign(attributes: Attributes, run: RunProperties) {
        run.verticalAlign = verticalAlignments[attributes["val"]] ?: VerticalAlign.Baseline
    }

    // A raised or lowered baseline is how some editors express super- and subscript.
    private fun readPosition(attributes: Attributes, run: RunProperties) {
        val offset = attributes.int("val") ?: return
        when {
            offset > 0 -> run.verticalAlign = VerticalAlign.Superscript
            offset < 0 -> run.verticalAlign = VerticalAlign.Subscript
        }
    }

    private fun readFonts(attributes: Attributes, run: RunProperties) {
        val font = (attributes["ascii"] ?: attributes["hAnsi"] ?: attributes["cs"])?.lowercase() ?: return
        run.monospace = monospaceFonts.any { it in font }
    }

    private fun readJustification(attributes: Attributes, paragraph: ParagraphProperties) {
        paragraph.alignment = justifications[attributes["val"]] ?: TextAlignment.Start
    }

    private fun readIndent(attributes: Attributes, paragraph: ParagraphProperties) {
        (attributes.int("start") ?: attributes.int("left"))?.let { paragraph.indentLeft = it }
        val hanging = attributes.int("hanging")
        val firstLine = attributes.int("firstLine")
        if (hanging != null || firstLine != null) {
            paragraph.hanging = hanging
            paragraph.firstLine = firstLine
        }
    }

    private fun readSpacing(attributes: Attributes, paragraph: ParagraphProperties) {
        attributes.int("before")?.let { paragraph.spacingBefore = it }
        attributes.int("after")?.let { paragraph.spacingAfter = it }
    }
}

// The named palette of w:highlight.
private object HighlightColors {
    private val colors = mapOf(
        "yellow" to 0xFFFFFF00,
        "green" to 0xFF00FF00,
        "cyan" to 0xFF00FFFF,
        "magenta" to 0xFFFF00FF,
        "blue" to 0xFF0000FF,
        "red" to 0xFFFF0000,
        "darkBlue" to 0xFF000080,
        "darkCyan" to 0xFF008080,
        "darkGreen" to 0xFF008000,
        "darkMagenta" to 0xFF800080,
        "darkRed" to 0xFF800000,
        "darkYellow" to 0xFF808000,
        "darkGray" to 0xFF808080,
        "lightGray" to 0xFFC0C0C0,
        "black" to 0xFF000000,
        "white" to 0xFFFFFFFF,
    ).mapValues { it.value.toInt() }

    operator fun get(name: String?): Int? = colors[name]
}
