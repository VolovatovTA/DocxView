package io.github.volovatovta.docviewer.core.docx

import io.github.volovatovta.docviewer.core.model.ImageStore
import io.github.volovatovta.docviewer.core.readUpTo
import java.util.zip.ZipFile

// Reads images lazily from the still-open archive; closing the document closes the archive.
internal class ZipImageStore(private val zip: ZipFile) : ImageStore {

    @Volatile
    private var closed = false

    override fun open(imageId: String): ByteArray? = synchronized(this) {
        if (closed) return null
        val entry = zip.getEntry(imageId) ?: return null
        // Guard against zip bombs posing as images; the declared size can lie, so cap the actual read too.
        if (entry.size > MAX_IMAGE_BYTES) return null
        val bytes = zip.getInputStream(entry).use { it.readUpTo(MAX_IMAGE_BYTES + 1) }
        bytes.takeIf { it.size <= MAX_IMAGE_BYTES }
    }

    override fun close() = synchronized(this) {
        closed = true
        zip.close()
    }

    private companion object {
        const val MAX_IMAGE_BYTES = 40 * 1024 * 1024
    }
}
