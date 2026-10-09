package io.github.volovatovta.docviewer.core.doc

import io.github.volovatovta.docviewer.core.CorruptedDocumentException
import io.github.volovatovta.docviewer.core.PasswordProtectedException
import io.github.volovatovta.docviewer.core.PlainTextParser
import io.github.volovatovta.docviewer.core.SizeNormalizer
import io.github.volovatovta.docviewer.core.TextDecoding
import io.github.volovatovta.docviewer.core.UnsupportedFormatException
import io.github.volovatovta.docviewer.core.doc.CompoundFile.Companion.u16
import io.github.volovatovta.docviewer.core.doc.CompoundFile.Companion.u32
import io.github.volovatovta.docviewer.core.docx.Numbering
import io.github.volovatovta.docviewer.core.model.Block
import io.github.volovatovta.docviewer.core.model.FlowDocument
import io.github.volovatovta.docviewer.core.model.Inline
import io.github.volovatovta.docviewer.core.model.PageBreak
import io.github.volovatovta.docviewer.core.model.Paragraph
import io.github.volovatovta.docviewer.core.model.ParagraphStyle
import io.github.volovatovta.docviewer.core.model.RunStyle
import io.github.volovatovta.docviewer.core.model.Table
import io.github.volovatovta.docviewer.core.model.TableCell
import io.github.volovatovta.docviewer.core.model.TableRow
import io.github.volovatovta.docviewer.core.model.TextAlignment
import io.github.volovatovta.docviewer.core.model.TextRun
import io.github.volovatovta.docviewer.core.model.VerticalAlign
import java.io.File

// Word 97-2003 binary format ([MS-DOC]): text via the piece table, formatting via CHPX/PAPX FKPs.
public object DocParser {

    private const val MAX_FILE_SIZE = 256L * 1024 * 1024

    public fun parse(file: File): FlowDocument {
        if (file.length() > MAX_FILE_SIZE) throw UnsupportedFormatException("DOC file is too large")
        val cfb = CompoundFile(file.readBytes())
        if (cfb.find("EncryptedPackage") != null || cfb.find("EncryptionInfo") != null) throw PasswordProtectedException()
        val word = cfb.stream("WordDocument") ?: throw UnsupportedFormatException("OLE file is not a Word document")
        try {
            return parseWord(cfb, word, file.nameWithoutExtension)
        } catch (e: IndexOutOfBoundsException) {
            throw CorruptedDocumentException("Damaged DOC structure", e)
        }
    }

    private fun parseWord(cfb: CompoundFile, word: ByteArray, name: String): FlowDocument {
        if (word.size < 0x20 || u16(word, 0) != 0xA5EC) throw CorruptedDocumentException("Bad Word FIB")
        val nFib = u16(word, 2)
        val flags = u16(word, 0x0A)
        if (flags and 0x0100 != 0) throw PasswordProtectedException()
        if (nFib < 0xC1) return parseLegacy(word, name)

        val fib = Fib(word)
        val table = cfb.stream(if (flags and 0x0200 != 0) "1Table" else "0Table") ?: throw CorruptedDocumentException("Missing table stream")
        val pieces = Pieces.parse(table, fib.fcLcb(33), word)
        val chars = pieces.decode(word, fib.ccpText)
        val chp = PropertyRuns.parseChpx(word, table, fib.fcLcb(12))
        val pap = PropertyRuns.parsePapx(word, table, fib.fcLcb(13))
        val lists = runCatching { ListTables.parse(table, fib.fcLcb(73), fib.fcLcb(74)) }.getOrNull()
        val baseSize = chp.dominantSize(chars.fc)
        return FlowDocument(SizeNormalizer.normalize(DocBuilder(chars, chp, pap, lists, baseSize).build()), name)
    }

    // Word 6/95: no piece table in the 97 sense, but the text is stored contiguously between fcMin and fcMac.
    private fun parseLegacy(word: ByteArray, name: String): FlowDocument {
        val fcMin = u32(word, 0x18)
        val fcMac = u32(word, 0x1C)
        if (fcMin <= 0 || fcMac <= fcMin || fcMac > word.size) throw UnsupportedFormatException("Unsupported Word 6/95 document")
        val bytes = word.copyOfRange(fcMin, fcMac)
        val charset = TextDecoding.guessSingleByteCharset(bytes, bytes.size)
        val text = String(bytes, charset).map { c ->
            when (c) {
                '\r', '\u0007', '\u000B', '\u000C' -> '\n'
                '\t' -> c
                else -> if (c < ' ') '\u0000' else c
            }
        }.filter { it != '\u0000' }.joinToString("")
        return PlainTextParser.parse(text, title = name)
    }

    private class Fib(private val word: ByteArray) {
        private val rgLw: Int
        private val rgFcLcb: Int
        private val cbRgFcLcb: Int

        init {
            val csw = u16(word, 32)
            rgLw = 34 + csw * 2 + 2
            val cslw = u16(word, 34 + csw * 2)
            val cbOffset = rgLw + cslw * 4
            cbRgFcLcb = u16(word, cbOffset)
            rgFcLcb = cbOffset + 2
        }

        val ccpText: Int get() = u32(word, rgLw + 3 * 4)

        fun fcLcb(index: Int): Pair<Int, Int> {
            if (index >= cbRgFcLcb) return 0 to 0
            val offset = rgFcLcb + index * 8
            return u32(word, offset) to u32(word, offset + 4)
        }
    }
}

internal class DecodedText(val text: CharArray, val fc: IntArray)

internal class Pieces(private val cps: IntArray, private val fcs: IntArray, private val compressed: BooleanArray) {

    fun decode(word: ByteArray, ccpText: Int): DecodedText {
        val total = minOf(ccpText.coerceAtLeast(0), cps.lastOrNull() ?: 0)
        val text = CharArray(total)
        val fc = IntArray(total)
        val cp1252 = charset("windows-1252")
        for (i in 0 until cps.size - 1) {
            val start = cps[i]
            val end = minOf(cps[i + 1], total)
            for (cp in start until end) {
                val offset = if (compressed[i]) fcs[i] + (cp - start) else fcs[i] + (cp - start) * 2
                fc[cp] = offset
                text[cp] = when {
                    compressed[i] -> if (offset < word.size) String(byteArrayOf(word[offset]), cp1252)[0] else ' '
                    offset + 1 < word.size -> u16(word, offset).toChar()
                    else -> ' '
                }
            }
        }
        return DecodedText(text, fc)
    }

    companion object {
        fun parse(table: ByteArray, clx: Pair<Int, Int>, word: ByteArray): Pieces {
            val (fcClx, lcbClx) = clx
            if (lcbClx <= 0 || fcClx < 0 || fcClx + lcbClx > table.size) throw CorruptedDocumentException("Missing piece table")
            var pos = fcClx
            val end = fcClx + lcbClx
            // Skip Prc entries (clxt = 1) until the Pcdt (clxt = 2).
            while (pos < end && table[pos].toInt() == 1) pos += 3 + u16(table, pos + 1)
            if (pos >= end || table[pos].toInt() != 2) throw CorruptedDocumentException("Bad piece table")
            val lcb = u32(table, pos + 1)
            val plc = pos + 5
            val n = (lcb - 4) / 12
            if (n <= 0 || plc + lcb > table.size) throw CorruptedDocumentException("Bad piece table size")
            val cps = IntArray(n + 1) { u32(table, plc + it * 4) }
            val fcs = IntArray(n)
            val compressed = BooleanArray(n)
            for (i in 0 until n) {
                val raw = u32(table, plc + (n + 1) * 4 + i * 8 + 2)
                compressed[i] = raw and 0x40000000 != 0
                fcs[i] = if (compressed[i]) (raw and 0x3FFFFFFF) / 2 else raw and 0x3FFFFFFF
            }
            if (word.isEmpty()) throw CorruptedDocumentException("Empty WordDocument stream")
            return Pieces(cps, fcs, compressed)
        }
    }
}

internal class CharProps(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strike: Boolean = false,
    val hidden: Boolean = false,
    val color: Int? = null,
    val highlight: Int? = null,
    val sizeHalfPt: Int? = null,
    val verticalAlign: VerticalAlign = VerticalAlign.Baseline,
    val caps: Boolean = false,
    val smallCaps: Boolean = false,
    val special: Boolean = false,
)

internal class ParaProps(
    val istd: Int = 0,
    val inTable: Boolean = false,
    val rowEnd: Boolean = false,
    val alignment: TextAlignment = TextAlignment.Start,
    val ilfo: Int = 0,
    val ilvl: Int = 0,
    val outlineLevel: Int? = null,
    val indentLeft: Int = 0,
    val indentFirst: Int = 0,
    val spaceBefore: Int? = null,
    val spaceAfter: Int? = null,
)

internal class PropertyRuns<T>(private val starts: IntArray, private val ends: IntArray, private val props: List<T>, private val default: T) {
    private var last = 0

    fun at(fc: Int): T {
        if (starts.isEmpty()) return default
        if (last < starts.size && fc >= starts[last] && fc < ends[last]) return props[last]
        var lo = 0
        var hi = starts.size - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            when {
                fc < starts[mid] -> hi = mid - 1
                fc >= ends[mid] -> lo = mid + 1
                else -> {
                    last = mid
                    return props[mid]
                }
            }
        }
        return default
    }

    companion object {
        private val ICO = intArrayOf(
            0, 0xFF000000.toInt(), 0xFF0000FF.toInt(), 0xFF00FFFF.toInt(), 0xFF00FF00.toInt(), 0xFFFF00FF.toInt(), 0xFFFF0000.toInt(),
            0xFFFFFF00.toInt(), 0xFFFFFFFF.toInt(), 0xFF000080.toInt(), 0xFF008080.toInt(), 0xFF008000.toInt(), 0xFF800080.toInt(),
            0xFF800000.toInt(), 0xFF808000.toInt(), 0xFF808080.toInt(), 0xFFC0C0C0.toInt(),
        )

        private fun ico(index: Int): Int? = if (index in 1 until ICO.size) ICO[index] else null

        private fun toggle(value: Int) = value == 1 || value == 0x81

        fun parseChpx(word: ByteArray, table: ByteArray, plc: Pair<Int, Int>): PropertyRuns<CharProps> {
            val starts = ArrayList<Int>()
            val ends = ArrayList<Int>()
            val props = ArrayList<CharProps>()
            forEachFkp(word, table, plc) { page ->
                val crun = word[page + 511].toInt() and 0xFF
                for (i in 0 until crun) {
                    val start = u32(word, page + i * 4)
                    val end = u32(word, page + (i + 1) * 4)
                    val offset = (word[page + (crun + 1) * 4 + i].toInt() and 0xFF) * 2
                    val cp = if (offset == 0) CharProps() else {
                        val cb = word[page + offset].toInt() and 0xFF
                        readChp(word, page + offset + 1, page + offset + 1 + cb)
                    }
                    starts += start
                    ends += end
                    props += cp
                }
            }
            return sorted(starts, ends, props, CharProps())
        }

        fun parsePapx(word: ByteArray, table: ByteArray, plc: Pair<Int, Int>): PropertyRuns<ParaProps> {
            val starts = ArrayList<Int>()
            val ends = ArrayList<Int>()
            val props = ArrayList<ParaProps>()
            forEachFkp(word, table, plc) { page ->
                val crun = word[page + 511].toInt() and 0xFF
                for (i in 0 until crun) {
                    val start = u32(word, page + i * 4)
                    val end = u32(word, page + (i + 1) * 4)
                    val bx = page + (crun + 1) * 4 + i * 13
                    val offset = (word[bx].toInt() and 0xFF) * 2
                    val pp = if (offset == 0) ParaProps() else {
                        var cb = word[page + offset].toInt() and 0xFF
                        var data = page + offset + 1
                        val size = if (cb == 0) {
                            cb = word[data].toInt() and 0xFF
                            data++
                            cb * 2
                        } else {
                            cb * 2 - 1
                        }
                        readPap(word, data, minOf(data + size, page + 511))
                    }
                    starts += start
                    ends += end
                    props += pp
                }
            }
            return sorted(starts, ends, props, ParaProps())
        }

        private inline fun forEachFkp(word: ByteArray, table: ByteArray, plc: Pair<Int, Int>, action: (pageOffset: Int) -> Unit) {
            val (fc, lcb) = plc
            if (lcb < 12 || fc < 0 || fc + lcb > table.size) return
            val n = (lcb - 4) / 8
            for (i in 0 until n) {
                val pn = u32(table, fc + (n + 1) * 4 + i * 4) and 0x3FFFFF
                val page = pn * 512
                if (page + 512 <= word.size) action(page)
            }
        }

        private fun <T> sorted(starts: List<Int>, ends: List<Int>, props: List<T>, default: T): PropertyRuns<T> {
            val order = starts.indices.sortedBy { starts[it] }
            return PropertyRuns(IntArray(order.size) { starts[order[it]] }, IntArray(order.size) { ends[order[it]] }, order.map { props[it] }, default)
        }

        private fun readChp(b: ByteArray, from: Int, to: Int): CharProps {
            var bold = false; var italic = false; var underline = false; var strike = false; var hidden = false
            var color: Int? = null; var highlight: Int? = null; var size: Int? = null; var vAlign = VerticalAlign.Baseline
            var caps = false; var smallCaps = false; var special = false
            Sprms.forEach(b, from, to) { sprm, op ->
                when (sprm) {
                    0x0835 -> bold = toggle(b[op].toInt() and 0xFF)
                    0x0836 -> italic = toggle(b[op].toInt() and 0xFF)
                    0x0837, 0x2A53 -> strike = toggle(b[op].toInt() and 0xFF)
                    0x083C -> hidden = toggle(b[op].toInt() and 0xFF)
                    0x083A -> smallCaps = toggle(b[op].toInt() and 0xFF)
                    0x083B -> caps = toggle(b[op].toInt() and 0xFF)
                    0x0855 -> special = b[op].toInt() != 0
                    0x2A3E -> underline = b[op].toInt() != 0
                    0x4A43 -> size = u16(b, op)
                    0x2A42 -> color = ico(b[op].toInt() and 0xFF)
                    0x6870 -> {
                        val cv = u32(b, op)
                        // COLORREF is 0x00BBGGRR; 0xFF000000 marks "auto".
                        color = if (cv ushr 24 == 0xFF) null else 0xFF000000.toInt() or ((cv and 0xFF) shl 16) or (cv and 0xFF00) or ((cv shr 16) and 0xFF)
                    }
                    0x2A0C -> highlight = ico(b[op].toInt() and 0xFF)
                    0x2A48 -> vAlign = when (b[op].toInt()) {
                        1 -> VerticalAlign.Superscript
                        2 -> VerticalAlign.Subscript
                        else -> VerticalAlign.Baseline
                    }
                }
            }
            return CharProps(bold, italic, underline, strike, hidden, color, highlight, size, vAlign, caps, smallCaps, special)
        }

        private fun readPap(b: ByteArray, from: Int, to: Int): ParaProps {
            if (to - from < 2) return ParaProps()
            val istd = u16(b, from)
            var inTable = false; var ttp = false; var itap = 0; var jc = TextAlignment.Start
            var ilfo = 0; var ilvl = 0; var outline: Int? = null; var left = 0; var first = 0
            var before: Int? = null; var after: Int? = null
            Sprms.forEach(b, from + 2, to) { sprm, op ->
                when (sprm) {
                    0x2416 -> inTable = b[op].toInt() != 0
                    0x2417 -> ttp = b[op].toInt() != 0
                    0x6649 -> itap = u32(b, op)
                    0x2403, 0x2461 -> jc = when (b[op].toInt()) {
                        1 -> TextAlignment.Center
                        2 -> TextAlignment.End
                        3, 4, 5 -> TextAlignment.Justify
                        else -> TextAlignment.Start
                    }
                    0x460B -> ilfo = u16(b, op).toShort().toInt()
                    0x260A -> ilvl = b[op].toInt() and 0xFF
                    0x2640 -> outline = (b[op].toInt() and 0xFF).takeIf { it < 9 }
                    0x840F, 0x845E -> left = u16(b, op).toShort().toInt()
                    0x8411, 0x8460 -> first = u16(b, op).toShort().toInt()
                    0xA413 -> before = u16(b, op)
                    0xA414 -> after = u16(b, op)
                }
            }
            return ParaProps(istd, inTable || itap > 0, ttp, jc, ilfo, ilvl, outline, left, first, before, after)
        }
    }
}

internal fun PropertyRuns<CharProps>.dominantSize(fcs: IntArray): Int {
    val counts = HashMap<Int, Int>()
    for (fc in fcs) at(fc).sizeHalfPt?.let { counts[it] = (counts[it] ?: 0) + 1 }
    return counts.maxByOrNull { it.value }?.key ?: 20
}

internal object Sprms {
    // Calls action(sprm, operandOffset) for each property modifier in [from, to).
    inline fun forEach(b: ByteArray, from: Int, to: Int, action: (Int, Int) -> Unit) {
        var i = from
        while (i + 2 <= to) {
            val sprm = u16(b, i)
            var op = i + 2
            val length = when (sprm ushr 13) {
                0, 1 -> 1
                2, 4, 5 -> 2
                3 -> 4
                7 -> 3
                else -> {
                    if (op >= to) return
                    if (sprm == 0xD608 || sprm == 0xD606) {
                        val cb = u16(b, op)
                        op += 2
                        cb - 1
                    } else {
                        val cb = b[op].toInt() and 0xFF
                        if (cb == 255 && sprm == 0xC615) return
                        op += 1
                        cb
                    }
                }
            }
            if (length < 0 || op + length > to) return
            action(sprm, op)
            i = op + length
        }
    }
}

internal class ListTables(private val lists: Map<Int, Array<ListLevel>>, private val lfoToLsid: IntArray) {
    class ListLevel(val start: Int, val nfc: Int, val text: String)

    private val counters = HashMap<Int, IntArray>()

    fun next(ilfo: Int, ilvl: Int): String? {
        if (ilfo <= 0 || ilfo > lfoToLsid.size) return null
        val lsid = lfoToLsid[ilfo - 1]
        val levels = lists[lsid] ?: return null
        val level = ilvl.coerceIn(0, levels.size - 1)
        val counter = counters.getOrPut(lsid) { IntArray(9) { levels.getOrNull(it)?.start?.minus(1) ?: 0 } }
        counter[level]++
        for (deeper in level + 1 until 9) counter[deeper] = (levels.getOrNull(deeper)?.start ?: 1) - 1
        val def = levels[level]
        if (def.nfc == 23) return Numbering.bullet(def.text, "symbol")
        if (def.nfc == 255) return null
        return buildString {
            for (c in def.text) {
                if (c.code < 9) {
                    val lvl = c.code
                    append(Numbering.format(counter[lvl].coerceAtLeast(1), nfcName(levels.getOrNull(lvl)?.nfc ?: 0)))
                } else {
                    append(c)
                }
            }
        }.ifEmpty { null }
    }

    private fun nfcName(nfc: Int) = when (nfc) {
        1 -> "upperRoman"
        2 -> "lowerRoman"
        3 -> "upperLetter"
        4 -> "lowerLetter"
        22 -> "decimalZero"
        else -> "decimal"
    }

    companion object {
        fun parse(table: ByteArray, lst: Pair<Int, Int>, lfo: Pair<Int, Int>): ListTables? {
            val (fcLst, lcbLst) = lst
            val (fcLfo, lcbLfo) = lfo
            if (lcbLst <= 0 || lcbLfo <= 0) return null
            val count = u16(table, fcLst)
            var pos = fcLst + 2
            val ids = IntArray(count)
            val simple = BooleanArray(count)
            for (i in 0 until count) {
                ids[i] = u32(table, pos)
                simple[i] = table[pos + 26].toInt() and 1 != 0
                pos += 28
            }
            val lists = HashMap<Int, Array<ListLevel>>()
            for (i in 0 until count) {
                val levelCount = if (simple[i]) 1 else 9
                lists[ids[i]] = Array(levelCount) {
                    val start = u32(table, pos)
                    val nfc = table[pos + 4].toInt() and 0xFF
                    val cbChpx = table[pos + 24].toInt() and 0xFF
                    val cbPapx = table[pos + 25].toInt() and 0xFF
                    pos += 28 + cbPapx + cbChpx
                    val cch = u16(table, pos)
                    val text = String(CharArray(cch) { u16(table, pos + 2 + it * 2).toChar() })
                    pos += 2 + cch * 2
                    ListLevel(start, nfc, text)
                }
            }
            val lfoCount = u32(table, fcLfo)
            val lfoToLsid = IntArray(lfoCount.coerceIn(0, 10_000)) { u32(table, fcLfo + 4 + it * 16) }
            return ListTables(lists, lfoToLsid)
        }
    }
}

private class DocBuilder(
    private val chars: DecodedText,
    private val chp: PropertyRuns<CharProps>,
    private val pap: PropertyRuns<ParaProps>,
    private val lists: ListTables?,
    private val baseSize: Int,
) {
    private val blocks = ArrayList<Block>()
    private var inlines = ArrayList<Inline>()
    private val fields = ArrayList<Field>()
    private var rows = ArrayList<TableRow>()
    private var cells = ArrayList<TableCell>()
    private var cellBlocks = ArrayList<Block>()

    private class Field(var instruction: Boolean = true, val code: StringBuilder = StringBuilder(), var link: String? = null)

    fun build(): List<Block> {
        val text = chars.text
        for (cp in text.indices) {
            val c = text[cp]
            val fc = chars.fc[cp]
            when (c.code) {
                0x13 -> fields += Field()
                0x14 -> fields.lastOrNull()?.let { it.instruction = false; it.link = hyperlink(it.code.toString()) }
                0x15 -> fields.removeLastOrNull()
                else -> if (fields.any { it.instruction }) {
                    fields.last { it.instruction }.code.append(c)
                } else when (c) {
                    '\r', '\u0007' -> endParagraph(fc, c)
                    '\u000C' -> {
                        endParagraph(fc, c)
                        if (cells.isEmpty() && cellBlocks.isEmpty() && rows.isEmpty() && blocks.isNotEmpty() && blocks.last() != PageBreak) blocks += PageBreak
                    }
                    '\u000B', '\u000E' -> append('\n', fc)
                    '\u001E' -> append('‑', fc)
                    '\t', ' ' -> append(c, fc)
                    else -> if (c >= ' ') append(c, fc)
                }
            }
        }
        if (inlines.isNotEmpty() || runText.isNotEmpty()) endParagraph(chars.fc.lastOrNull() ?: 0, '\r')
        flushTable()
        return blocks
    }

    private val runText = StringBuilder()
    private var runStyle: RunStyle? = null
    private var runLink: String? = null
    private var lastProps: CharProps? = null
    private var lastStyle: RunStyle = RunStyle.Default

    private fun append(c: Char, fc: Int) {
        val props = chp.at(fc)
        if (props.hidden || props.special) return
        val style = styleOf(props)
        val link = fields.lastOrNull { it.link != null }?.link
        if (style != runStyle || link != runLink) flushRun()
        runStyle = style
        runLink = link
        runText.append(if (props.caps) c.uppercaseChar() else c)
    }

    private fun styleOf(props: CharProps): RunStyle {
        if (props === lastProps) return lastStyle
        lastProps = props
        lastStyle = RunStyle(
            bold = props.bold,
            italic = props.italic,
            underline = props.underline,
            strikethrough = props.strike,
            color = props.color,
            background = props.highlight,
            relativeSize = props.sizeHalfPt?.let { it.toFloat() / baseSize }?.takeIf { kotlin.math.abs(it - 1f) > 0.05f },
            verticalAlign = props.verticalAlign,
            allCaps = props.caps,
            smallCaps = props.smallCaps,
        )
        return lastStyle
    }

    private fun flushRun() {
        val style = runStyle
        if (runText.isNotEmpty() && style != null) inlines += TextRun(runText.toString(), style, runLink)
        runText.setLength(0)
    }

    private fun endParagraph(markFc: Int, mark: Char) {
        flushRun()
        val props = pap.at(markFc)
        val paragraph = Paragraph(inlines, styleOf(props))
        inlines = ArrayList()
        if (props.inTable) {
            if (mark == '\u0007' && props.rowEnd) {
                if (cellBlocks.isNotEmpty()) closeCell()
                if (cells.isNotEmpty()) rows += TableRow(cells)
                cells = ArrayList()
            } else {
                cellBlocks += paragraph
                if (mark == '\u0007') closeCell()
            }
        } else {
            flushTable()
            blocks += paragraph
        }
    }

    private fun closeCell() {
        cells += TableCell(cellBlocks)
        cellBlocks = ArrayList()
    }

    private fun flushTable() {
        if (cellBlocks.isNotEmpty()) closeCell()
        if (cells.isNotEmpty()) rows += TableRow(cells)
        if (rows.isNotEmpty()) blocks += Table(rows)
        rows = ArrayList()
        cells = ArrayList()
    }

    private fun styleOf(p: ParaProps): ParagraphStyle {
        val heading = when {
            p.istd in 1..9 -> minOf(p.istd, 6)
            p.outlineLevel != null -> minOf(p.outlineLevel + 1, 6)
            else -> 0
        }
        val marker = if (p.ilfo > 0) lists?.next(p.ilfo, p.ilvl) ?: "•" else null
        return ParagraphStyle(
            alignment = p.alignment,
            headingLevel = heading,
            indentStartPt = (p.indentLeft + if (marker != null) minOf(p.indentFirst, 0) else 0).coerceAtLeast(0) / 20f,
            firstLineIndentPt = if (marker != null) 0f else p.indentFirst / 20f,
            spaceBeforePt = p.spaceBefore?.let { it / 20f },
            spaceAfterPt = p.spaceAfter?.let { it / 20f },
            listMarker = marker,
            listLevel = p.ilvl,
        )
    }

    private fun hyperlink(code: String): String? {
        val trimmed = code.trim()
        if (!trimmed.startsWith("HYPERLINK", ignoreCase = true) || Regex("\\\\l\\b").containsMatchIn(trimmed)) return null
        return Regex("\"([^\"]+)\"").find(trimmed)?.groupValues?.get(1)
    }
}
