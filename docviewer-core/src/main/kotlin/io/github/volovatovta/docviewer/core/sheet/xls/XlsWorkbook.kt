package io.github.volovatovta.docviewer.core.sheet.xls

import io.github.volovatovta.docviewer.core.PasswordProtectedException
import io.github.volovatovta.docviewer.core.UnsupportedFormatException
import io.github.volovatovta.docviewer.core.binary.float64
import io.github.volovatovta.docviewer.core.binary.int32
import io.github.volovatovta.docviewer.core.binary.uint16
import io.github.volovatovta.docviewer.core.binary.uint32
import io.github.volovatovta.docviewer.core.binary.uint8
import io.github.volovatovta.docviewer.core.model.Workbook
import io.github.volovatovta.docviewer.core.ole.CompoundFile
import io.github.volovatovta.docviewer.core.sheet.DateSystem
import io.github.volovatovta.docviewer.core.sheet.FormatContext
import io.github.volovatovta.docviewer.core.sheet.NumberFormat
import io.github.volovatovta.docviewer.core.sheet.SheetBuilder
import io.github.volovatovta.docviewer.core.sheet.SheetLimits

/**
 * Reads the visible worksheets of an Excel 97–2003 workbook ([MS-XLS], BIFF8): first the workbook globals
 * (strings, number formats, cell styles, sheet list), then each sheet's cells from its own substream.
 */
internal class XlsWorkbook(private val stream: ByteArray) {

    private val globals = Globals()

    fun read(): Workbook {
        stream.biffRecords().forEach(globals::accept)
        val context = FormatContext(globals.dateSystem)
        val sheets = globals.sheets.filter { it.isVisibleWorksheet }.map { entry ->
            SheetBuilder(entry.name, SheetLimits.Xls).also { sheet -> readCells(entry.offset, sheet, context) }.build()
        }
        if (sheets.isEmpty()) throw UnsupportedFormatException("The workbook has no worksheets")
        return Workbook(sheets)
    }

    // A formula with a text result is followed by a STRING record that carries the text.
    private fun readCells(offset: Int, sheet: SheetBuilder, context: FormatContext) {
        var formulaWaitingForText: CellHeader? = null
        for (record in stream.biffRecords(offset)) {
            when (record.type) {
                in SINGLE_VALUE_CELLS -> {
                    val cell = CellHeader(record.data)
                    sheet[cell] = value(record, cell, context)
                }
                BiffType.MULRK -> numbers(record.data, sheet, context)
                BiffType.FORMULA -> formulaWaitingForText = formula(record.data, sheet, context)
                BiffType.STRING -> formulaWaitingForText?.let { sheet[it] = record.reader().unicodeString() }
            }
        }
    }

    private fun value(record: BiffRecord, cell: CellHeader, context: FormatContext): String {
        val data = record.data
        return when (record.type) {
            BiffType.LABELSST -> globals.strings.getOrElse(data.int32(VALUE)) { "" }
            BiffType.LABEL -> record.reader(VALUE).unicodeString()
            BiffType.NUMBER -> globals.format(cell.style).format(data.float64(VALUE), context)
            BiffType.RK -> globals.format(cell.style).format(rk(data.int32(VALUE)), context)
            else -> booleanOrError(data.uint8(VALUE), isError = data.uint8(VALUE + 1) != 0)
        }
    }

    // A formula result is a number, or a marker that it is text, a boolean or an error ([MS-XLS] 2.5.133).
    private fun formula(data: ByteArray, sheet: SheetBuilder, context: FormatContext): CellHeader? {
        val cell = CellHeader(data)
        if (data.uint16(VALUE + RESULT_MARKER) != NOT_A_NUMBER) {
            sheet[cell] = globals.format(cell.style).format(data.float64(VALUE), context)
            return null
        }
        when (data.uint8(VALUE)) {
            TEXT_RESULT -> return cell
            BOOLEAN_RESULT -> sheet[cell] = booleanOrError(data.uint8(VALUE + RESULT_PAYLOAD), isError = false)
            ERROR_RESULT -> sheet[cell] = booleanOrError(data.uint8(VALUE + RESULT_PAYLOAD), isError = true)
        }
        return null
    }

    // MULRK: one row, consecutive columns, a style and an RK number for each ([MS-XLS] 2.4.175).
    private fun numbers(data: ByteArray, sheet: SheetBuilder, context: FormatContext) {
        val row = data.uint16(0)
        val firstColumn = data.uint16(COLUMN)
        val count = (data.size - MULRK_FIXED_BYTES) / MULRK_ITEM_BYTES
        repeat(count) { index ->
            val item = MULRK_ITEMS + index * MULRK_ITEM_BYTES
            val number = rk(data.int32(item + STYLE_BYTES))
            sheet[row, firstColumn + index] = globals.format(data.uint16(item)).format(number, context)
        }
    }

    private operator fun SheetBuilder.set(cell: CellHeader, text: String) {
        this[cell.row, cell.column] = text
    }

    private companion object {
        const val COLUMN = 2
        const val VALUE = 6
        const val STYLE_BYTES = 2
        const val RESULT_MARKER = 6
        const val RESULT_PAYLOAD = 2
        const val NOT_A_NUMBER = 0xFFFF
        const val TEXT_RESULT = 0
        const val BOOLEAN_RESULT = 1
        const val ERROR_RESULT = 2
        const val MULRK_ITEMS = 4
        const val MULRK_ITEM_BYTES = 6
        const val MULRK_FIXED_BYTES = 6
        val SINGLE_VALUE_CELLS = setOf(
            BiffType.LABELSST,
            BiffType.LABEL,
            BiffType.NUMBER,
            BiffType.RK,
            BiffType.BOOLERR,
        )
    }
}

/** The row, column and style index every BIFF8 cell record starts with ([MS-XLS] 2.5.19). */
private class CellHeader(data: ByteArray) {
    val row = data.uint16(0)
    val column = data.uint16(COLUMN)
    val style = data.uint16(STYLE)

    private companion object {
        const val COLUMN = 2
        const val STYLE = 4
    }
}

/** Workbook-wide records that every sheet refers to. */
private class Globals {
    val strings = ArrayList<String>()
    val sheets = ArrayList<SheetEntry>()
    var dateSystem = DateSystem.Excel1900
        private set
    private val customFormats = HashMap<Int, NumberFormat>()
    private val styleFormats = ArrayList<Int>()
    private var inGlobals = true

    fun format(style: Int): NumberFormat {
        val id = styleFormats.getOrElse(style) { 0 }
        // Built-in codes are parsed once per workbook, not once per cell.
        return customFormats.getOrPut(id) { NumberFormat.builtIn(id) }
    }

    fun accept(record: BiffRecord) {
        if (!inGlobals) return
        val data = record.data
        when (record.type) {
            BiffType.FILEPASS -> throw PasswordProtectedException()
            BiffType.DATEMODE -> if (data.uint16(0) == 1) dateSystem = DateSystem.Excel1904
            BiffType.FORMAT -> {
                val code = record.reader(FORMAT_CODE).unicodeString()
                customFormats[data.uint16(0)] = NumberFormat.of(code)
            }
            BiffType.XF -> styleFormats += data.uint16(XF_FORMAT)
            BiffType.BOUNDSHEET -> sheets += SheetEntry(record)
            BiffType.SST -> readStrings(record)
            BiffType.EOF -> inGlobals = false
        }
    }

    private fun readStrings(record: BiffRecord) {
        val reader = record.reader(SST_STRINGS)
        val count = record.data.int32(SST_UNIQUE_COUNT)
        var read = 0
        // The count comes from the file, so reading stops where the strings end.
        while (read++ < count && !reader.exhausted) strings += reader.richString()
    }

    private companion object {
        const val FORMAT_CODE = 2
        const val XF_FORMAT = 2
        const val SST_UNIQUE_COUNT = 4
        const val SST_STRINGS = 8
    }
}

/** BOUNDSHEET: where a sheet's substream starts, whether it is shown, and its name ([MS-XLS] 2.4.28). */
private class SheetEntry(record: BiffRecord) {
    val offset = record.data.uint32(0).toInt()
    val isVisibleWorksheet = record.data.uint8(VISIBILITY) == VISIBLE && record.data.uint8(KIND) == WORKSHEET
    val name: String = record.reader(NAME).unicodeString(lengthBytes = 1)

    private companion object {
        const val VISIBILITY = 4
        const val KIND = 5
        const val NAME = 6
        const val VISIBLE = 0
        const val WORKSHEET = 0
    }
}

// RK packs a number into 30 bits: an integer or the top of a double, maybe divided by 100 ([MS-XLS] 2.5.217).
private fun rk(value: Int): Double {
    val number = if (value and RK_INTEGER != 0) {
        (value shr 2).toDouble()
    } else {
        Double.fromBits((value and RK_DOUBLE_BITS).toLong() shl Int.SIZE_BITS)
    }
    return if (value and RK_HUNDREDTHS != 0) number / HUNDRED else number
}

private fun booleanOrError(value: Int, isError: Boolean): String = when {
    isError -> errorNames[value] ?: "#ERROR"
    value != 0 -> "TRUE"
    else -> "FALSE"
}

private val errorNames = mapOf(
    0x00 to "#NULL!",
    0x07 to "#DIV/0!",
    0x0F to "#VALUE!",
    0x17 to "#REF!",
    0x1D to "#NAME?",
    0x24 to "#NUM!",
    0x2A to "#N/A",
)

private const val RK_HUNDREDTHS = 0x01
private const val RK_INTEGER = 0x02
private const val RK_DOUBLE_BITS = -0x4
private const val HUNDRED = 100.0

/** The BIFF8 stream of an OLE container; Excel 5.0/95 wrote an older record set into a "Book" stream. */
internal fun CompoundFile.workbookStream(): ByteArray {
    if (isEncrypted) throw PasswordProtectedException()
    val reason = if (find(LEGACY_BOOK) != null) "Excel 5.0/95 workbooks are not supported" else "Not an Excel workbook"
    return stream(WORKBOOK) ?: throw UnsupportedFormatException(reason)
}

private const val WORKBOOK = "Workbook"
private const val LEGACY_BOOK = "Book"
