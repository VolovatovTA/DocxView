package io.github.volovatovta.docviewer.core.docx

import io.github.volovatovta.docviewer.core.model.Block
import io.github.volovatovta.docviewer.core.model.PageBreak
import io.github.volovatovta.docviewer.core.model.Paragraph
import io.github.volovatovta.docviewer.core.model.RunStyle
import io.github.volovatovta.docviewer.core.model.VerticalAlign
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler

internal class XmlElement(val name: String, val parent: String?, val attributes: Attributes)

private typealias OpenHandler = (XmlElement) -> Unit
private typealias CloseHandler = () -> Unit

// Streams word/document.xml (or footnotes.xml / endnotes.xml) into model blocks.
internal class DocxBodyHandler(
    styles: StyleSheet,
    numbering: Numbering,
    private val relationships: Relationships,
    // Resolves a footnote/endnote reference to the visible marker number.
    private val noteMarker: (kind: String, id: String) -> String,
) : DefaultHandler() {

    val blocks = ArrayList<Block>()

    // Filled when parsing footnotes.xml / endnotes.xml: note id -> its blocks.
    val notes = LinkedHashMap<String, List<Block>>()

    private class NoteContext(val kind: String, val id: String?)

    private val runFormatter = RunFormatter(styles)
    private val paragraphFormatter = ParagraphFormatter(styles, numbering)
    private val path = ArrayList<String>()
    private var skipDepth = 0
    private val containers = ArrayList<MutableList<Block>>().apply { add(blocks) }
    private val tables = ArrayList<TableBuilder>()
    private val paragraphs = ArrayList<ParagraphBuilder>()
    private val links = ArrayList<String?>()
    private val fields = FieldTracker()
    private var image: ImageBuilder? = null
    private var note: NoteContext? = null
    private val text = StringBuilder()
    private var collectingText = false

    private val paragraph: ParagraphBuilder? get() = paragraphs.lastOrNull()
    private val table: TableBuilder? get() = tables.lastOrNull()

    private val openHandlers: Map<String, OpenHandler> =
        skippedElements.associateWith { { _: XmlElement -> skipCurrentElement() } } +
            textHandlers() + fieldHandlers() + tableHandlers() + drawingHandlers() + noteHandlers()

    private val closeHandlers: Map<String, CloseHandler> = mapOf(
        onClose("t") { finishText(isInstruction = false) },
        onClose("instrText") { finishText(isInstruction = true) },
        onClose("p") { finishParagraph() },
        onClose("hyperlink") { links.removeLastOrNull() },
        onClose("fldSimple") { fields.end() },
        onClose("drawing") { finishImage() },
        onClose("pict") { finishImage() },
        onClose("object") { finishImage() },
        onClose("txbxContent") { finishTextBox() },
        onClose("tc") { containers.removeAt(containers.lastIndex) },
        onClose("tbl") { finishTable() },
        onClose("footnote") { finishNote() },
        onClose("endnote") { finishNote() },
    )

    override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes) {
        if (skipDepth > 0) {
            skipDepth++
            return
        }
        val element = XmlElement(localName(localName, qName), path.lastOrNull(), attributes)
        path += element.name
        val handler = openHandlers[element.name]
        if (handler != null) handler(element) else applyProperty(element)
    }

    override fun endElement(uri: String?, localName: String?, qName: String?) {
        if (skipDepth > 0) {
            skipDepth--
            if (skipDepth == 0) path.removeAt(path.lastIndex)
            return
        }
        closeHandlers[path.removeAt(path.lastIndex)]?.invoke()
    }

    override fun characters(ch: CharArray, start: Int, length: Int) {
        if (collectingText && skipDepth == 0) text.appendRange(ch, start, start + length)
    }

    private fun textHandlers() = mapOf(
        onOpen("p") { paragraphs += ParagraphBuilder() },
        onOpen("r") { paragraph?.run = RunProperties() },
        // Paragraph-mark formatting describes the pilcrow, not the text.
        onOpen("rPr") { if (it.parent == "pPr") skipCurrentElement() },
        onOpen("t") { if (it.parent == "r") startCollectingText() },
        onOpen("instrText") { startCollectingText() },
        onOpen("tab") { if (it.parent == "r") appendText("\t") },
        onOpen("br") { if (it.parent == "r") onBreak(it.attributes) },
        onOpen("cr") { if (it.parent == "r") appendText("\n") },
        onOpen("noBreakHyphen") { appendText(NON_BREAKING_HYPHEN) },
        onOpen("sym") { onSymbol(it.attributes) },
    )

    private fun fieldHandlers() = mapOf(
        onOpen("fldChar") { fields.onFieldCharacter(it.attributes["fldCharType"]) },
        onOpen("fldSimple") { fields.beginSimple(it.attributes["instr"].orEmpty()) },
        onOpen("hyperlink") { links += relationships.externalTarget(it.attributes["id"]) },
    )

    private fun tableHandlers() = mapOf(
        onOpen("tbl") { tables += TableBuilder() },
        onOpen("gridCol") { table?.addGridColumn(it.attributes.int("w") ?: 0) },
        onOpen("tr") { table?.startRow() },
        onOpen("tblHeader") { table?.markHeaderRow(it.attributes.onOff()) },
        // Every w:tc gets a container, even a stray one, so that the closing tag always has something to pop.
        onOpen("tc") { containers.add(table?.startCell()?.blocks ?: ArrayList()) },
        onOpen("gridSpan") { table?.currentCell?.colSpan = it.attributes.int("val") ?: 1 },
        onOpen("vMerge") { table?.currentCell?.verticalMerge = it.attributes["val"] ?: CellBuilder.CONTINUE },
        onOpen("shd") { onShading(it) },
    )

    private fun drawingHandlers() = mapOf(
        onOpen("drawing") { image = ImageBuilder() },
        onOpen("pict") { image = ImageBuilder() },
        onOpen("object") { image = ImageBuilder() },
        onOpen("extent") { image?.readExtent(it.attributes) },
        onOpen("docPr") { image?.readDescription(it.attributes) },
        onOpen("blip") { image?.relationshipId = it.attributes["embed"] },
        onOpen("imagedata") { image?.relationshipId = it.attributes["id"] },
        onOpen("shape") { image?.readVmlStyle(it.attributes["style"]) },
        onOpen("txbxContent") { containers.add(ArrayList()) },
    )

    private fun noteHandlers() = mapOf(
        onOpen("footnote") { startNote(it) },
        onOpen("endnote") { startNote(it) },
        onOpen("footnoteReference") { onNoteReference(it) },
        onOpen("endnoteReference") { onNoteReference(it) },
        onOpen("footnoteRef") { note?.let(::appendOwnNoteNumber) },
        onOpen("endnoteRef") { note?.let(::appendOwnNoteNumber) },
    )

    private fun onOpen(name: String, handler: OpenHandler) = name to handler

    private fun onClose(name: String, handler: CloseHandler) = name to handler

    private fun skipCurrentElement() {
        skipDepth = 1
    }

    private fun applyProperty(element: XmlElement) {
        val paragraph = paragraph ?: return
        val run = paragraph.run
        when {
            element.parent == "rPr" && "r" in path && run != null -> {
                PropertyReader.applyRun(element.name, element.attributes, run)
            }
            element.parent == "pPr" || element.parent == "numPr" -> {
                PropertyReader.applyParagraph(element.name, element.attributes, paragraph.direct)
            }
        }
    }

    private fun onShading(element: XmlElement) {
        if (element.parent == "tcPr") {
            table?.currentCell?.background = parseHexColor(element.attributes["fill"])
        } else {
            applyProperty(element)
        }
    }

    private fun startCollectingText() {
        text.setLength(0)
        collectingText = true
    }

    private fun finishText(isInstruction: Boolean) {
        collectingText = false
        val value = text.toString()
        text.setLength(0)
        if (isInstruction) fields.appendInstruction(value) else appendText(value)
    }

    private fun appendText(value: String, superscript: Boolean = false) {
        val paragraph = paragraph ?: return
        if (value.isEmpty() || fields.isInsideInstruction) return
        val properties = runFormatter.effectiveProperties(paragraph.direct.styleId, paragraph.run ?: RunProperties())
        if (properties.hidden == true) return
        val style = runFormatter.style(properties).raisedIf(superscript)
        val shown = if (properties.caps == true) value.uppercase() else value
        paragraph.appendText(shown, style, fields.link ?: links.lastOrNull { it != null })
    }

    private fun RunStyle.raisedIf(superscript: Boolean) =
        if (superscript) copy(verticalAlign = VerticalAlign.Superscript) else this

    private fun onBreak(attributes: Attributes) {
        if (attributes["type"] == "page") paragraph?.breakPage() else appendText("\n")
    }

    private fun onSymbol(attributes: Attributes) {
        val code = attributes["char"]?.toIntOrNull(HEX_RADIX) ?: return
        val symbol = code.toChar().toString()
        appendText(if (code in privateUseSymbols) Numbering.bullet(symbol, attributes["font"]) else symbol)
    }

    private fun startNote(element: XmlElement) {
        if (element.attributes["type"] in separatorNoteTypes) {
            skipCurrentElement()
            return
        }
        note = NoteContext(element.name, element.attributes["id"])
        containers.add(ArrayList())
    }

    private fun finishNote() {
        val finished = containers.removeAt(containers.lastIndex)
        note?.id?.let { notes[it] = finished }
        note = null
    }

    private fun onNoteReference(element: XmlElement) {
        val id = element.attributes["id"] ?: return
        appendText(noteMarker(element.name.removeSuffix("Reference"), id), superscript = true)
    }

    private fun appendOwnNoteNumber(context: NoteContext) {
        context.id?.let { appendText(noteMarker(context.kind, it), superscript = true) }
    }

    private fun finishImage() {
        val built = image?.build(relationships)
        image = null
        built?.let { paragraph?.inlines?.add(it) }
    }

    private fun finishTextBox() {
        val inner = containers.removeAt(containers.lastIndex)
        paragraph?.trailing?.addAll(inner) ?: containers.last().addAll(inner)
    }

    private fun finishTable() {
        tables.removeLastOrNull()?.let { containers.last().add(it.build()) }
    }

    private fun finishParagraph() {
        val builder = paragraphs.removeLastOrNull() ?: return
        val target = containers.last()
        val properties = paragraphFormatter.properties(builder.direct)
        if (builder.pageBreakBefore || properties.pageBreakBefore == true) addPageBreak(target)
        target += Paragraph(builder.inlines.toList(), paragraphFormatter.style(properties))
        target.addAll(builder.trailing)
        if (builder.pageBreakAfter) addPageBreak(target)
    }

    // Page breaks only make sense in the main flow; inside cells or at the very top they are noise.
    private fun addPageBreak(target: MutableList<Block>) {
        if (target === blocks && blocks.isNotEmpty() && blocks.last() != PageBreak) blocks += PageBreak
    }

    private companion object {
        const val NON_BREAKING_HYPHEN = "\u2011"
        const val HEX_RADIX = 16
        val privateUseSymbols = 0xF000..0xF0FF
        val separatorNoteTypes = setOf("separator", "continuationSeparator", "continuationNotice")

        // Deleted revisions, legacy duplicates of DrawingML and section/property metadata are not visible text.
        val skippedElements = setOf(
            "Fallback",
            "del",
            "delText",
            "moveFrom",
            "sdtPr",
            "sdtEndPr",
            "sectPr",
            "commentRangeStart",
            "rPrChange",
            "pPrChange",
        )
    }
}
