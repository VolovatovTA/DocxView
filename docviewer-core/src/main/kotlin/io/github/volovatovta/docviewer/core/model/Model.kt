package io.github.volovatovta.docviewer.core.model

import java.io.Closeable

public enum class DocumentFormat(public val extensions: Set<String>, public val mimeTypes: Set<String>) {
    PDF(setOf("pdf"), setOf("application/pdf")),
    DOCX(
        setOf("docx", "docm", "dotx", "dotm"),
        setOf(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.template",
            "application/vnd.ms-word.document.macroenabled.12",
        ),
    ),
    DOC(setOf("doc", "dot"), setOf("application/msword", "application/vnd.ms-word")),
    CSV(setOf("csv", "tsv", "tab"), setOf("text/csv", "text/comma-separated-values", "text/tab-separated-values", "application/csv")),
    TXT(setOf("txt", "text", "log", "md", "json", "xml"), setOf("text/plain", "text/markdown", "application/json"));

    public companion object {
        public fun fromExtension(extension: String?): DocumentFormat? {
            val ext = extension?.lowercase()?.removePrefix(".") ?: return null
            return entries.firstOrNull { ext in it.extensions }
        }

        public fun fromMimeType(mimeType: String?): DocumentFormat? {
            val mime = mimeType?.lowercase()?.substringBefore(';')?.trim() ?: return null
            return entries.firstOrNull { mime in it.mimeTypes }
        }
    }
}

// Reflowable document: DOCX, DOC and plain text are all converted into this tree.
public class FlowDocument(
    public val blocks: List<Block>,
    public val title: String? = null,
    public val images: ImageStore = ImageStore.Empty,
    public val warnings: List<String> = emptyList(),
) : Closeable {
    override fun close(): Unit = images.close()
}

public interface ImageStore : Closeable {
    public fun open(imageId: String): ByteArray?

    override fun close() {}

    public object Empty : ImageStore {
        override fun open(imageId: String): ByteArray? = null
    }
}

public sealed interface Block

public class Paragraph(
    public val inlines: List<Inline>,
    public val style: ParagraphStyle = ParagraphStyle.Default,
) : Block {
    // Text of all runs without images; search offsets refer to this string.
    public val plainText: String by lazy(LazyThreadSafetyMode.NONE) {
        buildString { inlines.forEach { if (it is TextRun) append(it.text) } }
    }

    public val isBlank: Boolean get() = inlines.none { it is ImageRun } && plainText.isBlank()

    override fun toString(): String = "Paragraph(${style.headingLevel.takeIf { it > 0 }?.let { "h$it " } ?: ""}\"$plainText\")"
}

public class Table(
    public val rows: List<TableRow>,
    // Relative column widths from the source document, empty when unknown.
    public val columnWidths: List<Float> = emptyList(),
) : Block {
    public val columnCount: Int = maxOf(columnWidths.size, rows.maxOfOrNull { row -> row.cells.sumOf { it.colSpan } } ?: 0)

    override fun toString(): String = "Table(${rows.size}x$columnCount)"
}

public class TableRow(public val cells: List<TableCell>, public val isHeader: Boolean = false)

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

public sealed interface Inline

public class TextRun(
    public val text: String,
    public val style: RunStyle = RunStyle.Default,
    public val link: String? = null,
) : Inline {
    override fun toString(): String = "TextRun(\"$text\", $style${link?.let { ", link=$it" } ?: ""})"
}

public class ImageRun(
    public val imageId: String,
    public val widthPt: Float? = null,
    public val heightPt: Float? = null,
    public val description: String? = null,
) : Inline

public enum class VerticalAlign { Baseline, Superscript, Subscript }

public data class RunStyle(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strikethrough: Boolean = false,
    // ARGB colors; null means "use the viewer default".
    val color: Int? = null,
    val background: Int? = null,
    // Size relative to the document's default body text size (1.0 = normal text).
    val relativeSize: Float? = null,
    val verticalAlign: VerticalAlign = VerticalAlign.Baseline,
    val monospace: Boolean = false,
    val allCaps: Boolean = false,
    val smallCaps: Boolean = false,
) {
    public companion object {
        public val Default: RunStyle = RunStyle()
    }
}

public enum class TextAlignment { Start, Center, End, Justify }

public data class ParagraphStyle(
    val alignment: TextAlignment = TextAlignment.Start,
    // 0 = body text, 1..6 = headings, TITLE_LEVEL = document title.
    val headingLevel: Int = 0,
    val indentStartPt: Float = 0f,
    val firstLineIndentPt: Float = 0f,
    val spaceBeforePt: Float? = null,
    val spaceAfterPt: Float? = null,
    val listMarker: String? = null,
    val listLevel: Int = 0,
    val background: Int? = null,
) {
    public companion object {
        public const val TITLE_LEVEL: Int = 100
        public val Default: ParagraphStyle = ParagraphStyle()
    }
}

public class TableDocument(
    public val header: List<String>?,
    public val rows: List<List<String>>,
    public val delimiter: Char,
    public val columnCount: Int,
    public val truncated: Boolean = false,
    public val charsetName: String? = null,
)
