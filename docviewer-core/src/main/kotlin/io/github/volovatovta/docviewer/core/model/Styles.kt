package io.github.volovatovta.docviewer.core.model

public enum class VerticalAlign { Baseline, Superscript, Subscript }

public enum class TextAlignment { Start, Center, End, Justify }

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
