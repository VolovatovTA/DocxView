package io.github.volovatovta.docviewer

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertTrue

// Every opened document holds files (PdfRenderer, ZIP archives, text read from disk) and may start threads;
// switching between documents many times must give all of them back.
@RunWith(AndroidJUnit4::class)
class ResourceLeakTest {

    @get:Rule
    val rule = createComposeRule()

    private val state = DocumentViewerState()

    @Test
    fun switchingDocumentsLeavesNoOpenFilesOrThreads() {
        val sources = listOf(
            DocumentSource.FromAsset("book-41.pdf"),
            DocumentSource.FromAsset("report.docx"),
            DocumentSource.FromAsset("workbook.xlsx"),
            DocumentSource.FromAsset("workbook.xls"),
            DocumentSource.FromFile(generated("lines.txt") { index -> "Line $index of a log" }),
            DocumentSource.FromFile(generated("rows.csv") { index -> "$index;name $index;\"quoted, text\"" }),
        )
        var source by mutableStateOf<DocumentSource?>(null)
        rule.setContent { DocumentViewer(source, Modifier.fillMaxSize(), state = state) }

        fun cycle() = sources.forEach { next ->
            rule.runOnIdle { source = next }
            rule.waitUntil(TIMEOUT_MS) { state.status is DocumentStatus.Ready }
        }

        cycle()
        rule.runOnIdle { source = null }
        val filesBefore = settled(::openFiles)
        val threadsBefore = settled(Thread::activeCount)

        repeat(CYCLES) { cycle() }
        rule.runOnIdle { source = null }

        val filesAfter = settled(::openFiles)
        val threadsAfter = settled(Thread::activeCount)
        assertTrue(filesAfter - filesBefore <= TOLERANCE, "open files grew from $filesBefore to $filesAfter")
        assertTrue(threadsAfter - threadsBefore <= TOLERANCE, "threads grew from $threadsBefore to $threadsAfter")
    }

    // Closing runs on the PDF thread and finalizers; give both a moment before counting.
    private fun settled(count: () -> Int): Int {
        rule.waitForIdle()
        repeat(GC_ROUNDS) {
            Runtime.getRuntime().gc()
            Thread.sleep(SETTLE_MS)
        }
        return count()
    }

    private fun openFiles(): Int = File("/proc/self/fd").list()?.size ?: 0

    private fun generated(name: String, line: (Int) -> String): File =
        File(TestPdf.context.cacheDir, name).apply {
            bufferedWriter().use { writer -> repeat(LINES) { writer.appendLine(line(it)) } }
        }

    private companion object {
        const val CYCLES = 8
        const val LINES = 20_000
        const val TOLERANCE = 2
        const val GC_ROUNDS = 3
        const val SETTLE_MS = 300L
        const val TIMEOUT_MS = 30_000L
    }
}
