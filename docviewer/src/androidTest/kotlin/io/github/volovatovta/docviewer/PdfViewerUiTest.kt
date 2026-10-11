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

    // Gestures keep the default unconfined test dispatcher: input injection in this Compose version expects
    // pointer handlers to run at once.
    @get:Rule
    val rule = createComposeRule()

    private val state = DocumentViewerState()

    @Test
    fun book41Pages() = fullScenario(TestPdf.own().single())

    @Test
    fun twoThousandPages() = fullScenario(GeneratedPdfs.named("gen-2000-pages"))

    @Test
    fun giantPoster() = fullScenario(GeneratedPdfs.named("gen-giant-poster"))

    @Test
    fun receipt() = fullScenario(GeneratedPdfs.named("gen-receipt"))

    @Test
    fun mixedPageSizes() = fullScenario(GeneratedPdfs.named("gen-mixed-sizes"))

    @Test
    fun vectorHeavy() = fullScenario(GeneratedPdfs.named("gen-vector-heavy"))

    @Test
    fun tracemonkeyFromCorpus() {
        val pdf = TestPdf.corpus().firstOrNull { it.name == "tracemonkey" }
        assumeTrue("run tools/fetch-test-pdfs.sh first", pdf != null)
        pdf?.let(::fullScenario)
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
        log("truncated-book", "status=${state.status::class.simpleName} pages=${state.pageCount}")
        if (state.status is DocumentStatus.Ready) {
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
        settle()
        shot(pdf.name, "open")
        val zoomAfterPinch = exerciseGestures(pdf)
        goToLastPage(pdf)
        searchIfSupported(pdf)
        val hits = state.searchResultCount
        log(pdf.name, "ui=ok pages=${state.pageCount} zoomAfterPinch=$zoomAfterPinch searchHits=$hits")
    }

    // Fling, double tap, pan, pinch and double tap back, checking the zoom after each step.
    private fun exerciseGestures(pdf: TestPdf): Float {
        rule.onRoot().performTouchInput { swipeUp(durationMillis = 150) }
        rule.waitForIdle()
        rule.onRoot().performTouchInput { doubleClick(center) }
        rule.waitUntil(GESTURE_TIMEOUT_MS) { state.zoom > 1.5f }
        rule.onRoot().performTouchInput { swipe(center, center + Offset(-300f, -400f), 300) }
        settle()
        shot(pdf.name, "zoomed")

        val before = state.zoom
        rule.onRoot().performTouchInput {
            pinch(
                start0 = center - Offset(40f, 0f),
                end0 = center - Offset(240f, 0f),
                start1 = center + Offset(40f, 0f),
                end1 = center + Offset(240f, 0f),
                durationMillis = 400,
            )
        }
        rule.waitForIdle()
        val afterPinch = state.zoom
        assertTrue(afterPinch > before, "pinch-out did not zoom in ($afterPinch <= $before)")
        assertTrue(afterPinch <= DocumentViewerConfig().maxZoom + 0.01f)

        rule.onRoot().performTouchInput { doubleClick(center) }
        rule.waitUntil(GESTURE_TIMEOUT_MS) { state.zoom < 1.01f }
        return afterPinch
    }

    private fun goToLastPage(pdf: TestPdf) {
        val last = state.pageCount - 1
        rule.runOnIdle { state.goToPage(last) }
        rule.waitUntil(NAVIGATION_TIMEOUT_MS) { state.currentPage >= last - 1 }
        settle()
        shot(pdf.name, "last-page")
    }

    private fun searchIfSupported(pdf: TestPdf) {
        val word = pdf.searchWord ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return
        rule.runOnIdle { state.search(word) }
        rule.waitUntil(SEARCH_TIMEOUT_MS) { !state.isSearching && state.searchResultCount >= pdf.minHits }
        rule.runOnIdle { state.nextSearchResult() }
        settle()
        shot(pdf.name, "search")
    }

    private fun show(pdf: TestPdf) {
        val file = pdf.file()
        rule.setContent { DocumentViewer(DocumentSource.FromFile(file), Modifier.fillMaxSize(), state = state) }
    }

    private fun awaitStatus(condition: (DocumentStatus) -> Boolean) =
        rule.waitUntil(OPEN_TIMEOUT_MS) { condition(state.status) }

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
        File(dir, "api${Build.VERSION.SDK_INT}-$name-$step.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun log(name: String, message: String) {
        Log.i("DVTEST", "UI|api=${Build.VERSION.SDK_INT}|pdf=$name|$message")
    }

    private companion object {
        const val GESTURE_TIMEOUT_MS = 5_000L
        const val NAVIGATION_TIMEOUT_MS = 20_000L
        const val OPEN_TIMEOUT_MS = 90_000L
        const val SEARCH_TIMEOUT_MS = 120_000L
    }
}
