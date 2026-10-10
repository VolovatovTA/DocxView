package io.github.volovatovta.docviewer.core.format

import io.github.volovatovta.docviewer.core.TextDecoding
import io.github.volovatovta.docviewer.core.binary.startsWith
import io.github.volovatovta.docviewer.core.model.DocumentFormat
import io.github.volovatovta.docviewer.core.ole.CompoundFile
import io.github.volovatovta.docviewer.core.ooxml.OpcPackage
import io.github.volovatovta.docviewer.core.readPrefix
import org.xml.sax.SAXException
import java.io.File
import java.io.IOException
import java.util.zip.ZipFile

/**
 * A file being identified. Each view of it is read once, and only if a signature asks for it,
 * so cheap checks on the header never pay for unpacking a ZIP or an OLE container.
 */
internal class ProbedFile(val file: File, val hint: DocumentFormat?) {

    val header: ByteArray by lazy { file.readPrefix(HEADER_BYTES) }

    val isZip: Boolean get() = header.startsWith(ZIP_SIGNATURE)

    val isOle: Boolean get() = header.startsWith(CompoundFile.SIGNATURE)

    /** Entry names of a ZIP package, lowercased. */
    val zipEntries: Set<String> by lazy {
        readZip(fallback = emptySet()) { zip -> zip.entries().asSequence().map { it.name.lowercase() }.toSet() }
    }

    /**
     * The folder of the part an Office package opens with, such as `word` or `xl`, read from its relationships;
     * the folder of the expected part when the package has none. Embedded objects never change it, so an
     * Excel file holding a Word document is still Excel.
     */
    val mainFolder: String? by lazy {
        val declared = readZip(fallback = null) { zip -> OpcPackage(zip).mainPartPath() }
        declared?.lowercase()?.substringBefore('/', missingDelimiterValue = "")
    }

    /** Stream names of an OLE container, lowercased. */
    val oleStreams: Set<String> by lazy {
        if (!isOle) emptySet() else runCatching { CompoundFile(file.readBytes()).streamNames }.getOrDefault(emptySet())
    }

    /** A password-protected Office file; only its name tells a Word document from a workbook. */
    val isEncryptedOffice: Boolean get() = CompoundFile.ENCRYPTED_PACKAGE.lowercase() in oleStreams

    /** The start of the file as text, or null when it reads like binary data. */
    val text: String? by lazy {
        val sample = file.readPrefix(TEXT_SAMPLE_BYTES)
        val decoded = TextDecoding.decode(sample).text
        decoded.takeIf { it.count(::isBinaryCharacter) <= it.length * MAX_BINARY_CHARACTER_SHARE }
    }

    // Anything that is not a readable archive is simply not one of the formats that are.
    private fun <T> readZip(fallback: T, read: (ZipFile) -> T): T {
        if (!isZip) return fallback
        return try {
            ZipFile(file).use(read)
        } catch (_: IOException) {
            fallback
        } catch (_: IllegalArgumentException) {
            fallback
        } catch (_: SAXException) {
            fallback
        }
    }

    private fun isBinaryCharacter(char: Char): Boolean =
        char == '\u0000' || char == '�' || (char < ' ' && char !in TEXT_CONTROL_CHARACTERS)

    private companion object {
        const val HEADER_BYTES = 1024
        const val TEXT_SAMPLE_BYTES = 16 * 1024
        const val MAX_BINARY_CHARACTER_SHARE = 0.02
        const val TEXT_CONTROL_CHARACTERS = "\r\n\t\u000C"
        val ZIP_SIGNATURE = bytes(0x50, 0x4B, 0x03, 0x04)

        fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }
    }
}
