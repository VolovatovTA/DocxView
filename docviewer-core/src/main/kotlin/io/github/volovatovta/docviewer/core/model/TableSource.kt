package io.github.volovatovta.docviewer.core.model

import java.io.Closeable

/** Rows of a table: a CSV file held in memory or read from disk on demand, or one sheet of a workbook. */
public interface TableSource : Closeable {
    public val header: List<String>?
    public val rowCount: Int
    public val columnCount: Int

    @Deprecated("Only CSV has a delimiter: read it from TableDocument or CsvFile.")
    public val delimiter: Char get() = ','

    public fun row(index: Int): List<String>

    /** Visits every row in order; disk-backed sources stream instead of caching. */
    public fun forEachRow(action: (index: Int, row: List<String>) -> Unit) {
        for (index in 0 until rowCount) action(index, row(index))
    }

    override fun close() {}
}
