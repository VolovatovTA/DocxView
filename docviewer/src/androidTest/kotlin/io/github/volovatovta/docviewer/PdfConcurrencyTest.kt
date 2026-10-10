package io.github.volovatovta.docviewer

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.volovatovta.docviewer.core.CorruptedDocumentException
import io.github.volovatovta.docviewer.internal.pdf.PdfDocument
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.concurrent.thread
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

// Platform pdfium before Android 9 stayed broken for the whole process after one failed load.
@RunWith(AndroidJUnit4::class)
class PdfConcurrencyTest {

    private val book = TestPdf.asset("book-41.pdf").file()
    private val poster = GeneratedPdfs.named("gen-mixed-sizes").file()

    @Test
    fun openingAfterADocumentThatFailedToOpen() {
        val garbage = TestPdf.garbage().file()
        repeat(5) {
            assertFailsWith<CorruptedDocumentException> { PdfDocument.open(garbage, password = null) }
            PdfDocument.open(book, password = null).use { document ->
                assertNotNull(runBlocking { document.render(0, 200, 280) })
            }
        }
    }

    @Test
    fun switchingBetweenDocumentsQuickly() {
        repeat(25) {
            val previous = PdfDocument.open(book, password = null)
            val next = PdfDocument.open(poster, password = null)
            previous.close()
            assertNotNull(runBlocking { next.render(0, 200, 280) })
            next.close()
        }
    }

    @Test
    fun openingAndRenderingFromSeveralThreads() {
        val failures = mutableListOf<Throwable>()
        val workers = List(4) { worker ->
            thread {
                runCatching {
                    repeat(8) {
                        PdfDocument.open(if (worker % 2 == 0) book else poster, password = null).use { document ->
                            assertNotNull(runBlocking { document.render(it % document.pageCount, 150, 210) })
                        }
                    }
                }.onFailure { synchronized(failures) { failures += it } }
            }
        }
        workers.forEach { it.join() }
        failures.firstOrNull()?.let { throw it }
    }
}
