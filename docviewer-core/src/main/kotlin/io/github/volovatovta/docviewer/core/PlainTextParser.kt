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
        return parse(decoded.text, monospace = file.extension.lowercase() in codeExtensions)
    }

    public fun parse(text: String, monospace: Boolean = false, title: String? = null): FlowDocument {
        val runStyle = if (monospace) RunStyle(monospace = true) else RunStyle.Default
        // Code keeps its lines tight; prose gets a little air between paragraphs.
        val spacingAfter = if (monospace) 0f else PROSE_PARAGRAPH_SPACING_PT
        val paragraphStyle = ParagraphStyle(spaceBeforePt = 0f, spaceAfterPt = spacingAfter)
        val paragraphs = text.lineSequence()
            .map { line -> Paragraph(line.toRuns(runStyle), paragraphStyle) }
            .toList()
            .dropLastWhile { it.inlines.isEmpty() }
        return FlowDocument(paragraphs, title)
    }

    private fun String.toRuns(style: RunStyle): List<TextRun> =
        if (isEmpty()) emptyList() else listOf(TextRun(this, style))
}
