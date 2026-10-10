package io.github.volovatovta.docviewer.core.sheet

import io.github.volovatovta.docviewer.core.CsvHeaderDetector
import io.github.volovatovta.docviewer.core.model.Sheet
import io.github.volovatovta.docviewer.core.model.TableSource

/** Collects the cells of one sheet in whatever order a file stores them, then lays them out as rows. */
internal class SheetBuilder(private val name: String) {
    private val rows = ArrayList<ArrayList<String>?>()

    operator fun set(row: Int, column: Int, text: String) {
        if (text.isEmpty() || row < 0 || column < 0) return
        while (rows.size <= row) rows += null
        val cells = rows[row] ?: ArrayList<String>().also { rows[row] = it }
        while (cells.size <= column) cells += ""
        cells[column] = text
    }

    // Leading blank rows and columns keep their place, as they do on screen in Excel; a first row
    // that reads like column names becomes the pinned header.
    fun build(): Sheet {
        val table = rows.map { it.orEmpty() }
        val header = table.firstOrNull()?.takeIf { CsvHeaderDetector.looksLikeHeader(table) }
        val body = if (header != null) table.drop(1) else table
        return Sheet(name, SheetTable(header, body, table.maxOfOrNull { it.size } ?: 0))
    }
}

private class SheetTable(
    override val header: List<String>?,
    private val rows: List<List<String>>,
    override val columnCount: Int,
) : TableSource {
    override val rowCount: Int get() = rows.size

    override fun row(index: Int): List<String> = rows[index]
}
