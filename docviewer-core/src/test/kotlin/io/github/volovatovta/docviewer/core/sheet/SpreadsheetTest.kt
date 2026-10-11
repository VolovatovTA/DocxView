package io.github.volovatovta.docviewer.core.sheet

import io.github.volovatovta.docviewer.core.format.DocumentContent
import io.github.volovatovta.docviewer.core.format.DocumentReader
import io.github.volovatovta.docviewer.core.model.Workbook
import io.github.volovatovta.docviewer.core.resource
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// The same workbook saved by LibreOffice as .xlsx and as .xls must read the same way.
@RunWith(Parameterized::class)
class SpreadsheetTest(private val fileName: String) {

    private val workbook: Workbook =
        (DocumentReader.read(resource("sheets/$fileName")) as DocumentContent.Sheets).workbook

    @Test
    fun `visible sheets keep their order and names, hidden ones are skipped`() {
        assertEquals(listOf("Sales", "Inventory"), workbook.sheets.map { it.name })
    }

    @Test
    fun `cells show what Excel shows`() {
        val sales = workbook.sheets.first().table
        assertEquals(listOf("Region", "Date", "Amount", "Share", "Paid", "Doubled"), sales.header)
        assertEquals(listOf("Север", "10.10.2026", "12,345.68", "25.6%", "TRUE", "0.00"), sales.row(0))
        assertEquals(listOf("South", "2026-01-31 14:30", "-7.50", "100.0%", "FALSE", "#DIV/0!"), sales.row(1))
        assertEquals("1,234,567", sales.row(2)[1])
        assertTrue(sales.row(2)[0].length > 1000, "long strings arrive whole")
    }

    @Test
    fun `a larger sheet keeps every row`() {
        val inventory = workbook.sheets[1].table
        assertEquals(listOf("SKU", "Qty"), inventory.header)
        assertEquals(50, inventory.rowCount)
        assertEquals(listOf("A-050", "150"), inventory.row(49))
    }

    companion object {
        private lateinit var previousLocale: Locale

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun files() = listOf("workbook.xlsx", "workbook.xls")

        @JvmStatic
        @BeforeClass
        fun useUsLocale() {
            previousLocale = Locale.getDefault()
            Locale.setDefault(Locale.US)
        }

        @JvmStatic
        @AfterClass
        fun restoreLocale() = Locale.setDefault(previousLocale)
    }
}
