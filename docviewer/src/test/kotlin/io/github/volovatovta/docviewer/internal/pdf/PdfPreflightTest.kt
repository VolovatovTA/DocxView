package io.github.volovatovta.docviewer.internal.pdf

import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PdfPreflightTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val body = "1 0 obj << /Type /Catalog >> endobj\n".repeat(200)

    private fun pdf(content: String) = temp.newFile().apply { writeBytes(content.toByteArray(Charsets.ISO_8859_1)) }

    @Test
    fun `a complete document passes`() {
        assertTrue(PdfPreflight.looksComplete(pdf("%PDF-1.7\n$body%%EOF\n")))
    }

    @Test
    fun `a truncated download is turned away`() {
        val whole = "%PDF-1.7\n$body%%EOF\n"
        assertFalse(PdfPreflight.looksComplete(pdf(whole.substring(0, whole.length * 2 / 5))))
    }

    @Test
    fun `a file that is not a PDF is turned away`() {
        assertFalse(PdfPreflight.looksComplete(pdf("PK\u0003\u0004 not a pdf $body%%EOF")))
    }

    @Test
    fun `garbage after the end marker is tolerated like pdfium does`() {
        assertTrue(PdfPreflight.looksComplete(pdf("%PDF-1.4\n$body%%EOF\n\n\u0000\u0000")))
    }

    @Test
    fun `encryption dictionaries are found anywhere in the file`() {
        assertTrue(PdfPreflight.isEncrypted(pdf("%PDF-1.7\n$body trailer << /Encrypt 5 0 R >>\n%%EOF")))
        assertFalse(PdfPreflight.isEncrypted(pdf("%PDF-1.7\n$body trailer << /Root 1 0 R >>\n%%EOF")))
    }

    @Test
    fun `an encryption key split across scan chunks is still found`() {
        val chunk = 64 * 1024
        val padding = "x".repeat(chunk - "%PDF-1.7\n".length - 3)
        assertTrue(PdfPreflight.isEncrypted(pdf("%PDF-1.7\n$padding/Encrypt 9 0 R\n%%EOF")))
    }
}
