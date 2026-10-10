package io.github.volovatovta.docviewer.internal

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.volovatovta.docviewer.DocumentSource
import io.github.volovatovta.docviewer.DocumentViewerConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.Closeable

// Owns the local file and the opened document of one source while it is on screen.
internal class DocumentSession(private val context: Context, private val source: DocumentSource) : Closeable {

    var document: LoadedDocument? by mutableStateOf(null)
        private set

    // The resolved file survives reloads, so a password retry does not download or copy the document again.
    private var resolved: ResolvedFile? = null

    suspend fun resolve(onProgress: (Float?) -> Unit): ResolvedFile =
        resolved ?: DocumentLoader.resolve(context, source, onProgress).also { resolved = it }

    suspend fun open(file: ResolvedFile, password: String?, config: DocumentViewerConfig): LoadedDocument {
        closeDocument()
        // Opening cannot be interrupted midway, so a result that arrives after cancellation is closed, not leaked.
        val loaded = withContext(NonCancellable) { DocumentLoader.open(file, source, password, config) }
        if (!currentCoroutineContext().isActive) {
            loaded.close()
            throw CancellationException("Document load was cancelled")
        }
        document = loaded
        return loaded
    }

    fun closeDocument() {
        document?.close()
        document = null
    }

    override fun close() {
        closeDocument()
        resolved?.release()
    }
}
