package io.github.volovatovta.docviewer.internal

import java.io.File

// A source materialized as a local file; temporary copies are deleted on release.
internal class ResolvedFile(
    val file: File,
    val fileName: String?,
    val mimeType: String?,
    private val temporary: Boolean,
) {
    fun release() {
        if (temporary) file.delete()
    }
}
