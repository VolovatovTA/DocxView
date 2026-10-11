package io.github.volovatovta.docviewer

import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.volovatovta.docviewer.core.DocumentTooLargeException
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

// Large text and CSV files are read from disk while shown; the heap must not grow with the file.
@RunWith(AndroidJUnit4::class)
class StreamingUiTest {

    @get:Rule
    val rule = viewerComposeRule()

    private val state = DocumentViewerState()

    @Test
    fun bigTextFile() {
        val file = generated("big.txt") { out ->
            repeat(TEXT_LINES) { line ->
                val text = if (line == TEXT_LINES - 10) "the needle line" else "Line $line of a long log, строка $line."
                out.write("$text\r\n".toByteArray())
                if (line % 5_000 == 0) out.write(("long paragraph words ".repeat(400) + "\n").toByteArray())
            }
        }
        scenario(file, "needle", "big-txt")
    }

    @Test
    fun bigCsvFile() {
        val file = generated("big.csv") { out ->
            out.write("id;name;comment\n".toByteArray())
            repeat(CSV_ROWS) { row ->
                val name = if (row == CSV_ROWS - 5) "needle" else "name $row"
                out.write("$row;$name;\"quoted, with ; and\nline break\"\n".toByteArray())
            }
        }
        scenario(file, "needle", "big-csv")
        assertTrue(state.pageCount > 500_000, "no row limit when streamed")
    }

    @Test
    fun tooLargeShowsTheErrorScreen() {
        val file = generated("utf16.txt") { out -> out.write("﻿hello\nworld".toByteArray(Charsets.UTF_16LE)) }
        val config = DocumentViewerConfig(maxDocumentSizeBytes = 4)
        rule.setContent { DocumentViewer(DocumentSource.FromFile(file), Modifier.fillMaxSize(), state, config) }
        rule.waitUntil(OPEN_TIMEOUT_MS) { state.status is DocumentStatus.Failed }
        assertIs<DocumentTooLargeException>((state.status as DocumentStatus.Failed).error)
        shot("too-large", "error")
    }

    private fun scenario(file: File, word: String, name: String) {
        val heapBefore = usedHeapMb()
        rule.setContent { DocumentViewer(DocumentSource.FromFile(file), Modifier.fillMaxSize(), state = state) }
        rule.waitUntil(OPEN_TIMEOUT_MS) { state.status.isSettled() }
        assertIs<DocumentStatus.Ready>(state.status)
        rule.onRoot().performTouchInput { swipeUp(durationMillis = 150) }
        rule.waitForIdle()
        rule.runOnIdle { state.goToPage(state.pageCount - 1) }
        // The current item is the first visible one, so the end of the list leaves a screenful below it.
        rule.waitUntil(OPEN_TIMEOUT_MS) { state.currentPage >= state.pageCount - SCREENFUL }
        rule.runOnIdle { state.search(word) }
        rule.waitUntil(OPEN_TIMEOUT_MS) { !state.isSearching && state.searchResultCount > 0 }
        rule.runOnIdle { state.nextSearchResult() }
        rule.waitForIdle()
        Thread.sleep(500)
        rule.waitForIdle()
        shot(name, "search")
        val grown = usedHeapMb() - heapBefore
        val sizeMb = file.length() / BYTES_PER_MB
        Log.i(
            "DVTEST",
            "STREAM|api=${Build.VERSION.SDK_INT}|file=$name|sizeMb=$sizeMb|items=${state.pageCount}|" +
                "heapGrowthMb=$grown|hits=${state.searchResultCount}",
        )
        assertEquals(1, state.searchResultCount)
        assertTrue(grown < sizeMb / 2, "heap grew by $grown MB for a $sizeMb MB file")
    }

    private fun DocumentStatus.isSettled() = this !is DocumentStatus.Loading && this !is DocumentStatus.Idle

    private fun generated(name: String, write: (java.io.OutputStream) -> Unit): File =
        File(TestPdf.context.cacheDir, name).apply { outputStream().buffered().use(write) }

    // Collecting first makes the reading reflect what is retained, not garbage awaiting collection.
    @Suppress("ExplicitGarbageCollectionCall")
    private fun usedHeapMb(): Long {
        Runtime.getRuntime().gc()
        Thread.sleep(200)
        val runtime = Runtime.getRuntime()
        return (runtime.totalMemory() - runtime.freeMemory()) / BYTES_PER_MB
    }

    private fun shot(name: String, step: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val dir = File(TestPdf.context.getExternalFilesDir(null), "screens").apply { mkdirs() }
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        File(dir, "api${Build.VERSION.SDK_INT}-$name-$step.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private companion object {
        const val TEXT_LINES = 600_000
        const val CSV_ROWS = 600_000
        const val OPEN_TIMEOUT_MS = 120_000L
        const val BYTES_PER_MB = 1024L * 1024
        const val SCREENFUL = 200
    }
}
