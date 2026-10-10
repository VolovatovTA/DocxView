package io.github.volovatovta.docviewer.core.docx

import io.github.volovatovta.docviewer.core.model.Block
import io.github.volovatovta.docviewer.core.model.ImageRun
import io.github.volovatovta.docviewer.core.model.Inline
import io.github.volovatovta.docviewer.core.model.RunStyle
import io.github.volovatovta.docviewer.core.model.Table
import io.github.volovatovta.docviewer.core.model.TableCell
import io.github.volovatovta.docviewer.core.model.TableRow
import io.github.volovatovta.docviewer.core.model.TextAlignment
import io.github.volovatovta.docviewer.core.model.TextRun
import io.github.volovatovta.docviewer.core.ooxml.Relationships
import io.github.volovatovta.docviewer.core.ooxml.get
import org.xml.sax.Attributes

internal class ParagraphBuilder {
    val direct = ParagraphProperties()
    var run: RunProperties? = null
    val inlines = ArrayList<Inline>()

    // Text boxes anchored in this paragraph, shown right after it.
    val trailing = ArrayList<Block>()
    var pageBreakBefore = false
    var pageBreakAfter = false

    // Word splits text into many tiny runs; neighbours with identical formatting are joined back.
    fun appendText(text: String, style: RunStyle, link: String?) {
        val last = inlines.lastOrNull()
        if (last is TextRun && last.style == style && last.link == link) {
            inlines[inlines.lastIndex] = TextRun(last.text + text, style, link)
        } else {
            inlines += TextRun(text, style, link)
        }
    }

    fun breakPage() {
        if (inlines.isEmpty()) pageBreakBefore = true else pageBreakAfter = true
    }
}

internal class TableBuilder(private val styles: StyleSheet) {
    private val gridColumns = ArrayList<Int>()
    private val rows = ArrayList<RowBuilder>()
    var styleId: String? = null
    var look = TableLook()

    val currentCell: CellBuilder? get() = rows.lastOrNull()?.cells?.lastOrNull()

    fun addGridColumn(widthTwips: Int) {
        gridColumns += widthTwips
    }

    fun startRow() {
        rows += RowBuilder()
    }

    fun markHeaderRow(isHeader: Boolean) {
        rows.lastOrNull()?.isHeader = isHeader
    }

    // The last row is unknown while cells stream in, so only its shading is applied, once the table is complete.
    fun startCell(): CellBuilder {
        val row = rows.lastOrNull()
        val rowIndex = (rows.size - 1).coerceAtLeast(0)
        val column = row?.cells?.sumOf { it.colSpan } ?: 0
        val cell = CellBuilder()
        cell.styleCondition = styles.tableCondition(styleId, look.conditionsOf(rowIndex, column, gridColumns.size))
        row?.cells?.add(cell)
        return cell
    }

    fun build(): Table {
        if (look.lastRow) {
            val shading = styles.tableCondition(styleId, listOf(TableLook.LAST_ROW)).shading
            rows.lastOrNull()?.cells?.forEach { cell -> shading?.let { cell.styleCondition.shading = it } }
        }
        val widths = if (gridColumns.isNotEmpty() && gridColumns.all { it > 0 }) {
            gridColumns.map(OoxmlUnits::twipsToPoints)
        } else {
            emptyList()
        }
        return Table(rows.filter { it.cells.isNotEmpty() }.map(RowBuilder::build), widths)
    }
}

internal class RowBuilder {
    var isHeader = false
    val cells = ArrayList<CellBuilder>()

    fun build() = TableRow(cells.map(CellBuilder::build), isHeader)
}

internal class CellBuilder {
    val blocks = ArrayList<Block>()
    var colSpan = 1
    var verticalMerge: String? = null
    var background: Int? = null
    var styleCondition = TableCondition()

    fun build(): TableCell {
        val continuesAbove = verticalMerge == CONTINUE
        return TableCell(
            blocks = if (continuesAbove) emptyList() else blocks,
            colSpan = colSpan.coerceAtLeast(1),
            mergedWithAbove = continuesAbove,
            background = background ?: styleCondition.shading,
        )
    }

    companion object {
        // A bare w:vMerge means "continue the merged cell from the row above".
        const val CONTINUE = "continue"
    }
}

internal class ImageBuilder {
    var relationshipId: String? = null
    var floating = false

    // wp:positionH/wp:align of a floating image: left, center, right, inside or outside.
    var horizontalAlign: String? = null
    private var widthPt: Float? = null
    private var heightPt: Float? = null
    private var description: String? = null

    fun readExtent(attributes: Attributes) {
        attributes["cx"]?.toLongOrNull()?.let { widthPt = OoxmlUnits.emuToPoints(it) }
        attributes["cy"]?.toLongOrNull()?.let { heightPt = OoxmlUnits.emuToPoints(it) }
    }

    fun readDescription(attributes: Attributes) {
        description = attributes["descr"]?.takeIf { it.isNotBlank() } ?: attributes["title"]
    }

    // Legacy VML pictures carry their size in a CSS-like style attribute: "width:120pt;height:3cm".
    fun readVmlStyle(style: String?) {
        val declarations = style?.split(';').orEmpty()
            .map { declaration -> declaration.split(':', limit = 2).map(String::trim) }
            .filter { it.size == 2 }
        for ((property, value) in declarations) {
            when (property) {
                "width" -> cssLengthToPoints(value)?.let { widthPt = it }
                "height" -> cssLengthToPoints(value)?.let { heightPt = it }
            }
        }
    }

    fun build(relationships: Relationships): ImageRun? =
        relationships.internalTarget(relationshipId)?.let { target ->
            ImageRun(target, widthPt, heightPt, description, floatAlignment.takeIf { floating })
        }

    private val floatAlignment: TextAlignment
        get() = when (horizontalAlign) {
            "center" -> TextAlignment.Center
            "right", "outside" -> TextAlignment.End
            else -> TextAlignment.Start
        }

    private fun cssLengthToPoints(value: String): Float? {
        val number = value.takeWhile { it.isDigit() || it in NUMBER_SIGNS }
        val unit = value.drop(number.length).trim()
        return number.toFloatOrNull()?.let { amount -> pointsPerCssUnit[unit]?.let { amount * it } }
    }

    private companion object {
        const val NUMBER_SIGNS = ".-"
        val pointsPerCssUnit = mapOf("pt" to 1f, "" to 1f, "in" to 72f, "cm" to 28.35f, "mm" to 2.835f, "px" to 0.75f)
    }
}
