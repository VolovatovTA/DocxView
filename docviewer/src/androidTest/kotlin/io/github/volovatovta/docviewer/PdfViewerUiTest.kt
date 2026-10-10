package io.github.volovatovta.docviewer

import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.volovatovta.docviewer.core.CorruptedDocumentException
import io.github.volovatovta.docviewer.internal.PasswordUnsupportedException
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail

// End-to-end scenarios through the public Compose API with real touch input.
@RunWith(AndroidJUnit4::class)
class PdfViewerUiTest {

    @get:Rule
    val rule = createComposeRule()

    private val state = DocumentViewerState()

    @Test
    fun book41Pages() = fullScenario(TestPdf.own().single())

    @Test
    fun twoThousandPages() = fullScenario(generated("gen-2000-pages"))

    @Test
    fun giantPoster() = fullScenario(generated("gen-giant-poster"))

    @Test
    fun receipt() = fullScenario(generated("gen-receipt"))

    @Test
    fun mixedPageSizes() = fullScenario(generated("gen-mixed-sizes"))

    @Test
    fun vectorHeavy() = fullScenario(generated("gen-vector-heavy"))

    @Test
    fun tracemonkeyFromCorpus() {
        val pdf = TestPdf.corpus().firstOrNull { it.name == "tracemonkey" }
        assumeTrue("run tools/fetch-test-pdfs.sh first", pdf != null)
        fullScenario(pdf!!)
    }

    @Test
    fun passwordFlow() {
        show(TestPdf.encrypted())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            awaitStatus { it is DocumentStatus.PasswordRequired }
            assertEquals(false, (state.status as DocumentStatus.PasswordRequired).wrongPassword)
            rule.runOnIdle { state.submitPassword("0000") }
            awaitStatus { it is DocumentStatus.PasswordRequired && it.wrongPassword }
            shot("encrypted", "wrong-password")
            rule.runOnIdle { state.submitPassword("1234") }
            awaitStatus { it is DocumentStatus.Ready }
            assertTrue(state.pageCount > 0)
            shot("encrypted", "unlocked")
        } else {
            awaitStatus { it is DocumentStatus.Failed }
            assertIs<PasswordUnsupportedException>((state.status as DocumentStatus.Failed).error)
            shot("encrypted", "unsupported")
        }
    }

    @Test
    fun garbageFailsCleanly() {
        show(TestPdf.garbage())
        awaitStatus { it is DocumentStatus.Failed }
        assertIs<CorruptedDocumentException>((state.status as DocumentStatus.Failed).error)
        shot("garbage", "error")
    }

    @Test
    fun truncatedFileDoesNotCrash() {
        show(TestPdf.truncated())
        awaitStatus { it is DocumentStatus.Ready || it is DocumentStatus.Failed }
        val status = state.status
        log("truncated-book", "status=${status::class.simpleName} pages=${state.pageCount}")
        if (status is DocumentStatus.Ready) {
            rule.runOnIdle { state.goToPage(state.pageCount - 1) }
            rule.waitForIdle()
            settle()
        }
        shot("truncated-book", "result")
    }

    private fun fullScenario(pdf: TestPdf) {
        show(pdf)
        awaitStatus { it is DocumentStatus.Ready || it is DocumentStatus.Failed }
        (state.status as? DocumentStatus.Failed)?.let { fail("${pdf.name} failed to open: ${it.error}") }
        val count = state.pageCount
        settle()
        shot(pdf.name, "open")

        rule.onRoot().performTouchInput { swipeUp(durationMillis = 150) }
        rule.waitForIdle()

        rule.onRoot().performTouchInput { doubleClick(center) }
        rule.waitUntil(5_000) { state.zoom > 1.5f }
        rule.onRoot().performTouchInput { swipe(center, center + Offset(-300f, -400f), 300) }
        settle()
        shot(pdf.name, "zoomed")

        val before = state.zoom
        rule.onRoot().performTouchInput {
            pinch(center - Offset(40f, 0f), center - Offset(240f, 0f), center + Offset(40f, 0f), center + Offset(240f, 0f), 400)
        }
        rule.waitForIdle()
        assertTrue(state.zoom > before, "pinch-out did not zoom in (${state.zoom} <= $before)")
        assertTrue(state.zoom <= DocumentViewerConfig().maxZoom + 0.01f)

        rule.onRoot().performTouchInput { doubleClick(center) }
        rule.waitUntil(5_000) { state.zoom < 1.01f }

        rule.runOnIdle { state.goToPage(count - 1) }
        rule.waitUntil(20_000) { state.currentPage >= count - 2 }
        settle()
        shot(pdf.name, "last-page")

        val word = pdf.searchWord
        if (word != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            rule.runOnIdle { state.search(word) }
            rule.waitUntil(120_000) { !state.isSearching && state.searchResultCount >= pdf.minHits }
            rule.runOnIdle { state.nextSearchResult() }
            settle()
            shot(pdf.name, "search")
        }
        log(pdf.name, "ui=ok pages=$count zoomAfterPinch=$before->${state.zoom} searchHits=${state.searchResultCount}")
    }

    private fun show(pdf: TestPdf) {
        val file = pdf.file()
        rule.setContent { DocumentViewer(DocumentSource.FromFile(file), Modifier.fillMaxSize(), state = state) }
    }

    private fun awaitStatus(condition: (DocumentStatus) -> Boolean) = rule.waitUntil(90_000) { condition(state.status) }

    // Tiles render after a short debounce on the PDF thread, which the test clock does not drive by itself.
    private fun settle() {
        rule.mainClock.advanceTimeBy(400)
        Thread.sleep(700)
        rule.waitForIdle()
    }

    private fun shot(name: String, step: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val dir = File(TestPdf.context.getExternalFilesDir(null), "screens").apply { mkdirs() }
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        File(dir, "api${Build.VERSION.SDK_INT}-$name-$step.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun log(name: String, message: String) = Log.i("DVTEST", "UI|api=${Build.VERSION.SDK_INT}|pdf=$name|$message")

    private fun generated(name: String) = TestPdf.generated().single { it.name == name }
}
