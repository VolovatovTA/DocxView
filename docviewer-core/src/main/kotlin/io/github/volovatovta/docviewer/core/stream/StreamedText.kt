package io.github.volovatovta.docviewer.core.stream

import io.github.volovatovta.docviewer.core.PlainTextParser
import io.github.volovatovta.docviewer.core.model.Block
import io.github.volovatovta.docviewer.core.model.FlowDocument
import io.github.volovatovta.docviewer.core.model.Paragraph
import java.lang.ref.ReferenceQueue
import java.lang.ref.WeakReference

/** A [FlowDocument] whose paragraphs are read from [lines] when asked for and dropped when nothing uses them. */
public object StreamedText {

    public fun document(lines: TextFile, monospace: Boolean, title: String? = null): FlowDocument =
        FlowDocument(LineBlocks(lines, monospace), title)
}

// The same line yields the same Paragraph while anything holds it, since search highlights match by identity.
private class LineBlocks(private val lines: TextFile, private val monospace: Boolean) : AbstractList<Block>() {
    private val alive = HashMap<Int, IndexedReference>()
    private val collected = ReferenceQueue<Paragraph>()

    override val size: Int get() = lines.lineCount

    override fun get(index: Int): Block = synchronized(this) {
        purgeCollected()
        alive[index]?.get() ?: PlainTextParser.paragraph(lines.line(index), monospace).also { paragraph ->
            alive[index] = IndexedReference(index, paragraph, collected)
        }
    }

    private fun purgeCollected() {
        var reference = collected.poll()
        while (reference != null) {
            val indexed = reference as IndexedReference
            if (alive[indexed.index] === indexed) alive.remove(indexed.index)
            reference = collected.poll()
        }
    }

    private class IndexedReference(val index: Int, paragraph: Paragraph, queue: ReferenceQueue<Paragraph>) :
        WeakReference<Paragraph>(paragraph, queue)
}
