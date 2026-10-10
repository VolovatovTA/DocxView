package io.github.volovatovta.docviewer.internal.pdf

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

private object PageSizeLoading {
    // Small batches let page rendering interleave with measuring on the single PDF thread.
    const val BATCH = 32
    const val PUBLISH_INTERVAL_MS = 500L
}

// Pages start with the first page's size; real sizes arrive in the background and are published only if they differ.
@Composable
internal fun rememberPageSizes(document: PdfDocument): List<PdfPageSize> {
    var published by remember(document) { mutableStateOf(List(document.pageCount) { document.firstPageSize }) }
    LaunchedEffect(document) {
        val known = ArrayList(published)
        var pending = false
        var lastPublished = 0L
        for (start in 1 until document.pageCount step PageSizeLoading.BATCH) {
            val batch = start until minOf(start + PageSizeLoading.BATCH, document.pageCount)
            val measured = document.measurePages(batch) ?: return@LaunchedEffect
            measured.forEachIndexed { index, size ->
                if (size != known[start + index]) {
                    known[start + index] = size
                    pending = true
                }
            }
            val now = SystemClock.uptimeMillis()
            if (pending && now - lastPublished >= PageSizeLoading.PUBLISH_INTERVAL_MS) {
                published = known.toList()
                pending = false
                lastPublished = now
            }
        }
        if (pending) published = known.toList()
    }
    return published
}
