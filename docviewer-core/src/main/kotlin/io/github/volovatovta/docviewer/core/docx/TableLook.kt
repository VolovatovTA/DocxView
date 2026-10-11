package io.github.volovatovta.docviewer.core.docx

import io.github.volovatovta.docviewer.core.ooxml.get
import org.xml.sax.Attributes

// w:tblLook: which parts of the table style a table uses; bands are counted after the header row and column.
internal class TableLook(
    val firstRow: Boolean = true,
    val lastRow: Boolean = false,
    val firstColumn: Boolean = true,
    val lastColumn: Boolean = false,
    val horizontalBands: Boolean = true,
    val verticalBands: Boolean = false,
) {
    // From weakest to strongest, as Word applies them.
    fun conditionsOf(row: Int, column: Int, columnCount: Int): List<String> =
        listOfNotNull(WHOLE_TABLE, verticalBand(column), horizontalBand(row)) + edges(row, column, columnCount)

    private fun verticalBand(column: Int): String? {
        val band = column - if (firstColumn) 1 else 0
        return if (!verticalBands || band < 0) null else if (band % 2 == 0) "band1Vert" else "band2Vert"
    }

    private fun horizontalBand(row: Int): String? {
        val band = row - if (firstRow) 1 else 0
        return if (!horizontalBands || band < 0) null else if (band % 2 == 0) "band1Horz" else "band2Horz"
    }

    private fun edges(row: Int, column: Int, columnCount: Int): List<String> = listOfNotNull(
        "firstCol".takeIf { firstColumn && column == 0 },
        "lastCol".takeIf { lastColumn && columnCount > 0 && column == columnCount - 1 },
        "firstRow".takeIf { firstRow && row == 0 },
    )

    companion object {
        const val LAST_ROW = "lastRow"
        private const val WHOLE_TABLE = "wholeTable"
        private const val FIRST_ROW_BIT = 0x0020
        private const val LAST_ROW_BIT = 0x0040
        private const val FIRST_COLUMN_BIT = 0x0080
        private const val LAST_COLUMN_BIT = 0x0100
        private const val NO_HORIZONTAL_BANDS_BIT = 0x0200
        private const val NO_VERTICAL_BANDS_BIT = 0x0400
        private const val HEX_RADIX = 16

        // Word 2010+ writes one attribute per flag; Word 2007 packs them into a hex bit mask in w:val.
        fun of(attributes: Attributes): TableLook {
            val mask = attributes["val"]?.toIntOrNull(HEX_RADIX) ?: 0
            fun flag(
                name: String,
                bit: Int
            ) = attributes[name]?.let { it == "1" || it == "true" } ?: (mask and bit != 0)
            return TableLook(
                firstRow = flag("firstRow", FIRST_ROW_BIT),
                lastRow = flag("lastRow", LAST_ROW_BIT),
                firstColumn = flag("firstColumn", FIRST_COLUMN_BIT),
                lastColumn = flag("lastColumn", LAST_COLUMN_BIT),
                horizontalBands = !flag("noHBand", NO_HORIZONTAL_BANDS_BIT),
                verticalBands = !flag("noVBand", NO_VERTICAL_BANDS_BIT),
            )
        }
    }
}
