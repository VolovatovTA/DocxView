package io.github.volovatovta.docviewer

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import io.github.volovatovta.docviewer.core.model.DocumentFormat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

// The sales workbook of the sample app, saved as .xlsx and as .xls: sheets, tabs and search across sheets.
@RunWith(Parameterized::class)
class WorkbookUiTest(private val fileName: String) {

    @get:Rule
    val rule = viewerComposeRule()

    private val state = DocumentViewerState()

    @Test
    fun tabsSwitchSheetsAndSearchFindsCellsOnAnySheet() {
        rule.setContent { DocumentViewer(DocumentSource.FromAsset(fileName), Modifier.fillMaxSize(), state = state) }
        rule.waitUntil(OPEN_TIMEOUT_MS) { state.status.isSettled() }
        assertIs<DocumentStatus.Ready>(state.status)
        assertTrue(state.info?.format in setOf(DocumentFormat.XLSX, DocumentFormat.XLS))
        rule.onNodeWithText("Summary").assertIsSelected()
        rule.onNodeWithText("Products").assertIsNotSelected()

        rule.onNodeWithText("Products").performClick()
        rule.onNodeWithText("Products").assertIsSelected()
        rule.onAllNodesWithText("Accessories").onFirst().assertExists()
        // Excel shows thousands separators on every platform; Android's DecimalFormat does not by default.
        rule.onNodeWithText("$1,299.00").assertExists()
        shot("products")

        // "SO-1200" is an order number: it exists only on the Orders sheet.
        rule.runOnIdle { state.search("SO-1200") }
        rule.waitUntil(OPEN_TIMEOUT_MS) { !state.isSearching && state.searchResultCount > 0 }
        assertEquals(1, state.searchResultCount)
        rule.waitUntil(OPEN_TIMEOUT_MS) { rule.onAllNodesWithText("SO-1200").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Orders").assertIsSelected()
        shot("search")
    }

    private fun DocumentStatus.isSettled() = this !is DocumentStatus.Loading && this !is DocumentStatus.Idle

    private fun shot(step: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val dir = File(TestPdf.context.getExternalFilesDir(null), "screens").apply { mkdirs() }
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        File(dir, "api${Build.VERSION.SDK_INT}-${fileName.replace('.', '-')}-$step.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private companion object {
        const val OPEN_TIMEOUT_MS = 30_000L

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun files() = listOf("workbook.xlsx", "workbook.xls")
    }
}
