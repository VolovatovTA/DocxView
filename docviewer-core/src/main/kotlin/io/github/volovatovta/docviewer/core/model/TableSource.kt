package io.github.volovatovta.docviewer.core.model

import java.io.Closeable

/** Rows of a table, held in memory ([TableDocument]) or streamed from disk on demand (CsvFile). */
public interface TableSource : Closeable {
    public val header: List<String>?
    public val rowCount: Int
    public val columnCount: Int
    public val delimiter: Char

    public fun row(index: Int): List<String>

    /** Visits every row in order; disk-backed sources stream instead of caching. */
    public fun forEachRow(action: (index: Int, row: List<String>) -> Unit) {
        for (index in 0 until rowCount) action(index, row(index))
    }

    override fun close() {}
}
