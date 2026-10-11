package io.github.volovatovta.docviewer.core.format

import io.github.volovatovta.docviewer.core.binary.startsWith
import io.github.volovatovta.docviewer.core.model.DocumentFormat

/** PDF is only recognized here; drawing its pages takes a platform renderer, not a parser. */
internal object PdfSignature : FormatSignature {
    override val format = DocumentFormat.PDF

    private val signature = "%PDF-".toByteArray()

    // Readers accept garbage before the header within the first kilobyte; a text file merely quoting a header
    // ("%PDF-1.7" in notes) is still text, so for those only a header at the very start counts.
    override fun recognizes(file: ProbedFile): Boolean {
        val header = file.header
        val at = header.indices.firstOrNull { offset -> isHeaderAt(header, offset) } ?: return false
        return at == 0 || file.hint != DocumentFormat.TXT && file.hint != DocumentFormat.CSV
    }

    // "%PDF-" and the first digit of the version.
    private fun isHeaderAt(bytes: ByteArray, offset: Int): Boolean {
        val version = bytes.getOrNull(offset + signature.size)?.toInt()?.toChar()
        return bytes.startsWith(signature, at = offset) && version in '0'..'9'
    }
}
