package io.github.volovatovta.docviewer.core.model

public sealed interface Block

public class Paragraph(
    public val inlines: List<Inline>,
    public val style: ParagraphStyle = ParagraphStyle.Default,
) : Block {
    // Text of all runs without images; search offsets refer to this string.
    public val plainText: String by lazy(LazyThreadSafetyMode.NONE) {
        inlines.filterIsInstance<TextRun>().joinToString(separator = "") { it.text }
    }

    public val isBlank: Boolean get() = inlines.none { it is ImageRun } && plainText.isBlank()

    override fun toString(): String {
        val heading = if (style.headingLevel > 0) "h${style.headingLevel} " else ""
        return "Paragraph($heading\"$plainText\")"
    }
}

public class Table(
    public val rows: List<TableRow>,
    // Relative column widths from the source document, empty when unknown.
    public val columnWidths: List<Float> = emptyList(),
) : Block {
    public val columnCount: Int = maxOf(columnWidths.size, rows.maxOfOrNull { it.spannedColumns } ?: 0)

    override fun toString(): String = "Table(${rows.size}x$columnCount)"
}

public class TableRow(public val cells: List<TableCell>, public val isHeader: Boolean = false) {
    internal val spannedColumns: Int get() = cells.sumOf { it.colSpan }
}

public class TableCell(
    public val blocks: List<Block>,
    public val colSpan: Int = 1,
    // True for cells vertically merged into the cell above; they render empty without a top border.
    public val mergedWithAbove: Boolean = false,
    public val background: Int? = null,
)

public data object PageBreak : Block

// Short horizontal rule, e.g. before footnotes.
public data object Divider : Block
