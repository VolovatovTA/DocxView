package io.github.volovatovta.docviewer.core.sheet

import org.junit.Test
import java.util.Locale
import kotlin.test.assertEquals

class NumberFormatTest {

    private val us = FormatContext(DateSystem.Excel1900, Locale.US)
    private val germany = FormatContext(DateSystem.Excel1900, Locale.GERMANY)

    private fun format(code: String, value: Double, context: FormatContext = us) = NumberFormat.of(
        code
    ).format(value, context)

    @Test
    fun `numbers follow placeholders, grouping, percent and the reader's separators`() {
        assertEquals("1,234.50", format("#,##0.00", 1234.5))
        assertEquals("1.234,50", format("#,##0.00", 1234.5, germany))
        assertEquals("7%", format("0%", 0.07))
        assertEquals("12.3%", format("0.0%", 0.123))
        assertEquals(".5", format("#.##", 0.5))
        assertEquals("1,235", format("#,##0,", 1_234_567.0))
        assertEquals("1.23E+04", format("0.00E+00", 12_345.0))
    }

    @Test
    fun `sections pick negative and zero formats, literals and currencies stay`() {
        assertEquals("(1,234)", format("#,##0 ;(#,##0)", -1234.0))
        assertEquals("-", format("0.00;-0.00;\"-\"", 0.0))
        assertEquals("$ 9.99", format("\"$ \"0.00", 9.99))
        assertEquals("€12.00", format("[\$€-407]0.00", 12.0))
        assertEquals("42", format("[Red]0", 42.0))
    }

    @Test
    fun `dates tell months from minutes and handle both date systems`() {
        val serial = 46_305.604_166_666_67 // 10.10.2026 14:30
        assertEquals("10.10.2026", format("dd.mm.yyyy", serial))
        assertEquals("14:30", format("hh:mm", serial))
        assertEquals("2:30 PM", format("h:mm AM/PM", serial))
        assertEquals("Oct 10, 2026", format("mmm d, yyyy", serial))
        assertEquals("Saturday", format("dddd", serial))
        assertEquals(
            "01.01.1904",
            NumberFormat.of("dd.mm.yyyy").format(0.0, FormatContext(DateSystem.Excel1904, Locale.US))
        )
        assertEquals("01.01.1900", format("dd.mm.yyyy", 1.0))
        assertEquals("01.03.1900", format("dd.mm.yyyy", 61.0))
    }

    @Test
    fun `general shows up to ten significant digits`() {
        assertEquals("42", NumberFormat.General.format(42.0, us))
        assertEquals("0.1", NumberFormat.General.format(0.1, us))
        assertEquals("3.141592654", NumberFormat.General.format(Math.PI, us))
        assertEquals("3,5", NumberFormat.General.format(3.5, germany))
    }
}
