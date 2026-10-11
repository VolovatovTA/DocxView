package io.github.volovatovta.docviewer.core.model

import java.io.Closeable

/** The sheets of a spreadsheet in tab order; a CSV file is a workbook of one sheet. */
public class Workbook internal constructor(public val sheets: List<Sheet>) : Closeable {
    init {
        require(sheets.isNotEmpty()) { "A workbook has at least one sheet" }
    }

    override fun close(): Unit = sheets.forEach { it.table.close() }
}

/** One tab of a workbook. */
public class Sheet internal constructor(public val name: String, public val table: TableSource)
