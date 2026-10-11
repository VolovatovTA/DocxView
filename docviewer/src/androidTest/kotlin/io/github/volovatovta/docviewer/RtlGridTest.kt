package io.github.volovatovta.docviewer

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertTrue

// A sheet keeps its columns left to right in an Arabic or Hebrew UI, and a drag still moves them with the finger.
@RunWith(AndroidJUnit4::class)
class RtlGridTest {

    @get:Rule
    val rule = viewerComposeRule()

    @Test
    fun swipingLeftRevealsTheNextColumnsInRightToLeftLayout() {
        rule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                DocumentViewer(DocumentSource.FromAsset("workbook.xlsx"), Modifier.size(NARROW, TALL))
            }
        }
        rule.waitUntil(TIMEOUT_MS) { rule.onAllNodesWithText("Region").fetchSemanticsNodes().isNotEmpty() }
        val before = rule.onNodeWithText("Region").getBoundsInRoot().left

        rule.onRoot().performTouchInput { swipeLeft() }
        rule.waitForIdle()

        val after = rule.onNodeWithText("Region").getBoundsInRoot().left
        assertTrue(after < before, "Region moved from $before to $after")
    }

    private companion object {
        const val TIMEOUT_MS = 30_000L
        val NARROW = 200.dp
        val TALL = 400.dp
    }
}
