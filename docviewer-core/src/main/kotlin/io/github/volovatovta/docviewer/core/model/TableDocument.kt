package io.github.volovatovta.docviewer.core.model

public class TableDocument(
    public val header: List<String>?,
    public val rows: List<List<String>>,
    public val delimiter: Char,
    public val columnCount: Int,
    public val truncated: Boolean = false,
    public val charsetName: String? = null,
)
