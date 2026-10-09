package io.github.volovatovta.docviewer.core

import io.github.volovatovta.docviewer.core.model.FlowDocument
import io.github.volovatovta.docviewer.core.model.Paragraph
import io.github.volovatovta.docviewer.core.model.ParagraphStyle
import io.github.volovatovta.docviewer.core.model.RunStyle
import io.github.volovatovta.docviewer.core.model.TextRun
import java.io.File

public object PlainTextParser {

    private val CODE_EXTENSIONS = setOf("json", "xml", "log", "yaml", "yml", "ini", "conf")

    public fun parse(file: File): FlowDocument {
        val decoded = TextDecoding.decode(file.readBytes())
        return parse(decoded.text, monospace = file.extension.lowercase() in CODE_EXTENSIONS, title = file.nameWithoutExtension)
    }

    public fun parse(text: String, monospace: Boolean = false, title: String? = null): FlowDocument {
        val style = if (monospace) RunStyle(monospace = true) else RunStyle.Default
        val paragraphStyle = ParagraphStyle(spaceBeforePt = 0f, spaceAfterPt = if (monospace) 0f else 4f)
        val blocks = text.replace("\r\n", "\n").replace('\r', '\n').split('\n')
            .map { line -> Paragraph(if (line.isEmpty()) emptyList() else listOf(TextRun(line, style)), paragraphStyle) }
            .dropLastWhile { it.inlines.isEmpty() }
        return FlowDocument(blocks, title)
    }
}
