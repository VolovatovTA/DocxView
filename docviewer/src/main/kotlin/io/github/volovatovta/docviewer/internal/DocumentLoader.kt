package io.github.volovatovta.docviewer.internal

import android.content.Context
import io.github.volovatovta.docviewer.DocumentInfo
import io.github.volovatovta.docviewer.DocumentSource
import io.github.volovatovta.docviewer.DocumentViewerConfig
import io.github.volovatovta.docviewer.core.FormatDetector
import io.github.volovatovta.docviewer.core.PasswordProtectedException
import io.github.volovatovta.docviewer.core.UnsupportedFormatException
import io.github.volovatovta.docviewer.core.format.DocumentContent
import io.github.volovatovta.docviewer.core.format.DocumentReader
import io.github.volovatovta.docviewer.core.format.ReadOptions
import io.github.volovatovta.docviewer.core.model.DocumentFormat
import io.github.volovatovta.docviewer.internal.pdf.PdfDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException

// Two steps: get the bytes into a local file, then open that file with the parser its content calls for.
internal object DocumentLoader {
    suspend fun resolve(context: Context, source: DocumentSource, onProgress: (Float?) -> Unit): ResolvedFile =
        withContext(Dispatchers.IO) {
            when (source) {
                is DocumentSource.FromFile -> ResolvedFile(source.file, source.file.name, null, temporary = false)
                is DocumentSource.FromUri -> ContentUriResolver.resolve(context, source, onProgress)
                is DocumentSource.FromUrl -> UrlDownloader.download(context, source, onProgress)
                is DocumentSource.FromAsset -> {
                    val metadata = SourceMetadata(source.path.substringAfterLast('/'))
                    DocumentCache.store(context, metadata, onProgress) { context.assets.open(source.path) }
                }
                is DocumentSource.FromBytes -> {
                    val metadata = SourceMetadata(source.fileName, sizeBytes = source.bytes.size.toLong())
                    DocumentCache.store(context, metadata, onProgress) { source.bytes.inputStream() }
                }
                is DocumentSource.FromStream ->
                    DocumentCache.store(context, SourceMetadata(source.fileName), onProgress) { source.open.open() }
            }
        }

    suspend fun open(
        resolved: ResolvedFile,
        source: DocumentSource,
        password: String?,
        config: DocumentViewerConfig,
    ): LoadedDocument = withContext(Dispatchers.IO) {
        val format = detectFormat(resolved, source)
        currentCoroutineContext().ensureActive()
        if (format == DocumentFormat.PDF) openPdf(resolved, password) else read(resolved, format, config)
    }

    private fun detectFormat(resolved: ResolvedFile, source: DocumentSource): DocumentFormat {
        val file = resolved.file
        if (!file.exists()) throw IOException("File not found: ${file.path}")
        return source.format
            ?: FormatDetector.detect(file, resolved.fileName, resolved.mimeType)
            ?: throw UnsupportedFormatException("Unknown document format")
    }

    private fun openPdf(resolved: ResolvedFile, password: String?): LoadedDocument {
        val pdf = PdfDocument.open(resolved.file, password)
        return LoadedDocument.Pdf(pdf, resolved.info(DocumentFormat.PDF, pdf.pageCount, pdf.isSearchSupported))
    }

    // Every format but PDF is read by docviewer-core; this only decides how much heap it may take.
    private fun read(resolved: ResolvedFile, format: DocumentFormat, config: DocumentViewerConfig): LoadedDocument {
        val options = ReadOptions(
            csv = config.csvOptions,
            memoryBudgetBytes = HeapBudget.forParsing(),
            maxBytesToParse = config.maxDocumentSizeBytes,
        )
        val content = try {
            DocumentReader.read(resolved.file, resolved.fileName, format, options)
        } catch (e: PasswordProtectedException) {
            // Decrypting Office files is out of scope; only PDF asks for a password.
            throw PasswordUnsupportedException(isPdf = false, cause = e)
        }
        return LoadedDocument.Parsed(content, resolved.info(format, content.itemCount, title = content.title))
    }

    private fun ResolvedFile.info(
        format: DocumentFormat,
        itemCount: Int,
        isSearchSupported: Boolean = true,
        title: String? = null,
    ) = DocumentInfo(format, fileName, title, itemCount, file.length(), isSearchSupported)
}

// Lines or blocks of text, rows of the first sheet.
private val DocumentContent.itemCount: Int
    get() = when (this) {
        is DocumentContent.Flow -> lines?.lineCount ?: document.blocks.size
        is DocumentContent.Sheets -> workbook.sheets.first().table.rowCount
    }

private val DocumentContent.title: String?
    get() = (this as? DocumentContent.Flow)?.document?.title
