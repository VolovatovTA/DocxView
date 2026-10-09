package io.github.volovatovta.docviewer.core

import io.github.volovatovta.docviewer.core.model.DocumentFormat
import java.io.File
import java.util.zip.ZipFile

public object FormatDetector {

    // Magic bytes win over the name/mime hint: files are often misnamed, especially from messengers.
    public fun detect(file: File, fileName: String? = null, mimeType: String? = null): DocumentFormat? {
        val head = file.readPrefix(1024)
        val read = head.size
        val hint = DocumentFormat.fromExtension(fileName?.substringAfterLast('.', "")) ?: DocumentFormat.fromMimeType(mimeType)
        return when {
            read >= 4 && containsPdfMagic(head) -> DocumentFormat.PDF
            read >= 4 && head.startsWith(ZIP_MAGIC) -> if (isWordZip(file)) DocumentFormat.DOCX else null
            read >= 8 && head.startsWith(CFB_MAGIC) -> DocumentFormat.DOC
            hint == DocumentFormat.CSV || hint == DocumentFormat.TXT -> hint
            looksLikeText(file) -> if (looksLikeCsv(file)) DocumentFormat.CSV else DocumentFormat.TXT
            else -> null
        }
    }

    private fun isWordZip(file: File): Boolean = runCatching {
        ZipFile(file).use { zip -> zip.getEntry("word/document.xml") != null || zip.getEntry("[Content_Types].xml")?.let { entry ->
            zip.getInputStream(entry).use { it.readBytes().decodeToString() }.contains("wordprocessingml")
        } == true }
    }.getOrDefault(false)

    private fun looksLikeText(file: File): Boolean {
        val sample = file.readPrefix(4096)
        if (sample.isEmpty()) return true
        val decoded = TextDecoding.decode(sample, sample.size)
        val bad = decoded.text.count { it == '\u0000' || it == '\uFFFD' || (it < ' ' && it !in "\r\n\t\u000C") }
        return bad <= decoded.text.length / 50
    }

    private fun looksLikeCsv(file: File): Boolean {
        val sample = TextDecoding.decode(file.readPrefix(16 * 1024)).text
        return CsvParser.detectDelimiter(sample) != null
    }

    // The spec allows garbage before the header within the first kilobyte.
    private fun containsPdfMagic(head: ByteArray) = (0..head.size - PDF_MAGIC.size).any { i -> PDF_MAGIC.indices.all { head[i + it] == PDF_MAGIC[it] } }

    private fun ByteArray.startsWith(prefix: ByteArray) = size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }

    private val PDF_MAGIC = "%PDF".toByteArray()
    private val ZIP_MAGIC = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
    private val CFB_MAGIC = byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(), 0xA1.toByte(), 0xB1.toByte(), 0x1A, 0xE1.toByte())
}
