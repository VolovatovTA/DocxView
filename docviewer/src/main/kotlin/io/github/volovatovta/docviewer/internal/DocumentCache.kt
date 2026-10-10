package io.github.volovatovta.docviewer.internal

import android.annotation.SuppressLint
import android.content.Context
import io.github.volovatovta.docviewer.core.DocumentTooLargeException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.TimeUnit

// What is known about a document before its bytes are copied.
internal class SourceMetadata(val fileName: String?, val mimeType: String? = null, val sizeBytes: Long? = null)

// Local copies of streamed documents: PdfRenderer and ZipFile need a seekable file.
internal object DocumentCache {
    private const val DIRECTORY = "docviewer"
    private const val BUFFER_BYTES = 64 * 1024
    private const val MAX_EXTENSION_LENGTH = 8
    private val STALE_AFTER_MILLIS = TimeUnit.DAYS.toMillis(1)

    // Storage left for the rest of the device when a document is copied into the cache.
    private const val FREE_SPACE_RESERVE_BYTES = 200L * 1024 * 1024

    // Free space without clearable caches is a conservative bound, and the bound only stops endless streams.
    @SuppressLint("UsableSpace")
    suspend fun store(
        context: Context,
        metadata: SourceMetadata,
        onProgress: (Float?) -> Unit,
        open: () -> InputStream,
    ): ResolvedFile {
        val target = newCacheFile(context, metadata.fileName)
        var stored = false
        try {
            val progress = ProgressReporter(metadata.sizeBytes, onProgress)
            val limit = (target.parentFile?.usableSpace ?: 0L) - FREE_SPACE_RESERVE_BYTES
            open().use { input ->
                target.outputStream().use { output -> input.copyCancellable(output, progress, limit) }
            }
            stored = true
        } finally {
            if (!stored) target.delete()
        }
        return ResolvedFile(target, metadata.fileName, metadata.mimeType, temporary = true)
    }

    private fun newCacheFile(context: Context, fileName: String?): File {
        val directory = File(context.cacheDir, DIRECTORY).apply { mkdirs() }
        deleteStaleCopies(directory)
        // The extension is kept because format detection falls back to it for plain-text formats.
        val extension = fileName?.substringAfterLast('.', "")
            ?.takeIf { it.length in 1..MAX_EXTENSION_LENGTH && it.all(Char::isLetterOrDigit) }
            ?.let { ".$it" }
            .orEmpty()
        return File(directory, UUID.randomUUID().toString() + extension)
    }

    // Copies left behind by a crashed process.
    private fun deleteStaleCopies(directory: File) {
        val threshold = System.currentTimeMillis() - STALE_AFTER_MILLIS
        directory.listFiles()?.filter { it.lastModified() < threshold }?.forEach(File::delete)
    }

    // An endless or huge stream stops before the device runs out of storage.
    private suspend fun InputStream.copyCancellable(output: OutputStream, progress: ProgressReporter, limit: Long) {
        val buffer = ByteArray(BUFFER_BYTES)
        var copied = 0L
        var count = read(buffer)
        while (count >= 0) {
            // A read can block for long; a load cancelled meanwhile must not report progress any more.
            currentCoroutineContext().ensureActive()
            copied += count
            if (copied > limit) throw DocumentTooLargeException(copied, limit.coerceAtLeast(0))
            output.write(buffer, 0, count)
            progress.onCopied(count)
            count = read(buffer)
        }
    }
}

// Reports copy progress in whole-percent steps when the total size is known.
private class ProgressReporter(private val totalBytes: Long?, private val onProgress: (Float?) -> Unit) {
    private var copiedBytes = 0L
    private var lastReported = -1f

    fun onCopied(bytes: Int) {
        copiedBytes += bytes
        val total = totalBytes?.takeIf { it > 0 } ?: return
        val progress = (copiedBytes.toFloat() / total).coerceIn(0f, 1f)
        if (progress - lastReported >= STEP) {
            lastReported = progress
            onProgress(progress)
        }
    }

    private companion object {
        const val STEP = 0.01f
    }
}
