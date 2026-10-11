package io.github.volovatovta.docviewer.core.text

import io.github.volovatovta.docviewer.core.CsvParser
import io.github.volovatovta.docviewer.core.format.DocumentContent
import io.github.volovatovta.docviewer.core.format.FormatReader
import io.github.volovatovta.docviewer.core.format.ProbedFile
import io.github.volovatovta.docviewer.core.format.ReadRequest
import io.github.volovatovta.docviewer.core.model.DocumentFormat
import io.github.volovatovta.docviewer.core.model.Sheet
import io.github.volovatovta.docviewer.core.model.Workbook
import io.github.volovatovta.docviewer.core.stream.CsvFile

/** Text with a consistent delimiter. Rows are read from disk on demand, so there is no row limit. */
internal object CsvFormat : FormatReader {
    override val format = DocumentFormat.CSV

    override val peakMemoryPerByte = 9

    // A .txt name keeps a file plain text even when its lines happen to split evenly on commas.
    override fun recognizes(file: ProbedFile): Boolean {
        val text = file.text ?: return false
        return when (file.hint) {
            DocumentFormat.CSV -> true
            DocumentFormat.TXT -> false
            else -> CsvParser.detectDelimiter(text) != null
        }
    }

    // UTF-16 and non-ASCII delimiters cannot be split byte by byte, so only such files are parsed in memory.
    override fun read(request: ReadRequest): DocumentContent {
        val options = request.options.csv.let { csv ->
            if (csv.delimiter == null && request.name.endsWith(TSV_EXTENSION, ignoreCase = true)) {
                csv.copy(
                    delimiter = TAB
                )
            } else {
                csv
            }
        }
        val table = CsvFile.open(request.file, options) ?: run {
            request.requireMemoryFor(this)
            CsvParser.parse(request.file, options)
        }
        return DocumentContent.Sheets(Workbook(listOf(Sheet(request.name.substringBeforeLast('.'), table))))
    }

    private const val TSV_EXTENSION = ".tsv"
    private const val TAB = '\t'
}
