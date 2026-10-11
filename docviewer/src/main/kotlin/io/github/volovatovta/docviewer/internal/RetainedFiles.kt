package io.github.volovatovta.docviewer.internal

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.annotation.MainThread
import io.github.volovatovta.docviewer.DocumentSource

// Local copies handed from a viewer torn down by a configuration change to the one that replaces it.
@MainThread
internal object RetainedFiles {
    private class Parked(val source: DocumentSource, val file: ResolvedFile)

    private val parked = HashMap<String, Parked>()

    fun park(key: String, source: DocumentSource, file: ResolvedFile) {
        parked.put(key, Parked(source, file))?.file?.release()
    }

    // A copy of another source is released; one never claimed is swept by the cache's stale-file cleanup.
    fun take(key: String, source: DocumentSource): ResolvedFile? {
        val entry = parked.remove(key) ?: return null
        if (entry.source == source) return entry.file
        entry.file.release()
        return null
    }
}

internal val Context.isChangingConfigurations: Boolean
    get() {
        var context: Context? = this
        while (context is ContextWrapper) {
            if (context is Activity) return context.isChangingConfigurations
            context = context.baseContext
        }
        return false
    }
