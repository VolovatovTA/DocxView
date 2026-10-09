package io.github.volovatovta.docviewer.core.docx

import io.github.volovatovta.docviewer.core.model.Block
import io.github.volovatovta.docviewer.core.model.ImageRun
import io.github.volovatovta.docviewer.core.model.Inline
import io.github.volovatovta.docviewer.core.model.PageBreak
import io.github.volovatovta.docviewer.core.model.Paragraph
import io.github.volovatovta.docviewer.core.model.ParagraphStyle
import io.github.volovatovta.docviewer.core.model.RunStyle
import io.github.volovatovta.docviewer.core.model.Table
import io.github.volovatovta.docviewer.core.model.TableCell
import io.github.volovatovta.docviewer.core.model.TableRow
import io.github.volovatovta.docviewer.core.model.TextRun
import io.github.volovatovta.docviewer.core.model.VerticalAlign
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler

internal class Relationship(val type: String, val target: String, val external: Boolean)

// Streams word/document.xml (or footnotes.xml) into model blocks.
internal class DocxBodyHandler(
    private val styles: StyleSheet,
    private val numbering: Numbering,
    private val rels: Map<String, Relationship>,
    // Resolves a footnote/endnote reference to the visible marker number.
    private val noteMarker: (kind: String, id: String) -> String? = { _, _ -> null },
) : DefaultHandler() {

    val blocks = ArrayList<Block>()

    // Filled when parsing footnotes.xml / endnotes.xml: note id -> its blocks.
    val notes = LinkedHashMap<String, List<Block>>()

    private val path = ArrayList<String>()
    private var skipDepth = 0
    private val containers = ArrayList<MutableList<Block>>().apply { add(blocks) }
    private val tables = ArrayList<TableBuilder>()
    private val paragraphs = ArrayList<ParagraphBuilder>()
    private val links = ArrayList<String?>()
    private val fields = ArrayList<FieldState>()
    private var image: ImageBuilder? = null
    private var noteId: String? = null
    private val text = StringBuilder()
    private var collecting = false

    private val paragraph get() = paragraphs.lastOrNull()

    override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes) {
        if (skipDepth > 0) {
            skipDepth++
            return
        }
        val name = localName(localName, qName)
        val parent = path.lastOrNull()
        path += name
        when (name) {
            // Deleted revisions, legacy duplicates of DrawingML and paragraph-mark formatting are not visible text.
            "Fallback", "del", "moveFrom", "sdtPr", "sdtEndPr", "sectPr", "commentRangeStart", "rPrChange", "pPrChange" -> skip()
            "rPr" -> if (parent == "pPr") skip()
            "footnote", "endnote" -> startNote(attributes)
            "p" -> paragraphs += ParagraphBuilder()
            "r" -> paragraph?.run = RunProps()
            "t" -> if (parent == "r") startText()
            "instrText" -> startText()
            "delText" -> skip()
            "tab" -> if (parent == "r") appendText("\t")
            "br" -> if (parent == "r") onBreak(attributes)
            "cr" -> if (parent == "r") appendText("\n")
            "noBreakHyphen" -> appendText("‑")
            "sym" -> onSymbol(attributes)
            "fldChar" -> onFieldChar(attributes["fldCharType"])
            "fldSimple" -> fields += FieldState(Phase.Result, link = hyperlinkFromInstruction(attributes["instr"].orEmpty()))
            "hyperlink" -> links += attributes["id"]?.let { rels[it] }?.takeIf { it.external }?.target
            "drawing", "pict", "object" -> image = ImageBuilder()
            "extent" -> image?.let { img ->
                attributes["cx"]?.toLongOrNull()?.let { img.widthPt = it / EMU_PER_PT }
                attributes["cy"]?.toLongOrNull()?.let { img.heightPt = it / EMU_PER_PT }
            }
            "docPr" -> image?.description = attributes["descr"]?.takeIf { it.isNotBlank() } ?: attributes["title"]
            "blip" -> image?.relId = attributes["embed"]
            "shape" -> image?.let { img -> parseVmlSize(attributes["style"], img) }
            "imagedata" -> image?.relId = attributes["id"]
            "txbxContent" -> containers.add(ArrayList())
            "tbl" -> tables += TableBuilder()
            "gridCol" -> tables.lastOrNull()?.gridCols?.add(attributes.int("w") ?: 0)
            "tr" -> tables.lastOrNull()?.rows?.add(RowBuilder())
            "tblHeader" -> tables.lastOrNull()?.rows?.lastOrNull()?.header = attributes.onOff()
            "tc" -> tables.lastOrNull()?.let { table ->
                val cell = CellBuilder()
                table.rows.lastOrNull()?.cells?.add(cell)
                containers.add(cell.blocks)
            }
            "gridSpan" -> currentCell()?.colSpan = attributes.int("val") ?: 1
            "vMerge" -> currentCell()?.vMerge = attributes["val"] ?: "continue"
            "shd" -> if (parent == "tcPr") currentCell()?.background = parseHexColor(attributes["fill"]) else applyProperty(name, parent, attributes)
            "footnoteReference", "endnoteReference" -> attributes["id"]?.let { id ->
                noteMarker(name.removeSuffix("Reference"), id)?.let { marker -> appendText(marker, superscript = true) }
            }
            "footnoteRef", "endnoteRef" -> noteId?.let { id -> noteMarker(path.firstOrNull { it == "footnote" || it == "endnote" } ?: "footnote", id) }
                ?.let { appendText(it, superscript = true) }
            else -> applyProperty(name, parent, attributes)
        }
    }

    override fun endElement(uri: String?, localName: String?, qName: String?) {
        if (skipDepth > 0) {
            skipDepth--
            if (skipDepth == 0) path.removeAt(path.lastIndex)
            return
        }
        val name = path.removeAt(path.lastIndex)
        when (name) {
            "t", "instrText" -> endText(instruction = name == "instrText")
            "p" -> endParagraph()
            "hyperlink" -> links.removeLastOrNull()
            "fldSimple" -> fields.removeLastOrNull()
            "drawing", "pict", "object" -> endImage()
            "txbxContent" -> {
                val inner = containers.removeAt(containers.lastIndex)
                paragraph?.trailing?.addAll(inner) ?: containers.last().addAll(inner)
            }
            "tc" -> containers.removeAt(containers.lastIndex)
            "tbl" -> tables.removeLastOrNull()?.let { containers.last().add(it.build()) }
            "footnote", "endnote" -> noteId?.let { id ->
                notes[id] = containers.removeAt(containers.lastIndex)
                noteId = null
            }
        }
    }

    override fun characters(ch: CharArray, start: Int, length: Int) {
        if (collecting && skipDepth == 0) text.appendRange(ch, start, start + length)
    }

    private fun skip() {
        skipDepth = 1
    }

    private fun applyProperty(name: String, parent: String?, attributes: Attributes) {
        val pb = paragraph ?: return
        when {
            parent == "rPr" && "r" in path -> pb.run?.let { PropsReader.applyRun(name, attributes, it) }
            (parent == "pPr" || parent == "numPr") && path.getOrNull(path.size - 3) != "style" -> PropsReader.applyParagraph(name, attributes, pb.direct)
        }
    }

    private fun startNote(attributes: Attributes) {
        val type = attributes["type"]
        if (type == "separator" || type == "continuationSeparator" || type == "continuationNotice") {
            skip()
            return
        }
        noteId = attributes["id"]
        containers.add(ArrayList())
    }

    private fun startText() {
        text.setLength(0)
        collecting = true
    }

    private fun endText(instruction: Boolean) {
        collecting = false
        val value = text.toString()
        text.setLength(0)
        if (instruction) {
            fields.lastOrNull()?.takeIf { it.phase == Phase.Instruction }?.instruction?.append(value)
        } else {
            appendText(value)
        }
    }

    private fun appendText(value: String, superscript: Boolean = false) {
        if (value.isEmpty()) return
        val pb = paragraph ?: return
        if (fields.any { it.phase == Phase.Instruction }) return
        val props = pb.run ?: RunProps()
        val effective = effectiveRun(pb, props)
        if (effective.hidden == true) return
        var style = toRunStyle(effective)
        if (superscript) style = style.copy(verticalAlign = VerticalAlign.Superscript)
        val link = fields.lastOrNull { it.link != null }?.link ?: links.lastOrNull { it != null }
        val shown = if (effective.caps == true) value.uppercase() else value
        val last = pb.inlines.lastOrNull()
        if (last is TextRun && last.style == style && last.link == link) {
            pb.inlines[pb.inlines.lastIndex] = TextRun(last.text + shown, style, link)
        } else {
            pb.inlines += TextRun(shown, style, link)
        }
    }

    private fun effectiveRun(pb: ParagraphBuilder, direct: RunProps): RunProps {
        val (_, paragraphRun) = styles.resolveParagraph(pb.direct.styleId)
        val characterStyle = direct.styleId?.let(styles::resolveRunStyle)
        val withStyle = if (characterStyle != null) paragraphRun.overlay(characterStyle) else paragraphRun
        return withStyle.overlay(direct)
    }

    private fun toRunStyle(p: RunProps): RunStyle {
        val size = p.sizeHalfPt?.let { it.toFloat() / styles.baseSizeHalfPt }?.takeIf { kotlin.math.abs(it - 1f) > 0.05f }
        return RunStyle(
            bold = p.bold == true,
            italic = p.italic == true,
            underline = p.underline == true,
            strikethrough = p.strike == true,
            color = p.color,
            background = p.highlight ?: p.shading,
            relativeSize = size,
            verticalAlign = p.vertAlign ?: VerticalAlign.Baseline,
            monospace = p.monospace == true,
            allCaps = p.caps == true,
            smallCaps = p.smallCaps == true,
        )
    }

    private fun onBreak(attributes: Attributes) {
        val pb = paragraph ?: return
        when (attributes["type"]) {
            "page" -> if (pb.inlines.isEmpty()) pb.pageBreakBefore = true else pb.pageBreakAfter = true
            "column" -> appendText("\n")
            else -> appendText("\n")
        }
    }

    private fun onSymbol(attributes: Attributes) {
        val code = attributes["char"]?.toIntOrNull(16) ?: return
        val font = attributes["font"]
        val char = if (code in 0xF000..0xF0FF) Numbering.bullet(code.toChar().toString(), font) else code.toChar().toString()
        appendText(char)
    }

    private fun onFieldChar(type: String?) {
        when (type) {
            "begin" -> fields += FieldState(Phase.Instruction)
            "separate" -> fields.lastOrNull()?.let { field ->
                field.phase = Phase.Result
                field.link = hyperlinkFromInstruction(field.instruction.toString())
            }
            "end" -> fields.removeLastOrNull()
        }
    }

    private fun hyperlinkFromInstruction(instruction: String): String? {
        val trimmed = instruction.trim()
        if (!trimmed.startsWith("HYPERLINK", ignoreCase = true)) return null
        if (Regex("\\\\l\\b").containsMatchIn(trimmed)) return null
        return Regex("\"([^\"]+)\"").find(trimmed)?.groupValues?.get(1)
            ?: trimmed.removePrefix("HYPERLINK").trim().split(' ').firstOrNull()?.takeIf { it.isNotEmpty() }
    }

    private fun endImage() {
        val img = image ?: return
        image = null
        val target = img.relId?.let { rels[it] }?.takeIf { !it.external } ?: return
        paragraph?.inlines?.add(ImageRun(target.target, img.widthPt, img.heightPt, img.description))
    }

    private fun parseVmlSize(style: String?, img: ImageBuilder) {
        style ?: return
        for (part in style.split(';')) {
            val (key, value) = part.split(':', limit = 2).takeIf { it.size == 2 }?.map { it.trim() } ?: continue
            val pt = cssToPt(value) ?: continue
            when (key) {
                "width" -> img.widthPt = pt
                "height" -> img.heightPt = pt
            }
        }
    }

    private fun cssToPt(value: String): Float? {
        val number = value.takeWhile { it.isDigit() || it == '.' || it == '-' }.toFloatOrNull() ?: return null
        return when (value.dropWhile { it.isDigit() || it == '.' || it == '-' }.trim()) {
            "pt", "" -> number
            "in" -> number * 72f
            "cm" -> number * 28.35f
            "mm" -> number * 2.835f
            "px" -> number * 0.75f
            else -> null
        }
    }

    private fun currentCell(): CellBuilder? = tables.lastOrNull()?.rows?.lastOrNull()?.cells?.lastOrNull()

    private fun endParagraph() {
        val pb = paragraphs.removeLastOrNull() ?: return
        val target = containers.last()
        val (resolved, _) = styles.resolveParagraph(pb.direct.styleId)
        val props = resolved.overlay(pb.direct)
        if (pb.pageBreakBefore || props.pageBreakBefore == true) target.addPageBreak()
        target += Paragraph(pb.inlines.toList(), buildStyle(props))
        target.addAll(pb.trailing)
        if (pb.pageBreakAfter) target.addPageBreak()
    }

    private fun MutableList<Block>.addPageBreak() {
        // Page breaks only make sense in the main flow; inside cells or at the very top they are noise.
        if (this === blocks && isNotEmpty() && last() != PageBreak) add(PageBreak)
    }

    private fun buildStyle(props: ParaProps): ParagraphStyle {
        val heading = styles.headingLevel(props.styleId, props.outlineLvl)
        val marker = props.numId?.let { numbering.next(it, props.ilvl ?: 0) }
        val left = props.indLeftTw ?: marker?.indLeftTw ?: 0
        val hanging = props.hangingTw ?: marker?.hangingTw.takeIf { props.firstLineTw == null } ?: 0
        val firstLine = props.firstLineTw ?: 0
        return ParagraphStyle(
            alignment = props.alignment ?: ParagraphStyle.Default.alignment,
            headingLevel = heading,
            indentStartPt = (if (marker != null) left - hanging else left).coerceAtLeast(0) / TWIPS_PER_PT,
            firstLineIndentPt = if (marker != null) 0f else (firstLine - hanging) / TWIPS_PER_PT,
            spaceBeforePt = props.spacingBeforeTw?.let { it / TWIPS_PER_PT },
            spaceAfterPt = props.spacingAfterTw?.let { it / TWIPS_PER_PT },
            listMarker = marker?.text?.takeIf { it.isNotEmpty() },
            listLevel = marker?.level ?: 0,
            background = props.shading,
        )
    }

    private enum class Phase { Instruction, Result }

    private class FieldState(var phase: Phase, var link: String? = null) {
        val instruction = StringBuilder()
    }

    private class ParagraphBuilder {
        val direct = ParaProps()
        var run: RunProps? = null
        val inlines = ArrayList<Inline>()
        val trailing = ArrayList<Block>()
        var pageBreakBefore = false
        var pageBreakAfter = false
    }

    private class ImageBuilder(var relId: String? = null, var widthPt: Float? = null, var heightPt: Float? = null, var description: String? = null)

    private class CellBuilder {
        val blocks = ArrayList<Block>()
        var colSpan = 1
        var vMerge: String? = null
        var background: Int? = null
    }

    private class RowBuilder {
        var header = false
        val cells = ArrayList<CellBuilder>()
    }

    private class TableBuilder {
        val gridCols = ArrayList<Int>()
        val rows = ArrayList<RowBuilder>()

        fun build(): Table {
            val built = rows.filter { it.cells.isNotEmpty() }.map { row ->
                TableRow(
                    row.cells.map { cell ->
                        val continued = cell.vMerge == "continue"
                        TableCell(if (continued) emptyList() else cell.blocks, cell.colSpan.coerceAtLeast(1), continued, cell.background)
                    },
                    row.header,
                )
            }
            val total = gridCols.sum().toFloat()
            val widths = if (total > 0 && gridCols.all { it > 0 }) gridCols.map { it / TWIPS_PER_PT } else emptyList()
            return Table(built, widths)
        }
    }

    companion object {
        const val TWIPS_PER_PT = 20f
        const val EMU_PER_PT = 12700f
    }
}
