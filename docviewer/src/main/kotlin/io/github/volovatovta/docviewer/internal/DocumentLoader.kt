package io.github.volovatovta.docviewer.internal

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import io.github.volovatovta.docviewer.DocumentInfo
import io.github.volovatovta.docviewer.DocumentSource
import io.github.volovatovta.docviewer.core.CsvOptions
import io.github.volovatovta.docviewer.core.CsvParser
import io.github.volovatovta.docviewer.core.FormatDetector
import io.github.volovatovta.docviewer.core.PasswordProtectedException
import io.github.volovatovta.docviewer.core.PlainTextParser
import io.github.volovatovta.docviewer.core.UnsupportedFormatException
import io.github.volovatovta.docviewer.core.doc.DocParser
import io.github.volovatovta.docviewer.core.docx.DocxParser
import io.github.volovatovta.docviewer.core.model.DocumentFormat
import io.github.volovatovta.docviewer.core.model.FlowDocument
import io.github.volovatovta.docviewer.core.model.TableDocument
import io.github.volovatovta.docviewer.internal.pdf.PdfDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlin.coroutines.coroutineContext

internal sealed interface LoadedDocument : Closeable {
    val info: DocumentInfo

    class Pdf(val document: PdfDocument, override val info: DocumentInfo) : LoadedDocument {
        override fun close() = document.close()
    }

    class Flow(val document: FlowDocument, override val info: DocumentInfo) : LoadedDocument {
        override fun close() = document.close()
    }

    class Grid(val document: TableDocument, override val info: DocumentInfo) : LoadedDocument {
        override fun close() = Unit
    }
}

// A source materialized as a local file; temporary copies are deleted on release.
internal class ResolvedFile(val file: File, val fileName: String?, val mimeType: String?, private val temporary: Boolean) {
    fun release() {
        if (temporary) file.delete()
    }
}

internal class PasswordUnsupportedException(val isPdf: Boolean) :
    UnsupportedFormatException(if (isPdf) "Password-protected PDF requires Android 15+" else "Password-protected Word documents are not supported")

internal class NetworkException(message: String, cause: Throwable? = null) : IOException(message, cause)

internal object DocumentLoader {

    private const val BUFFER = 64 * 1024

    suspend fun resolve(context: Context, source: DocumentSource, onProgress: (Float?) -> Unit): ResolvedFile = withContext(Dispatchers.IO) {
        when (source) {
            is DocumentSource.FromFile -> ResolvedFile(source.file, source.file.name, null, temporary = false)
            is DocumentSource.FromUri -> resolveUri(context, source, onProgress)
            is DocumentSource.FromAsset -> {
                val name = source.path.substringAfterLast('/')
                copyToCache(context, name, null, null, onProgress) { context.assets.open(source.path) }
            }
            is DocumentSource.FromBytes -> copyToCache(context, source.fileName, null, source.bytes.size.toLong(), onProgress) { source.bytes.inputStream() }
            is DocumentSource.FromStream -> copyToCache(context, source.fileName, null, null, onProgress) { source.open.open() }
            is DocumentSource.FromUrl -> download(context, source, onProgress)
        }
    }

    suspend fun open(resolved: ResolvedFile, source: DocumentSource, password: String?, csvOptions: CsvOptions): LoadedDocument = withContext(Dispatchers.IO) {
        val file = resolved.file
        if (!file.exists()) throw IOException("File not found: ${file.path}")
        val format = source.format
            ?: FormatDetector.detect(file, resolved.fileName, resolved.mimeType)
            ?: throw UnsupportedFormatException("Unknown document format")
        val name = resolved.fileName
        val size = file.length()
        coroutineContext.ensureActive()
        when (format) {
            DocumentFormat.PDF -> {
                val pdf = PdfDocument.open(file, password)
                LoadedDocument.Pdf(pdf, DocumentInfo(format, name, null, pdf.pageCount, size, pdf.isSearchSupported))
            }
            DocumentFormat.DOCX, DocumentFormat.DOC, DocumentFormat.TXT -> {
                val doc = try {
                    when (format) {
                        DocumentFormat.DOCX -> DocxParser.parse(file)
                        DocumentFormat.DOC -> DocParser.parse(file)
                        else -> PlainTextParser.parse(file)
                    }
                } catch (e: PasswordProtectedException) {
                    // Encrypted OOXML arrives as an OLE container; decryption is out of scope.
                    throw PasswordUnsupportedException(isPdf = false)
                }
                LoadedDocument.Flow(doc, DocumentInfo(format, name, doc.title, doc.blocks.size, size, isSearchSupported = true))
            }
            DocumentFormat.CSV -> {
                val tsv = name?.endsWith(".tsv", ignoreCase = true) == true
                val options = if (tsv && csvOptions.delimiter == null) csvOptions.copy(delimiter = '\t') else csvOptions
                val table = CsvParser.parse(file, options)
                LoadedDocument.Grid(table, DocumentInfo(format, name, null, table.rows.size, size, isSearchSupported = true))
            }
        }
    }

    private suspend fun resolveUri(context: Context, source: DocumentSource.FromUri, onProgress: (Float?) -> Unit): ResolvedFile {
        val uri = source.uri
        if (uri.scheme == ContentResolver.SCHEME_FILE) {
            val file = File(requireNotNull(uri.path) { "Bad file uri" })
            return ResolvedFile(file, source.fileName ?: file.name, source.mimeType, temporary = false)
        }
        val resolver = context.contentResolver
        var name = source.fileName
        var size: Long? = null
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (name == null && nameIndex >= 0) name = cursor.getString(nameIndex)
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
                }
            }
        }
        if (name == null) name = uri.lastPathSegment?.substringAfterLast('/')
        val mime = source.mimeType ?: runCatching { resolver.getType(uri) }.getOrNull()
        return copyToCache(context, name, mime, size, onProgress) {
            resolver.openInputStream(uri) ?: throw IOException("Cannot open $uri")
        }
    }

    private suspend fun download(context: Context, source: DocumentSource.FromUrl, onProgress: (Float?) -> Unit): ResolvedFile {
        var url = URL(source.url)
        var connection: HttpURLConnection
        var redirects = 0
        // HttpURLConnection does not follow http -> https redirects on its own.
        while (true) {
            connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = true
                source.headers.forEach { (k, v) -> setRequestProperty(k, v) }
            }
            val code = try {
                connection.responseCode
            } catch (e: IOException) {
                throw NetworkException("Cannot connect to ${url.host}", e)
            }
            if (code in 300..399 && redirects++ < 5) {
                val location = connection.getHeaderField("Location") ?: break
                url = URL(url, location)
                connection.disconnect()
                continue
            }
            if (code !in 200..299) {
                connection.disconnect()
                throw NetworkException("HTTP $code for ${source.url}")
            }
            break
        }
        try {
            val disposition = connection.getHeaderField("Content-Disposition")
            val name = disposition?.let { Regex("filename\\*?=(?:UTF-8'')?\"?([^\";]+)\"?").find(it)?.groupValues?.get(1) }
                ?.let { runCatching { Uri.decode(it) }.getOrDefault(it) }
                ?: url.path.substringAfterLast('/').takeIf { it.isNotEmpty() }?.let { Uri.decode(it) }
            val length = connection.contentLengthLong.takeIf { it > 0 }
            return copyToCache(context, name, connection.contentType, length, onProgress) { connection.inputStream }
        } catch (e: IOException) {
            throw if (e is NetworkException) e else NetworkException("Download failed", e)
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun copyToCache(
        context: Context,
        name: String?,
        mime: String?,
        size: Long?,
        onProgress: (Float?) -> Unit,
        open: () -> InputStream,
    ): ResolvedFile {
        val dir = File(context.cacheDir, "docviewer").apply { mkdirs() }
        cleanStale(dir)
        val extension = name?.substringAfterLast('.', "")?.takeIf { it.length in 1..8 }?.let { ".$it" } ?: ""
        val target = File(dir, UUID.randomUUID().toString() + extension)
        try {
            open().use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(BUFFER)
                    var copied = 0L
                    var lastReported = -1f
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        copied += n
                        if (size != null && size > 0) {
                            val progress = (copied.toFloat() / size).coerceIn(0f, 1f)
                            if (progress - lastReported >= 0.01f) {
                                lastReported = progress
                                onProgress(progress)
                            }
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            target.delete()
            throw e
        }
        return ResolvedFile(target, name, mime, temporary = true)
    }

    // Copies left behind by a crashed process.
    private fun cleanStale(dir: File) {
        val threshold = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        dir.listFiles()?.forEach { if (it.lastModified() < threshold) it.delete() }
    }
}
