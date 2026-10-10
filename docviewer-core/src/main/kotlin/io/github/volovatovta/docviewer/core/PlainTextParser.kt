package io.github.volovatovta.docviewer.core

import io.github.volovatovta.docviewer.core.model.FlowDocument
import io.github.volovatovta.docviewer.core.model.Paragraph
import io.github.volovatovta.docviewer.core.model.ParagraphStyle
import io.github.volovatovta.docviewer.core.model.RunStyle
import io.github.volovatovta.docviewer.core.model.TextRun
import java.io.File

public object PlainTextParser {

    private val codeExtensions = setOf("json", "xml", "log", "yaml", "yml", "ini", "conf")
    private const val PROSE_PARAGRAPH_SPACING_PT = 4f

    public fun parse(file: File): FlowDocument {
        val decoded = TextDecoding.decode(file.readBytes())
        return parse(decoded.text, monospace = isCode(file.name))
    }

    public fun parse(text: String, monospace: Boolean = false, title: String? = null): FlowDocument {
        val paragraphs = text.lineSequence()
            .map { line -> paragraph(line, monospace) }
            .toList()
            .dropLastWhile { it.inlines.isEmpty() }
        return FlowDocument(paragraphs, title)
    }

    /** Whether a file name looks like code or data, shown in a monospace font with tight lines. */
    public fun isCode(fileName: String): Boolean = fileName.substringAfterLast('.', "").lowercase() in codeExtensions

    internal fun paragraph(line: String, monospace: Boolean): Paragraph =
        Paragraph(
            line.toRuns(if (monospace) MonospaceRun else RunStyle.Default),
            if (monospace) CodeLine else ProseLine
        )

    private fun String.toRuns(style: RunStyle): List<TextRun> =
        if (isEmpty()) emptyList() else listOf(TextRun(this, style))

    private val MonospaceRun = RunStyle(monospace = true)

    // Code keeps its lines tight; prose gets a little air between paragraphs.
    private val CodeLine = ParagraphStyle(spaceBeforePt = 0f, spaceAfterPt = 0f)
    private val ProseLine = ParagraphStyle(spaceBeforePt = 0f, spaceAfterPt = PROSE_PARAGRAPH_SPACING_PT)
}
