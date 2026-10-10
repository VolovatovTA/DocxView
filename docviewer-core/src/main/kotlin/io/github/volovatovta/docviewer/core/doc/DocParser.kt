package io.github.volovatovta.docviewer.core.doc

import io.github.volovatovta.docviewer.core.CorruptedDocumentException
import io.github.volovatovta.docviewer.core.PasswordProtectedException
import io.github.volovatovta.docviewer.core.PlainTextParser
import io.github.volovatovta.docviewer.core.SizeNormalizer
import io.github.volovatovta.docviewer.core.TextDecoding
import io.github.volovatovta.docviewer.core.UnsupportedFormatException
import io.github.volovatovta.docviewer.core.model.FlowDocument
import java.io.File

// Word 97-2003 binary format ([MS-DOC]): text via the piece table, formatting via CHPX/PAPX formatted disk pages.
public object DocParser {

    private const val MAX_FILE_SIZE = 256L * 1024 * 1024
    private const val DEFAULT_BODY_SIZE_HALF_POINTS = 20

    private object StreamName {
        const val WORD_DOCUMENT = "WordDocument"
        const val DATA = "Data"
        const val ENCRYPTED_PACKAGE = "EncryptedPackage"
        const val ENCRYPTION_INFO = "EncryptionInfo"
    }

    public fun parse(file: File): FlowDocument {
        if (file.length() > MAX_FILE_SIZE) throw UnsupportedFormatException("DOC file is too large")
        val container = CompoundFile(file.readBytes())
        val word = wordDocumentStream(container)
        val fib = Fib(word)
        if (fib.isEncrypted) throw PasswordProtectedException()
        return if (fib.isWord97OrLater) parseWord97(container, word, fib) else parseLegacy(word, fib)
    }

    // Encrypted OOXML files are OLE containers too, recognizable by their encryption streams.
    private fun wordDocumentStream(container: CompoundFile): ByteArray {
        val encrypted = container.find(StreamName.ENCRYPTED_PACKAGE) ?: container.find(StreamName.ENCRYPTION_INFO)
        if (encrypted != null) throw PasswordProtectedException()
        return container.stream(StreamName.WORD_DOCUMENT)
            ?: throw UnsupportedFormatException("OLE file is not a Word document")
    }

    private fun parseWord97(container: CompoundFile, word: ByteArray, fib: Fib): FlowDocument {
        val table = container.stream(fib.tableStreamName) ?: throw CorruptedDocumentException("Missing table stream")
        val text = PieceTable.read(table, fib.range(FibEntry.Clx)).decode(word, fib.mainTextLength)
        val characterRuns = FormattedDiskPages.readCharacterRuns(word, table, fib.range(FibEntry.PlcfBteChpx))
        val paragraphRuns = FormattedDiskPages.readParagraphRuns(word, table, fib.range(FibEntry.PlcfBtePapx))
        // Lists only decorate paragraphs, so a damaged list table must not cost the whole document.
        val lists = runCatching {
            ListTables.read(table, fib.range(FibEntry.PlfLst), fib.range(FibEntry.PlfLfo))
        }.getOrNull()
        val images = DocImageStore(container.stream(StreamName.DATA) ?: ByteArray(0))
        val builder = DocumentBuilder(
            text = text,
            characterRuns = characterRuns,
            paragraphRuns = paragraphRuns,
            lists = lists,
            bodySizeHalfPoints = characterRuns.mostCommonSize(text.offsets),
            images = images,
        )
        return FlowDocument(SizeNormalizer.normalize(builder.build()), title = null, images = images)
    }

    // Word 6/95 has no piece table in the Word 97 sense, but keeps its text contiguously between fcMin and fcMac.
    private fun parseLegacy(word: ByteArray, fib: Fib): FlowDocument {
        val range = fib.legacyText
        if (range.offset <= 0 || !range.isInside(word)) {
            throw UnsupportedFormatException("Unsupported Word 6/95 document")
        }
        val bytes = word.copyOfRange(range.offset, range.end)
        val decoded = String(bytes, TextDecoding.guessSingleByteCharset(bytes, bytes.size))
        return PlainTextParser.parse(buildString { decoded.forEach { char -> legacyCharacter(char)?.let(::append) } })
    }

    // Paragraph, cell and page marks become line breaks; other control characters carry no text.
    private fun legacyCharacter(char: Char): Char? = when (char) {
        '\r', '\u0007', '\u000B', '\u000C' -> '\n'
        '\t' -> char
        else -> char.takeIf { it >= ' ' }
    }

    // Body text is whatever size most characters use; runs without an explicit size inherit it.
    private fun PropertyRuns<CharacterProperties>.mostCommonSize(offsets: IntArray): Int =
        offsets.asSequence()
            .mapNotNull { at(it).sizeHalfPoints }
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
            ?: DEFAULT_BODY_SIZE_HALF_POINTS
}
