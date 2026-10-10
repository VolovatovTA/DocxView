package io.github.volovatovta.docviewer

import android.os.Build
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import leakcanary.DetectLeaksAfterTestSuccess
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertIs

// What a host app sees when Android destroys the screen: a rotation, or the process killed in the background.
@RunWith(AndroidJUnit4::class)
class StateRestorationTest {

    @get:Rule
    val rule = createComposeRule()

    private val restoration = StateRestorationTester(rule)
    private lateinit var state: DocumentViewerState

    private fun show(source: DocumentSource) = restoration.setContent {
        state = rememberDocumentViewerState()
        DocumentViewer(source, Modifier.fillMaxSize(), state = state)
    }

    private fun awaitStatus(condition: (DocumentStatus) -> Boolean) =
        rule.waitUntil(TIMEOUT_MS) { condition(state.status) }

    @Test
    fun workbookKeepsTheSheetAndTheRowAfterProcessDeath() {
        show(DocumentSource.FromAsset("workbook.xlsx"))
        awaitStatus { it is DocumentStatus.Ready }
        rule.onNodeWithText("Orders").performClick()
        rule.runOnIdle { state.goToPage(ORDERS_ROW) }
        rule.waitUntil(TIMEOUT_MS) { state.currentPage == ORDERS_ROW }

        restoration.emulateSavedInstanceStateRestore()

        awaitStatus { it is DocumentStatus.Ready }
        rule.onNodeWithText("Orders").assertIsSelected()
        rule.waitUntil(TIMEOUT_MS) { state.currentPage == ORDERS_ROW }
        val restoredRow = "SO-${FIRST_ORDER + ORDERS_ROW}"
        rule.waitUntil(TIMEOUT_MS) { rule.onAllNodesWithText(restoredRow).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun pdfKeepsThePageAndTheZoomAfterProcessDeath() {
        show(DocumentSource.FromAsset("book-41.pdf"))
        awaitStatus { it is DocumentStatus.Ready }
        rule.runOnIdle {
            state.goToPage(PDF_PAGE)
            state.zoomIn()
        }
        rule.waitUntil(TIMEOUT_MS) { state.currentPage == PDF_PAGE && state.zoom > 1f }
        val zoom = state.zoom

        restoration.emulateSavedInstanceStateRestore()

        awaitStatus { it is DocumentStatus.Ready }
        rule.waitUntil(TIMEOUT_MS) { state.currentPage == PDF_PAGE }
        assertEquals(zoom, state.zoom, ZOOM_TOLERANCE)
    }

    // A password is never written to the saved state, so the restored screen asks for it again.
    @Test
    fun aPasswordIsAskedForAgainAfterProcessDeath() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM)
        show(DocumentSource.FromAsset("encrypted-1234.pdf"))
        awaitStatus { it is DocumentStatus.PasswordRequired }
        rule.runOnIdle { state.submitPassword("1234") }
        awaitStatus { it is DocumentStatus.Ready }

        restoration.emulateSavedInstanceStateRestore()

        awaitStatus { it is DocumentStatus.PasswordRequired }
        assertIs<DocumentStatus.PasswordRequired>(state.status)
    }

    private companion object {
        const val TIMEOUT_MS = 30_000L
        const val ORDERS_ROW = 120
        const val FIRST_ORDER = 1001
        const val PDF_PAGE = 20
        const val ZOOM_TOLERANCE = 0.01f
    }
}

// A real Activity recreated as on rotation; LeakCanary fails the test if the old one is still reachable.
@RunWith(AndroidJUnit4::class)
class RotationTest {

    private val rule = createAndroidComposeRule<ViewerTestActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(DetectLeaksAfterTestSuccess()).around(rule)

    @Test
    fun rotatingKeepsTheSheetAndLeaksNothing() {
        rule.waitUntil(TIMEOUT_MS) { rule.onAllNodesWithText("Orders").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Products").performClick()

        repeat(ROTATIONS) {
            rule.activityRule.scenario.recreate()
            rule.waitUntil(TIMEOUT_MS) { rule.onAllNodesWithText("Products").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithText("Products").assertIsSelected()
            rule.waitUntil(TIMEOUT_MS) { rule.onAllNodesWithText("Accessories").fetchSemanticsNodes().isNotEmpty() }
        }
    }

    private companion object {
        const val TIMEOUT_MS = 30_000L
        const val ROTATIONS = 3
    }
}
