package io.github.volovatovta.docviewer.internal

import android.content.Context
import io.github.volovatovta.docviewer.DocumentInfo
import io.github.volovatovta.docviewer.DocumentSource
import io.github.volovatovta.docviewer.DocumentViewerConfig
import io.github.volovatovta.docviewer.core.CsvParser
import io.github.volovatovta.docviewer.core.FormatDetector
import io.github.volovatovta.docviewer.core.PasswordProtectedException
import io.github.volovatovta.docviewer.core.PlainTextParser
import io.github.volovatovta.docviewer.core.TextDecoding
import io.github.volovatovta.docviewer.core.UnsupportedFormatException
import io.github.volovatovta.docviewer.core.doc.DocParser
import io.github.volovatovta.docviewer.core.docx.DocxParser
import io.github.volovatovta.docviewer.core.model.DocumentFormat
import io.github.volovatovta.docviewer.core.model.FlowDocument
import io.github.volovatovta.docviewer.core.stream.CsvFile
import io.github.volovatovta.docviewer.core.stream.StreamedText
import io.github.volovatovta.docviewer.core.stream.TextFile
import io.github.volovatovta.docviewer.internal.pdf.PdfDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
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
        config: DocumentViewerConfig,
    ): LoadedDocument = withContext(Dispatchers.IO) {
        val format = detectFormat(resolved, source)
        currentCoroutineContext().ensureActive()
        when (format) {
            DocumentFormat.PDF -> openPdf(resolved, password)
            DocumentFormat.TXT -> openText(resolved, config)
            DocumentFormat.DOCX, DocumentFormat.DOC -> openFlow(resolved, format, config)
            DocumentFormat.CSV -> openGrid(resolved, config)
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

    // Text is read from disk line by line; only UTF-16, which cannot be split byte-wise, is parsed in memory.
    private fun openText(resolved: ResolvedFile, config: DocumentViewerConfig): LoadedDocument {
        val monospace = PlainTextParser.isCode(resolved.fileName ?: resolved.file.name)
        val lines = TextFile.open(resolved.file) ?: return openFlow(resolved, DocumentFormat.TXT, config)
        val document = StreamedText.document(lines, monospace)
        return LoadedDocument.Flow(document, resolved.info(DocumentFormat.TXT, lines.lineCount), lines)
    }

    private fun openFlow(resolved: ResolvedFile, format: DocumentFormat, config: DocumentViewerConfig): LoadedDocument {
        DocumentSizeLimit.check(resolved.file, format, config.maxDocumentSizeBytes)
        val document = parseFlow(resolved, format)
        return LoadedDocument.Flow(document, resolved.info(format, document.blocks.size, title = document.title))
    }

    private fun parseFlow(resolved: ResolvedFile, format: DocumentFormat): FlowDocument = try {
        when (format) {
            DocumentFormat.DOCX -> DocxParser.parse(resolved.file)
            DocumentFormat.DOC -> DocParser.parse(resolved.file)
            else -> PlainTextParser.parse(
                TextDecoding.decode(resolved.file.readBytes()).text,
                monospace = PlainTextParser.isCode(resolved.fileName ?: resolved.file.name),
            )
        }
    } catch (e: PasswordProtectedException) {
        // Encrypted OOXML arrives as an OLE container; decryption is out of scope.
        throw PasswordUnsupportedException(isPdf = false, cause = e)
    }

    // Rows are read from disk on demand; files that cannot be streamed are parsed in memory within the size limit.
    private fun openGrid(resolved: ResolvedFile, config: DocumentViewerConfig): LoadedDocument {
        val csvOptions = config.csvOptions
        val isTsv = resolved.fileName?.endsWith(TSV_EXTENSION, ignoreCase = true) == true
        val options = if (isTsv && csvOptions.delimiter == null) csvOptions.copy(delimiter = TAB) else csvOptions
        val table = CsvFile.open(resolved.file, options) ?: run {
            DocumentSizeLimit.check(resolved.file, DocumentFormat.CSV, config.maxDocumentSizeBytes)
            CsvParser.parse(resolved.file, options)
        }
        return LoadedDocument.Grid(table, resolved.info(DocumentFormat.CSV, table.rowCount))
    }

    private fun ResolvedFile.info(
        format: DocumentFormat,
        itemCount: Int,
        isSearchSupported: Boolean = true,
        title: String? = null,
    ) = DocumentInfo(format, fileName, title, itemCount, file.length(), isSearchSupported)
}
