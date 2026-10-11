package io.github.volovatovta.docviewer.core.sheet

import io.github.volovatovta.docviewer.core.CsvHeaderDetector
import io.github.volovatovta.docviewer.core.model.Sheet
import io.github.volovatovta.docviewer.core.model.TableSource
import java.util.TreeMap

/** The largest grid a spreadsheet format can address; anything beyond comes from a damaged or hostile file. */
internal enum class SheetLimits(val rows: Int, val columns: Int) {
    Xlsx(rows = 1_048_576, columns = 16_384),
    Xls(rows = 65_536, columns = 256),
}

/**
 * Collects the cells of one sheet in whatever order a file stores them. Cells are kept sparse, so a value far
 * down or far to the right costs one entry rather than every empty cell before it.
 */
internal class SheetBuilder(private val name: String, private val limits: SheetLimits) {
    private val rows = TreeMap<Int, TreeMap<Int, String>>()

    operator fun set(row: Int, column: Int, text: String) {
        if (text.isEmpty() || row !in 0 until limits.rows || column !in 0 until limits.columns) return
        rows.getOrPut(row) { TreeMap() }[column] = text
    }

    // Leading blank rows and columns keep their place, as they do on screen in Excel; a first row that reads
    // like column names becomes the pinned header.
    fun build(): Sheet {
        val table = SheetTable(rows)
        val sample = List(minOf(table.rowCount, HEADER_SAMPLE_ROWS), table::row)
        return Sheet(name, if (CsvHeaderDetector.looksLikeHeader(sample)) table.withHeader() else table)
    }

    private companion object {
        const val HEADER_SAMPLE_ROWS = 101
    }
}

private class SheetTable(
    private val cells: TreeMap<Int, TreeMap<Int, String>>,
    private val firstRow: Int = 0,
    override val header: List<String>? = null,
) : TableSource {
    override val rowCount: Int = if (cells.isEmpty()) 0 else cells.lastKey() + 1 - firstRow

    override val columnCount: Int = cells.values.maxOfOrNull { it.lastKey() + 1 } ?: 0

    override fun row(index: Int): List<String> {
        val row = cells[index + firstRow] ?: return emptyList()
        return List(row.lastKey() + 1) { column -> row[column].orEmpty() }
    }

    fun withHeader() = SheetTable(cells, firstRow = 1, header = row(0))
}
