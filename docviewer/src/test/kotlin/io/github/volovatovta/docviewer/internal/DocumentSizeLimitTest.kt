package io.github.volovatovta.docviewer.internal

import io.github.volovatovta.docviewer.core.model.DocumentFormat
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DocumentSizeLimitTest {

    private val megabyte = 1024L * 1024

    @Test
    fun `a typical 256 MB heap allows about 14 MB of CSV and 27 MB of text`() {
        val csv = DocumentSizeLimit.limitFor(DocumentFormat.CSV, null, 256 * megabyte, 40 * megabyte)!!
        val text = DocumentSizeLimit.limitFor(DocumentFormat.TXT, null, 256 * megabyte, 40 * megabyte)!!
        assertEquals(14.2, csv.toDouble() / megabyte, 0.1)
        assertEquals(25.6, text.toDouble() / megabyte, 0.1)
    }

    @Test
    fun `a busy heap lowers the limit`() {
        val idle = DocumentSizeLimit.limitFor(DocumentFormat.DOC, null, 256 * megabyte, 20 * megabyte)!!
        val busy = DocumentSizeLimit.limitFor(DocumentFormat.DOC, null, 256 * megabyte, 200 * megabyte)!!
        assertTrue(busy < idle / 2)
    }

    @Test
    fun `an explicit limit wins and PDF has none by default`() {
        assertEquals(5 * megabyte, DocumentSizeLimit.limitFor(DocumentFormat.PDF, 5 * megabyte, 256 * megabyte, 0))
        assertNull(DocumentSizeLimit.limitFor(DocumentFormat.PDF, null, 256 * megabyte, 0))
    }
}
