package io.github.volovatovta.docviewer.core.text

import io.github.volovatovta.docviewer.core.PlainTextParser
import io.github.volovatovta.docviewer.core.TextDecoding
import io.github.volovatovta.docviewer.core.format.DocumentContent
import io.github.volovatovta.docviewer.core.format.FormatReader
import io.github.volovatovta.docviewer.core.format.ProbedFile
import io.github.volovatovta.docviewer.core.format.ReadRequest
import io.github.volovatovta.docviewer.core.model.DocumentFormat
import io.github.volovatovta.docviewer.core.stream.StreamedText
import io.github.volovatovta.docviewer.core.stream.TextFile

/** Anything that reads like text. Lines are read from disk on demand, so memory does not grow with the file. */
internal object TextFormat : FormatReader {
    override val format = DocumentFormat.TXT

    override val peakMemoryPerByte = 5

    override fun recognizes(file: ProbedFile): Boolean = file.text != null

    // UTF-16 cannot be split into lines byte by byte, so only such files are parsed in memory.
    override fun read(request: ReadRequest): DocumentContent {
        val monospace = PlainTextParser.isCode(request.name)
        val lines = TextFile.open(request.file)
        if (lines != null) return DocumentContent.Flow(StreamedText.document(lines, monospace), lines)
        request.requireMemoryFor(this)
        val text = TextDecoding.decode(request.file.readBytes()).text
        return DocumentContent.Flow(PlainTextParser.parse(text, monospace))
    }
}
