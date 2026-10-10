package io.github.volovatovta.docviewer.internal

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import io.github.volovatovta.docviewer.DocumentSource
import java.io.File
import java.io.IOException

// Turns content:// and file:// URIs into local files, copying content providers' streams.
internal object ContentUriResolver {

    suspend fun resolve(context: Context, source: DocumentSource.FromUri, onProgress: (Float?) -> Unit): ResolvedFile {
        val uri = source.uri
        if (uri.scheme == ContentResolver.SCHEME_FILE) return localFile(source)
        val resolver = context.contentResolver
        val columns = queryOpenableColumns(resolver, uri)
        val metadata = SourceMetadata(
            fileName = source.fileName ?: columns.displayName ?: uri.lastPathSegment?.substringAfterLast('/'),
            mimeType = source.mimeType ?: runCatching { resolver.getType(uri) }.getOrNull(),
            sizeBytes = columns.size,
        )
        return DocumentCache.store(context, metadata, onProgress) {
            resolver.openInputStream(uri) ?: throw IOException("Cannot open $uri")
        }
    }

    private fun localFile(source: DocumentSource.FromUri): ResolvedFile {
        val file = File(requireNotNull(source.uri.path) { "Bad file uri: ${source.uri}" })
        return ResolvedFile(file, source.fileName ?: file.name, source.mimeType, temporary = false)
    }

    private class OpenableColumnsRow(val displayName: String?, val size: Long?)

    // Providers may refuse the query or omit columns; the document is still readable without them.
    private fun queryOpenableColumns(resolver: ContentResolver, uri: Uri): OpenableColumnsRow = runCatching {
        val projection = arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        resolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            OpenableColumnsRow(
                displayName = cursor.takeIf { nameIndex >= 0 }?.getString(nameIndex),
                size = cursor.takeIf { sizeIndex >= 0 && !it.isNull(sizeIndex) }?.getLong(sizeIndex),
            )
        }
    }.getOrNull() ?: OpenableColumnsRow(displayName = null, size = null)
}
