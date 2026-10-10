package io.github.volovatovta.docviewer.core

import io.github.volovatovta.docviewer.core.model.DocumentFormat
import java.io.File
import java.io.IOException
import java.util.zip.ZipFile

public object FormatDetector {

    private const val HEADER_BYTES = 1024
    private const val TEXT_SAMPLE_BYTES = 4096
    private const val CSV_SAMPLE_BYTES = 16 * 1024
    private const val MAX_BINARY_CHARACTER_SHARE = 0.02
    private const val TEXT_CONTROL_CHARACTERS = "\r\n\t\u000C"
    private const val WORD_MAIN_PART = "word/document.xml"
    private const val CONTENT_TYPES_PART = "[Content_Types].xml"
    private const val WORDPROCESSING_CONTENT_TYPE = "wordprocessingml"

    private val pdfSignature = "%PDF".toByteArray()
    private val zipSignature = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
    private val compoundFileSignature = byteArrayOf(
        0xD0.toByte(),
        0xCF.toByte(),
        0x11,
        0xE0.toByte(),
        0xA1.toByte(),
        0xB1.toByte(),
        0x1A,
        0xE1.toByte(),
    )
    private val textFormats = setOf(DocumentFormat.CSV, DocumentFormat.TXT)

    // Magic bytes win over the name/mime hint: files are often misnamed, especially from messengers.
    public fun detect(file: File, fileName: String? = null, mimeType: String? = null): DocumentFormat? {
        val header = file.readPrefix(HEADER_BYTES)
        val hint = DocumentFormat.fromExtension(fileName?.substringAfterLast('.', ""))
            ?: DocumentFormat.fromMimeType(mimeType)
        return when {
            header.containsPdfSignature() -> DocumentFormat.PDF
            header.startsWith(zipSignature) -> DocumentFormat.DOCX.takeIf { isWordArchive(file) }
            header.startsWith(compoundFileSignature) -> DocumentFormat.DOC
            hint in textFormats -> hint
            looksLikeText(file) -> if (looksLikeCsv(file)) DocumentFormat.CSV else DocumentFormat.TXT
            else -> null
        }
    }

    private fun isWordArchive(file: File): Boolean = try {
        ZipFile(file).use { zip -> zip.getEntry(WORD_MAIN_PART) != null || zip.declaresWordprocessingContent() }
    } catch (_: IOException) {
        false
    } catch (_: IllegalArgumentException) {
        false
    }

    private fun ZipFile.declaresWordprocessingContent(): Boolean {
        val contentTypes = getEntry(CONTENT_TYPES_PART) ?: return false
        val declaration = getInputStream(contentTypes).use { it.readBytes().decodeToString() }
        return WORDPROCESSING_CONTENT_TYPE in declaration
    }

    private fun looksLikeText(file: File): Boolean {
        val sample = file.readPrefix(TEXT_SAMPLE_BYTES)
        if (sample.isEmpty()) return true
        val text = TextDecoding.decode(sample).text
        return text.count(::isBinaryCharacter) <= text.length * MAX_BINARY_CHARACTER_SHARE
    }

    private fun isBinaryCharacter(char: Char): Boolean =
        char == '\u0000' || char == '\uFFFD' || (char < ' ' && char !in TEXT_CONTROL_CHARACTERS)

    private fun looksLikeCsv(file: File): Boolean {
        val sample = TextDecoding.decode(file.readPrefix(CSV_SAMPLE_BYTES)).text
        return CsvParser.detectDelimiter(sample) != null
    }

    // The spec allows garbage before the header within the first kilobyte.
    private fun ByteArray.containsPdfSignature(): Boolean =
        (0..size - pdfSignature.size).any { offset -> matchesAt(offset, pdfSignature) }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean = size >= prefix.size && matchesAt(0, prefix)

    private fun ByteArray.matchesAt(offset: Int, expected: ByteArray): Boolean =
        expected.indices.all { this[offset + it] == expected[it] }
}
