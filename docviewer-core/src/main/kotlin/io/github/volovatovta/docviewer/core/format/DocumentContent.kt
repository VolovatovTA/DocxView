package io.github.volovatovta.docviewer.core.format

import io.github.volovatovta.docviewer.core.model.FlowDocument
import io.github.volovatovta.docviewer.core.model.Workbook
import io.github.volovatovta.docviewer.core.stream.TextFile
import java.io.Closeable

/**
 * A document after reading, in the shape it is shown: flowing text or sheets of cells. Later versions may add
 * other shapes (slides, say), so a `when` over it should keep an `else` branch; only this library creates them.
 */
public abstract class DocumentContent internal constructor() : Closeable {

    /** Word documents and plain text; [lines] is set when the text is read from disk line by line. */
    public class Flow internal constructor(
        public val document: FlowDocument,
        public val lines: TextFile? = null,
    ) : DocumentContent() {
        override fun close() {
            document.close()
            lines?.close()
        }
    }

    /** Spreadsheets and CSV files. */
    public class Sheets internal constructor(public val workbook: Workbook) : DocumentContent() {
        override fun close(): Unit = workbook.close()
    }
}
