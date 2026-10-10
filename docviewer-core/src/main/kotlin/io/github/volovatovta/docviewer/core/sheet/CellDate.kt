package io.github.volovatovta.docviewer.core.sheet

import java.text.DateFormat
import java.text.DateFormatSymbols
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** How a workbook counts dates: days since 1900 (Excel for Windows) or since 1904 (early Excel for Mac). */
internal enum class DateSystem(private val epochYear: Int, private val epochMonth: Int, private val epochDay: Int) {
    Excel1900(1899, Calendar.DECEMBER, 30),
    Excel1904(1904, Calendar.JANUARY, 1),
    ;

    // Excel 1900 counts a 29 February 1900 that never was, so serials before it start one day later.
    fun dateTime(serial: Double): CellDateTime {
        val days = floor(serial).toInt()
        val seconds = ((serial - days) * SECONDS_PER_DAY).roundToInt()
        val phantomLeapDay = if (this == Excel1900 && days < PHANTOM_LEAP_DAY) 1 else 0
        val calendar = GregorianCalendar(UTC).apply {
            clear()
            set(epochYear, epochMonth, epochDay)
            add(Calendar.DAY_OF_MONTH, days + phantomLeapDay)
            add(Calendar.SECOND, seconds)
        }
        return CellDateTime(calendar, (serial * SECONDS_PER_DAY).roundToLong())
    }

    private companion object {
        const val SECONDS_PER_DAY = 86_400
        const val PHANTOM_LEAP_DAY = 60
        val UTC: TimeZone = TimeZone.getTimeZone("UTC")
    }
}

internal enum class NameStyle { Short, Full, Narrow }

/**
 * The date and time of a cell, split into the fields a format code shows. Built on java.util.Calendar:
 * Android has java.time only from API 26, and the SDK supports API 23.
 */
internal class CellDateTime(private val calendar: Calendar, val elapsedSeconds: Long) {
    val year: Int get() = calendar[Calendar.YEAR]
    val month: Int get() = calendar[Calendar.MONTH] + 1
    val day: Int get() = calendar[Calendar.DAY_OF_MONTH]
    val hour: Int get() = calendar[Calendar.HOUR_OF_DAY]
    val minute: Int get() = calendar[Calendar.MINUTE]
    val second: Int get() = calendar[Calendar.SECOND]

    fun monthName(locale: Locale, style: NameStyle): String {
        val symbols = DateFormatSymbols.getInstance(locale)
        val index = calendar[Calendar.MONTH]
        return when (style) {
            NameStyle.Short -> symbols.shortMonths[index]
            NameStyle.Full -> symbols.months[index]
            NameStyle.Narrow -> symbols.months[index].take(1)
        }
    }

    fun dayName(locale: Locale, style: NameStyle): String {
        val symbols = DateFormatSymbols.getInstance(locale)
        val index = calendar[Calendar.DAY_OF_WEEK]
        return if (style == NameStyle.Short) symbols.shortWeekdays[index] else symbols.weekdays[index]
    }

    /** The reader's own short date style, as Excel uses for its built-in date formats. */
    fun regional(locale: Locale, withTime: Boolean): String {
        val format = if (withTime) {
            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, locale)
        } else {
            DateFormat.getDateInstance(DateFormat.SHORT, locale)
        }
        format.timeZone = calendar.timeZone
        return format.format(calendar.time)
    }
}
