package io.github.volovatovta.docviewer.core.model

public class TableDocument(
    override val header: List<String>?,
    public val rows: List<List<String>>,
    override val delimiter: Char,
    override val columnCount: Int,
    public val truncated: Boolean = false,
    public val charsetName: String? = null,
) : TableSource {
    override val rowCount: Int get() = rows.size

    override fun row(index: Int): List<String> = rows[index]
}
