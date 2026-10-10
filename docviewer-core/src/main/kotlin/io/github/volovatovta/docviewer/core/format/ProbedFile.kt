package io.github.volovatovta.docviewer.core.format

import io.github.volovatovta.docviewer.core.TextDecoding
import io.github.volovatovta.docviewer.core.binary.startsWith
import io.github.volovatovta.docviewer.core.model.DocumentFormat
import io.github.volovatovta.docviewer.core.ole.CompoundFile
import io.github.volovatovta.docviewer.core.readPrefix
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * A file being identified. Each view of it is read once, and only if a signature asks for it,
 * so cheap checks on the header never pay for unpacking a ZIP or an OLE container.
 */
internal class ProbedFile(val file: File, val hint: DocumentFormat?) {

    val header: ByteArray by lazy { file.readPrefix(HEADER_BYTES) }

    val isZip: Boolean get() = header.startsWith(ZIP_SIGNATURE)

    val isOle: Boolean get() = header.startsWith(OLE_SIGNATURE)

    /** Entry names of a ZIP package, lowercased. */
    val zipEntries: Set<String> by lazy {
        readZip(fallback = emptySet()) { zip -> zip.entries().asSequence().map { it.name.lowercase() }.toSet() }
    }

    /** The package's [Content_Types].xml, which names the kind of Office document inside. */
    val contentTypes: String by lazy {
        readZip(fallback = "") { zip -> zip.getEntry(CONTENT_TYPES)?.let { zip.readText(it) }.orEmpty() }
    }

    /** Stream names of an OLE container, lowercased. */
    val oleStreams: Set<String> by lazy {
        if (!isOle) emptySet() else runCatching { CompoundFile(file.readBytes()).streamNames }.getOrDefault(emptySet())
    }

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
        }
    }

    private fun ZipFile.readText(entry: ZipEntry): String = getInputStream(
        entry
    ).use { it.readBytes().decodeToString() }

    private fun isBinaryCharacter(char: Char): Boolean =
        char == '\u0000' || char == '�' || (char < ' ' && char !in TEXT_CONTROL_CHARACTERS)

    private companion object {
        const val HEADER_BYTES = 1024
        const val TEXT_SAMPLE_BYTES = 16 * 1024
        const val MAX_BINARY_CHARACTER_SHARE = 0.02
        const val TEXT_CONTROL_CHARACTERS = "\r\n\t\u000C"
        const val CONTENT_TYPES = "[Content_Types].xml"
        val ZIP_SIGNATURE = bytes(0x50, 0x4B, 0x03, 0x04)
        val OLE_SIGNATURE = bytes(0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1)

        fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }
    }
}
