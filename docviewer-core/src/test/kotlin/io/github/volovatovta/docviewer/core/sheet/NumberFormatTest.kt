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
    fun `fractions find the closest denominator the code allows`() {
        assertEquals("1 1/4", format("# ?/?", 1.25))
        assertEquals(" 1/2", format("# ?/?", 0.5))
        assertEquals("2    ", format("# ?/?", 2.0))
        assertEquals("3  1/3 ", format("# ??/??", 3.333))
        assertEquals("5/8", format("?/8", 0.625))
        assertEquals("-1 1/2", format("# ?/?", -1.5))
        assertEquals("7/4", format("?/?", 1.75))
    }

    @Test
    fun `dates Excel cannot show and the days it invents`() {
        assertEquals("00.01.1900", format("dd.mm.yyyy", 0.0))
        assertEquals("00.01.1900 12:00", format("dd.mm.yyyy hh:mm", 0.5))
        assertEquals("29.02.1900", format("dd.mm.yyyy", 60.0))
        assertEquals("#####", format("dd.mm.yyyy", -1.0))
        assertEquals("#####", format("dd.mm.yyyy", 3_000_000.0))
        assertEquals("31.12.9999", format("dd.mm.yyyy", 2_958_465.0))
        assertEquals("#####", NumberFormat.builtIn(14).format(-5.0, us))
    }

    @Test
    fun `general shows up to ten significant digits`() {
        assertEquals("42", NumberFormat.General.format(42.0, us))
        assertEquals("0.1", NumberFormat.General.format(0.1, us))
        assertEquals("3.141592654", NumberFormat.General.format(Math.PI, us))
        assertEquals("3,5", NumberFormat.General.format(3.5, germany))
    }

    @Test
    fun `numbers Excel cannot store show its error instead of failing the workbook`() {
        assertEquals("#NUM!", format("0.00", Double.NaN))
        assertEquals("#NUM!", NumberFormat.General.format(Double.POSITIVE_INFINITY, us))
        assertEquals("#NUM!", format("dd.mm.yyyy", Double.NEGATIVE_INFINITY))
    }

    @Test
    fun `General keeps its meaning inside sections and next to colors`() {
        assertEquals("1234.5", format("[Red]General", 1234.5))
        assertEquals("-5", format("General;[Red]-General", -5.0))
        assertEquals("Total 12", format("\"Total \"General", 12.0))
    }

    @Test
    fun `elapsed time counts the whole duration`() {
        assertEquals("29628:00:00", format("[h]:mm:ss", 1234.5))
        assertEquals("720:00", format("[mm]:ss", 0.5))
        assertEquals("90", format("[s]", 90.0 / 86_400))
    }

    @Test
    fun `dates use plain digits in every locale, like the numbers next to them`() {
        val arabic = FormatContext(DateSystem.Excel1900, Locale.forLanguageTag("ar-EG"))
        assertEquals("10.10.2026", format("dd.mm.yyyy", 46_305.0, arabic))
    }
}
