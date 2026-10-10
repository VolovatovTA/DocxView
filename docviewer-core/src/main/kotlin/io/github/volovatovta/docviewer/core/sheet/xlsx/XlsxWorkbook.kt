package io.github.volovatovta.docviewer.core.sheet.xlsx

import io.github.volovatovta.docviewer.core.CorruptedDocumentException
import io.github.volovatovta.docviewer.core.UnsupportedFormatException
import io.github.volovatovta.docviewer.core.model.Workbook
import io.github.volovatovta.docviewer.core.ooxml.ElementHandler
import io.github.volovatovta.docviewer.core.ooxml.OpcPackage
import io.github.volovatovta.docviewer.core.ooxml.get
import io.github.volovatovta.docviewer.core.ooxml.isOn
import io.github.volovatovta.docviewer.core.sheet.DateSystem
import io.github.volovatovta.docviewer.core.sheet.FormatContext
import io.github.volovatovta.docviewer.core.sheet.NumberFormat
import io.github.volovatovta.docviewer.core.sheet.SheetBuilder
import io.github.volovatovta.docviewer.core.sheet.SheetLimits
import org.xml.sax.Attributes

/** Reads the visible worksheets of an .xlsx package ([ECMA-376] SpreadsheetML) into a [Workbook]. */
internal class XlsxWorkbook(private val pkg: OpcPackage) {

    fun read(): Workbook {
        val workbookPath = pkg.mainPartPath() ?: DEFAULT_WORKBOOK
        val workbook = WorkbookHandler()
        if (!pkg.parse(workbookPath, workbook)) throw CorruptedDocumentException("XLSX has no workbook")
        val relationships = pkg.relationshipsOf(workbookPath)
        val strings = relationships.partOfType(SHARED_STRINGS)
            ?.let { path -> SharedStringsHandler().also { pkg.parse(path, it) }.strings }
            .orEmpty()
        val formats = relationships.partOfType(STYLES)
            ?.let { path -> StylesHandler().also { pkg.parse(path, it) }.formats }
            .orEmpty()
        val cells = CellText(strings, formats, FormatContext(workbook.dateSystem))
        // Chart sheets are listed among the sheets too, but have no cells to show.
        val sheets = workbook.visibleSheets.mapNotNull { sheet ->
            val path = relationships.internalTarget(sheet.relationshipId, WORKSHEET) ?: return@mapNotNull null
            SheetHandler(SheetBuilder(sheet.name, SheetLimits.Xlsx), cells).also { pkg.parse(path, it) }.result()
        }
        if (sheets.isEmpty()) throw UnsupportedFormatException("The workbook has no worksheets")
        return Workbook(sheets)
    }

    private companion object {
        const val DEFAULT_WORKBOOK = "xl/workbook.xml"
        const val SHARED_STRINGS = "/sharedStrings"
        const val STYLES = "/styles"
        const val WORKSHEET = "/worksheet"
    }
}

/** How a stored cell value becomes the text shown: shared strings, booleans, errors and number formats. */
private class CellText(
    private val strings: List<String>,
    private val formats: List<NumberFormat>,
    private val context: FormatContext,
) {
    fun of(type: String?, value: String, style: Int): String = when (type) {
        "s" -> value.toIntOrNull()?.let(strings::getOrNull).orEmpty()
        "b" -> if (value == "1") "TRUE" else "FALSE"
        "str", "inlineStr", "e" -> value
        else -> value.toDoubleOrNull()?.let { number -> formatOf(style).format(number, context) } ?: value
    }

    private fun formatOf(style: Int): NumberFormat = formats.getOrElse(style) { NumberFormat.General }
}

private class SheetEntry(val name: String, val relationshipId: String?, val visible: Boolean)

private class WorkbookHandler : ElementHandler() {
    private val sheets = ArrayList<SheetEntry>()
    var dateSystem = DateSystem.Excel1900
        private set

    val visibleSheets: List<SheetEntry> get() = sheets.filter { it.visible }

    override fun start(name: String, attributes: Attributes) {
        when (name) {
            "workbookPr" -> if (attributes.isOn("date1904")) dateSystem = DateSystem.Excel1904
            "sheet" -> sheets += SheetEntry(
                name = attributes["name"].orEmpty(),
                relationshipId = attributes["id"],
                visible = attributes["state"].let { it == null || it == "visible" },
            )
        }
    }
}

/** Collects the text of every string item; phonetic guides (rPh) are not part of the value. */
private class SharedStringsHandler : ElementHandler() {
    val strings = ArrayList<String>()
    private var inPhonetic = false

    override fun start(name: String, attributes: Attributes) {
        when (name) {
            "si" -> text.clear()
            "rPh" -> inPhonetic = true
            "t" -> collecting = !inPhonetic
        }
    }

    override fun end(name: String) {
        when (name) {
            "t" -> collecting = false
            "rPh" -> inPhonetic = false
            "si" -> strings += text.toString()
        }
    }
}

/** Number formats by cell style index: custom codes from numFmts, built-in ones by id. */
private class StylesHandler : ElementHandler() {
    private val customCodes = HashMap<Int, String>()
    private val styleFormats = ArrayList<Int>()
    private var inCellStyles = false

    val formats: List<NumberFormat> by lazy {
        styleFormats.map { id -> customCodes[id]?.let(NumberFormat::of) ?: NumberFormat.builtIn(id) }
    }

    override fun start(name: String, attributes: Attributes) {
        when (name) {
            "numFmt" -> {
                val id = attributes["numFmtId"]?.toIntOrNull() ?: return
                customCodes[id] = attributes["formatCode"].orEmpty()
            }
            "cellXfs" -> inCellStyles = true
            "xf" -> if (inCellStyles) styleFormats += attributes["numFmtId"]?.toIntOrNull() ?: 0
        }
    }

    override fun end(name: String) {
        if (name == "cellXfs") inCellStyles = false
    }
}

/** Streams sheetData; formulas are not evaluated, the value Excel saved with them is shown. */
private class SheetHandler(private val sheet: SheetBuilder, private val cells: CellText) : ElementHandler() {
    private var row = -1
    private var column = -1
    private var type: String? = null
    private var style = 0

    fun result() = sheet.build()

    override fun start(name: String, attributes: Attributes) {
        when (name) {
            "row" -> row = attributes["r"]?.toIntOrNull()?.minus(1) ?: (row + 1)
            "c" -> startCell(attributes)
            "v", "t" -> collecting = true
        }
    }

    override fun end(name: String) {
        when (name) {
            "v", "t" -> collecting = false
            "c" -> sheet[row, column] = cells.of(type, text.toString(), style)
        }
    }

    // A cell without a reference follows the previous one, as the format allows.
    private fun startCell(attributes: Attributes) {
        val reference = attributes["r"]?.let(CellReference::parse)
        if (reference != null) row = reference.row
        column = reference?.column ?: (column + 1)
        type = attributes["t"]
        style = attributes["s"]?.toIntOrNull() ?: 0
        text.clear()
    }
}

/** "B12" → row 11, column 1. */
internal class CellReference(val row: Int, val column: Int) {
    companion object {
        private const val LETTERS = 26
        private const val MAX_COLUMN_LETTERS = 3

        fun parse(reference: String): CellReference? {
            val letters = reference.takeWhile { it.isLetter() }
            val digits = reference.drop(letters.length).toIntOrNull()
            // Three letters reach XFD, Excel's last column; longer ones would overflow the arithmetic.
            if (digits == null || letters.length !in 1..MAX_COLUMN_LETTERS) return null
            val column = letters.uppercase().fold(0) { acc, letter -> acc * LETTERS + (letter - 'A' + 1) } - 1
            return CellReference(digits - 1, column)
        }
    }
}
