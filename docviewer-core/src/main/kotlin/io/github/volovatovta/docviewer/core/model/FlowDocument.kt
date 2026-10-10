package io.github.volovatovta.docviewer.core.model

import java.io.Closeable

// Reflowable document: DOCX, DOC and plain text are all converted into this tree.
public class FlowDocument(
    public val blocks: List<Block>,
    public val title: String? = null,
    public val images: ImageStore = ImageStore.Empty,
    public val warnings: List<String> = emptyList(),
) : Closeable {
    override fun close(): Unit = images.close()
}
