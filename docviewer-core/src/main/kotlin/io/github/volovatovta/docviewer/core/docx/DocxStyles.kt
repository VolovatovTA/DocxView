package io.github.volovatovta.docviewer.core.docx

import io.github.volovatovta.docviewer.core.model.ParagraphStyle
import io.github.volovatovta.docviewer.core.model.TextAlignment
import io.github.volovatovta.docviewer.core.model.VerticalAlign
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.io.InputStream

// Partial run properties: null means "inherit".
internal data class RunProps(
    var bold: Boolean? = null,
    var italic: Boolean? = null,
    var underline: Boolean? = null,
    var strike: Boolean? = null,
    var color: Int? = null,
    var colorAuto: Boolean? = null,
    var highlight: Int? = null,
    var shading: Int? = null,
    var sizeHalfPt: Int? = null,
    var vertAlign: VerticalAlign? = null,
    var monospace: Boolean? = null,
    var caps: Boolean? = null,
    var smallCaps: Boolean? = null,
    var hidden: Boolean? = null,
    var styleId: String? = null,
) {
    fun overlay(top: RunProps): RunProps = RunProps(
        bold = top.bold ?: bold,
        italic = top.italic ?: italic,
        underline = top.underline ?: underline,
        strike = top.strike ?: strike,
        color = if (top.colorAuto == true) null else top.color ?: color,
        colorAuto = top.colorAuto ?: colorAuto,
        highlight = top.highlight ?: highlight,
        shading = top.shading ?: shading,
        sizeHalfPt = top.sizeHalfPt ?: sizeHalfPt,
        vertAlign = top.vertAlign ?: vertAlign,
        monospace = top.monospace ?: monospace,
        caps = top.caps ?: caps,
        smallCaps = top.smallCaps ?: smallCaps,
        hidden = top.hidden ?: hidden,
        styleId = top.styleId ?: styleId,
    )
}

internal data class ParaProps(
    var styleId: String? = null,
    var alignment: TextAlignment? = null,
    var indLeftTw: Int? = null,
    var hangingTw: Int? = null,
    var firstLineTw: Int? = null,
    var spacingBeforeTw: Int? = null,
    var spacingAfterTw: Int? = null,
    var numId: String? = null,
    var ilvl: Int? = null,
    var outlineLvl: Int? = null,
    var shading: Int? = null,
    var pageBreakBefore: Boolean? = null,
) {
    fun overlay(top: ParaProps): ParaProps {
        // Hanging and first-line indents are mutually exclusive in Word, so they are inherited together.
        val indentFromTop = top.hangingTw != null || top.firstLineTw != null
        return ParaProps(
            styleId = top.styleId ?: styleId,
            alignment = top.alignment ?: alignment,
            indLeftTw = top.indLeftTw ?: indLeftTw,
            hangingTw = if (indentFromTop) top.hangingTw else hangingTw,
            firstLineTw = if (indentFromTop) top.firstLineTw else firstLineTw,
            spacingBeforeTw = top.spacingBeforeTw ?: spacingBeforeTw,
            spacingAfterTw = top.spacingAfterTw ?: spacingAfterTw,
            numId = top.numId ?: numId,
            ilvl = top.ilvl ?: ilvl,
            outlineLvl = top.outlineLvl ?: outlineLvl,
            shading = top.shading ?: shading,
            pageBreakBefore = top.pageBreakBefore ?: pageBreakBefore,
        )
    }
}

internal object PropsReader {

    private val MONO_FONTS = listOf("courier", "consolas", "mono", "menlo", "lucida console", "source code", "fira code")

    private val HIGHLIGHTS = mapOf(
        "yellow" to 0xFFFFFF00, "green" to 0xFF00FF00, "cyan" to 0xFF00FFFF, "magenta" to 0xFFFF00FF,
        "blue" to 0xFF0000FF, "red" to 0xFFFF0000, "darkBlue" to 0xFF000080, "darkCyan" to 0xFF008080,
        "darkGreen" to 0xFF008000, "darkMagenta" to 0xFF800080, "darkRed" to 0xFF800000, "darkYellow" to 0xFF808000,
        "darkGray" to 0xFF808080, "lightGray" to 0xFFC0C0C0, "black" to 0xFF000000, "white" to 0xFFFFFFFF,
    ).mapValues { it.value.toInt() }

    // Applies one child element of w:rPr; returns false if the element is not a run property.
    fun applyRun(name: String, attrs: Attributes, props: RunProps): Boolean {
        when (name) {
            "b" -> props.bold = attrs.onOff()
            "i" -> props.italic = attrs.onOff()
            "u" -> props.underline = attrs["val"]?.lowercase() != "none"
            "strike", "dstrike" -> props.strike = attrs.onOff()
            "color" -> {
                val value = attrs["val"]
                if (value.equals("auto", ignoreCase = true)) props.colorAuto = true else {
                    props.color = parseHexColor(value)
                    props.colorAuto = false
                }
            }
            "highlight" -> props.highlight = HIGHLIGHTS[attrs["val"]]
            "shd" -> props.shading = parseHexColor(attrs["fill"])
            "sz" -> props.sizeHalfPt = attrs.int("val")
            "vertAlign" -> props.vertAlign = when (attrs["val"]) {
                "superscript" -> VerticalAlign.Superscript
                "subscript" -> VerticalAlign.Subscript
                else -> VerticalAlign.Baseline
            }
            "position" -> attrs.int("val")?.let { if (it > 0) props.vertAlign = VerticalAlign.Superscript else if (it < 0) props.vertAlign = VerticalAlign.Subscript }
            "rFonts" -> {
                val font = (attrs["ascii"] ?: attrs["hAnsi"] ?: attrs["cs"])?.lowercase()
                if (font != null) props.monospace = MONO_FONTS.any { it in font }
            }
            "caps" -> props.caps = attrs.onOff()
            "smallCaps" -> props.smallCaps = attrs.onOff()
            "vanish", "specVanish", "webHidden" -> props.hidden = attrs.onOff()
            "rStyle" -> props.styleId = attrs["val"]
            else -> return false
        }
        return true
    }

    fun applyParagraph(name: String, attrs: Attributes, props: ParaProps): Boolean {
        when (name) {
            "pStyle" -> props.styleId = attrs["val"]
            "jc" -> props.alignment = when (attrs["val"]) {
                "center" -> TextAlignment.Center
                "right", "end" -> TextAlignment.End
                "both", "distribute", "thaiDistribute", "lowKashida", "mediumKashida", "highKashida" -> TextAlignment.Justify
                else -> TextAlignment.Start
            }
            "ind" -> {
                (attrs.int("start") ?: attrs.int("left"))?.let { props.indLeftTw = it }
                val hanging = attrs.int("hanging")
                val firstLine = attrs.int("firstLine")
                if (hanging != null || firstLine != null) {
                    props.hangingTw = hanging
                    props.firstLineTw = firstLine
                }
            }
            "spacing" -> {
                attrs.int("before")?.let { props.spacingBeforeTw = it }
                attrs.int("after")?.let { props.spacingAfterTw = it }
            }
            "ilvl" -> props.ilvl = attrs.int("val")
            "numId" -> props.numId = attrs["val"]
            "outlineLvl" -> props.outlineLvl = attrs.int("val")
            "shd" -> props.shading = parseHexColor(attrs["fill"])
            "pageBreakBefore" -> props.pageBreakBefore = attrs.onOff()
            else -> return false
        }
        return true
    }
}

internal class StyleDef(
    val id: String,
    val type: String,
    val name: String?,
    val basedOn: String?,
    val isDefault: Boolean,
    val paragraph: ParaProps,
    val run: RunProps,
)

internal class StyleSheet(
    private val styles: Map<String, StyleDef>,
    private val docDefaultRun: RunProps,
    private val docDefaultParagraph: ParaProps,
) {
    private val defaultParagraphStyle = styles.values.firstOrNull { it.type == "paragraph" && it.isDefault }?.id
    private val paragraphCache = HashMap<String?, Pair<ParaProps, RunProps>>()
    private val runCache = HashMap<String, RunProps>()

    // Body text size every run is measured against, in half-points.
    val baseSizeHalfPt: Int = resolveParagraph(null).second.sizeHalfPt ?: 20

    fun resolveParagraph(styleId: String?): Pair<ParaProps, RunProps> = paragraphCache.getOrPut(styleId) {
        val chain = chain(styleId ?: defaultParagraphStyle)
        var para = docDefaultParagraph
        var run = docDefaultRun
        for (style in chain) {
            para = para.overlay(style.paragraph)
            run = run.overlay(style.run)
        }
        para to run
    }

    fun resolveRunStyle(styleId: String): RunProps = runCache.getOrPut(styleId) {
        chain(styleId).fold(RunProps()) { acc, style -> acc.overlay(style.run) }
    }

    fun headingLevel(styleId: String?, outlineLvl: Int?): Int {
        val style = styles[styleId ?: defaultParagraphStyle]
        val name = (style?.name ?: styleId)?.lowercase()?.replace(" ", "")
        if (name == "title" || name == "название") return ParagraphStyle.TITLE_LEVEL
        if (outlineLvl != null && outlineLvl in 0..8) return minOf(outlineLvl + 1, 6)
        if (name != null) {
            HEADING_NAME.matchEntire(name)?.let { return it.groupValues[1].toInt().coerceIn(1, 6) }
        }
        return 0
    }

    private fun chain(styleId: String?): List<StyleDef> {
        val result = ArrayList<StyleDef>()
        val seen = HashSet<String>()
        var current = styleId?.let(styles::get)
        while (current != null && seen.add(current.id)) {
            result.add(0, current)
            current = current.basedOn?.let(styles::get)
        }
        return result
    }

    companion object {
        private val HEADING_NAME = Regex("(?:heading|заголовок)(\\d)")
        val Empty = StyleSheet(emptyMap(), RunProps(), ParaProps())

        fun parse(input: InputStream): StyleSheet {
            val handler = StylesHandler()
            Xml.parse(input, handler)
            return StyleSheet(handler.styles, handler.defaultRun, handler.defaultParagraph)
        }
    }
}

private class StylesHandler : DefaultHandler() {
    val styles = HashMap<String, StyleDef>()
    val defaultRun = RunProps()
    val defaultParagraph = ParaProps()

    private val path = ArrayList<String>()
    private var id: String? = null
    private var type = "paragraph"
    private var name: String? = null
    private var basedOn: String? = null
    private var isDefault = false
    private var para = ParaProps()
    private var run = RunProps()

    override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes) {
        val element = localName(localName, qName)
        val parent = path.lastOrNull()
        path += element
        val inStyle = "style" in path
        when {
            element == "style" -> {
                id = attributes["styleId"]
                type = attributes["type"] ?: "paragraph"
                isDefault = attributes.onOffAttr("default")
                name = null
                basedOn = null
                para = ParaProps()
                run = RunProps()
            }
            inStyle && element == "name" && parent == "style" -> name = attributes["val"]
            inStyle && element == "basedOn" -> basedOn = attributes["val"]
            // Table-style conditional formatting (tblStylePr) would wrongly leak into every cell.
            "tblStylePr" in path -> Unit
            parent == "rPr" -> PropsReader.applyRun(element, attributes, if (inStyle) run else defaultRun)
            parent == "pPr" || parent == "numPr" -> PropsReader.applyParagraph(element, attributes, if (inStyle) para else defaultParagraph)
        }
    }

    override fun endElement(uri: String?, localName: String?, qName: String?) {
        val element = path.removeAt(path.lastIndex)
        if (element == "style") {
            id?.let { styles[it] = StyleDef(it, type, name, basedOn, isDefault, para, run) }
            id = null
        }
    }

    private fun Attributes.onOffAttr(local: String) = when (this[local]?.lowercase()) {
        "1", "true", "on" -> true
        else -> false
    }
}
