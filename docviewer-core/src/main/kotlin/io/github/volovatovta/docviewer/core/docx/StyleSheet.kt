package io.github.volovatovta.docviewer.core.docx

import io.github.volovatovta.docviewer.core.model.ParagraphStyle
import io.github.volovatovta.docviewer.core.ooxml.Xml
import io.github.volovatovta.docviewer.core.ooxml.get
import io.github.volovatovta.docviewer.core.ooxml.isOn
import io.github.volovatovta.docviewer.core.ooxml.localName
import io.github.volovatovta.docviewer.core.ooxml.parseHexColor
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.io.InputStream

internal class StyleDefinition(
    val id: String,
    val type: String,
    val name: String?,
    val basedOn: String?,
    val isDefault: Boolean,
    val paragraph: ParagraphProperties,
    val run: RunProperties,
    // Conditional formatting of a table style by w:tblStylePr type: firstRow, band1Horz, lastCol...
    val conditions: Map<String, TableCondition> = emptyMap(),
)

// Formatting a table style gives to some cells, e.g. a colored header row or shaded odd rows.
internal class TableCondition(val run: RunProperties = RunProperties(), var shading: Int? = null) {
    fun overlay(top: TableCondition) = TableCondition(run.overlay(top.run), top.shading ?: shading)
}

// word/styles.xml with inheritance (basedOn chains) resolved on demand.
internal class StyleSheet(
    private val styles: Map<String, StyleDefinition>,
    private val defaultRun: RunProperties,
    private val defaultParagraph: ParagraphProperties,
) {
    private val defaultParagraphStyleId = styles.values.firstOrNull { it.type == PARAGRAPH_TYPE && it.isDefault }?.id
    private val paragraphCache = HashMap<String?, Pair<ParagraphProperties, RunProperties>>()
    private val runCache = HashMap<String, RunProperties>()

    // Body text size every run is measured against, in half-points.
    val baseSizeHalfPoints: Int = resolveParagraph(null).second.sizeHalfPoints ?: WORD_DEFAULT_SIZE_HALF_POINTS

    fun resolveParagraph(styleId: String?): Pair<ParagraphProperties, RunProperties> =
        paragraphCache.getOrPut(styleId) {
            chain(styleId ?: defaultParagraphStyleId).fold(defaultParagraph to defaultRun) { (paragraph, run), style ->
                paragraph.overlay(style.paragraph) to run.overlay(style.run)
            }
        }

    // Later conditions win, so callers pass them from the weakest (bands) to the strongest (header row).
    fun tableCondition(styleId: String?, types: List<String>): TableCondition {
        val definitions = chain(styleId)
        return types.fold(TableCondition()) { merged, type ->
            definitions.mapNotNull { it.conditions[type] }.fold(merged, TableCondition::overlay)
        }
    }

    fun resolveRunStyle(styleId: String): RunProperties = runCache.getOrPut(styleId) {
        chain(styleId).fold(RunProperties()) { run, style -> run.overlay(style.run) }
    }

    fun headingLevel(styleId: String?, outlineLevel: Int?): Int {
        val name = (styles[styleId ?: defaultParagraphStyleId]?.name ?: styleId)?.lowercase()?.replace(" ", "")
        return when {
            name in titleStyleNames -> ParagraphStyle.TITLE_LEVEL
            outlineLevel != null && outlineLevel in outlineLevels -> minOf(outlineLevel + 1, MAX_HEADING_LEVEL)
            else -> name?.let(::headingLevelFromName) ?: 0
        }
    }

    // Localized Word templates keep the built-in names, e.g. "heading 2" or "Заголовок 2".
    private fun headingLevelFromName(name: String): Int? =
        headingStyleName.matchEntire(name)?.groupValues?.get(1)?.toInt()?.coerceIn(1, MAX_HEADING_LEVEL)

    private fun chain(styleId: String?): List<StyleDefinition> {
        val chain = ArrayDeque<StyleDefinition>()
        var current = styleId?.let(styles::get)
        // basedOn loops exist in damaged files, so each style is visited once.
        while (current != null && chain.none { it.id == current?.id }) {
            chain.addFirst(current)
            current = current.basedOn?.let(styles::get)
        }
        return chain
    }

    companion object {
        const val PARAGRAPH_TYPE = "paragraph"
        private const val WORD_DEFAULT_SIZE_HALF_POINTS = 20
        private const val MAX_HEADING_LEVEL = 6
        private val outlineLevels = 0..8
        private val titleStyleNames = setOf("title", "название")
        private val headingStyleName = Regex("(?:heading|заголовок)(\\d)")

        val Empty = StyleSheet(emptyMap(), RunProperties(), ParagraphProperties())

        fun parse(input: InputStream): StyleSheet {
            val handler = StylesHandler()
            Xml.parse(input, handler)
            return StyleSheet(handler.styles, handler.defaultRun, handler.defaultParagraph)
        }
    }
}

private class StylesHandler : DefaultHandler() {
    val styles = HashMap<String, StyleDefinition>()
    val defaultRun = RunProperties()
    val defaultParagraph = ParagraphProperties()

    private val path = ArrayList<String>()
    private var current: StyleBuilder? = null

    override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes) {
        val element = localName(localName, qName)
        val parent = path.lastOrNull()
        path += element
        val style = current
        when {
            element == "style" -> current = StyleBuilder(attributes)
            style != null && element == "name" && parent == "style" -> style.name = attributes["val"]
            style != null && element == "basedOn" -> style.basedOn = attributes["val"]
            style != null && element == "tblStylePr" -> style.condition = attributes["type"]?.let { type ->
                style.conditions.getOrPut(type) { TableCondition() }
            }
            // Conditional formatting applies only to the cells it names, never to the style as a whole.
            "tblStylePr" in path -> readCondition(element, parent, attributes, style?.condition)
            parent == "rPr" -> PropertyReader.applyRun(element, attributes, style?.run ?: defaultRun)
            parent == "pPr" || parent == "numPr" -> {
                PropertyReader.applyParagraph(element, attributes, style?.paragraph ?: defaultParagraph)
            }
            parent == "pBdr" -> PropertyReader.applyParagraphBorder(
                element,
                attributes,
                style?.paragraph ?: defaultParagraph
            )
        }
    }

    private fun readCondition(element: String, parent: String?, attributes: Attributes, condition: TableCondition?) {
        condition ?: return
        when {
            parent == "rPr" -> PropertyReader.applyRun(element, attributes, condition.run)
            element == "shd" && parent == "tcPr" -> condition.shading = parseHexColor(attributes["fill"])
        }
    }

    override fun endElement(uri: String?, localName: String?, qName: String?) {
        val closed = path.removeAt(path.lastIndex)
        if (closed == "tblStylePr") current?.condition = null
        if (closed != "style") return
        current?.build()?.let { styles[it.id] = it }
        current = null
    }

    private class StyleBuilder(attributes: Attributes) {
        val id = attributes["styleId"]
        val type = attributes["type"] ?: StyleSheet.PARAGRAPH_TYPE
        val isDefault = attributes.isOn("default")
        var name: String? = null
        var basedOn: String? = null
        val paragraph = ParagraphProperties()
        val run = RunProperties()
        val conditions = HashMap<String, TableCondition>()
        var condition: TableCondition? = null

        fun build(): StyleDefinition? =
            id?.let { StyleDefinition(it, type, name, basedOn, isDefault, paragraph, run, conditions) }
    }
}
