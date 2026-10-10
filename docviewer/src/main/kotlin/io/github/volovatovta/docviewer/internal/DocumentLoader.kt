package io.github.volovatovta.docviewer.internal

import android.content.Context
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
import io.github.volovatovta.docviewer.internal.pdf.PdfDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

// Two steps: get the bytes into a local file, then open that file with the parser its content calls for.
internal object DocumentLoader {
    private const val TSV_EXTENSION = ".tsv"
    private const val TAB = '\t'

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
        csvOptions: CsvOptions,
    ): LoadedDocument = withContext(Dispatchers.IO) {
        val format = detectFormat(resolved, source)
        currentCoroutineContext().ensureActive()
        when (format) {
            DocumentFormat.PDF -> openPdf(resolved, password)
            DocumentFormat.DOCX, DocumentFormat.DOC, DocumentFormat.TXT -> openFlow(resolved, format)
            DocumentFormat.CSV -> openGrid(resolved, csvOptions)
        }
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

    private fun openFlow(resolved: ResolvedFile, format: DocumentFormat): LoadedDocument {
        val document = parseFlow(resolved.file, format)
        return LoadedDocument.Flow(document, resolved.info(format, document.blocks.size, title = document.title))
    }

    private fun parseFlow(file: File, format: DocumentFormat): FlowDocument = try {
        when (format) {
            DocumentFormat.DOCX -> DocxParser.parse(file)
            DocumentFormat.DOC -> DocParser.parse(file)
            else -> PlainTextParser.parse(file)
        }
    } catch (e: PasswordProtectedException) {
        // Encrypted OOXML arrives as an OLE container; decryption is out of scope.
        throw PasswordUnsupportedException(isPdf = false, cause = e)
    }

    private fun openGrid(resolved: ResolvedFile, csvOptions: CsvOptions): LoadedDocument {
        val isTsv = resolved.fileName?.endsWith(TSV_EXTENSION, ignoreCase = true) == true
        val options = if (isTsv && csvOptions.delimiter == null) csvOptions.copy(delimiter = TAB) else csvOptions
        val table = CsvParser.parse(resolved.file, options)
        return LoadedDocument.Grid(table, resolved.info(DocumentFormat.CSV, table.rows.size))
    }

    private fun ResolvedFile.info(
        format: DocumentFormat,
        itemCount: Int,
        isSearchSupported: Boolean = true,
        title: String? = null,
    ) = DocumentInfo(format, fileName, title, itemCount, file.length(), isSearchSupported)
}
