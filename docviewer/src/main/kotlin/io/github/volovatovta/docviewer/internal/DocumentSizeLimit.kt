package io.github.volovatovta.docviewer.internal

import io.github.volovatovta.docviewer.core.DocumentTooLargeException
import io.github.volovatovta.docviewer.core.ParsingCost
import io.github.volovatovta.docviewer.core.model.DocumentFormat
import java.io.File

// Refuses documents whose parsing would not fit in this device's heap, before a single byte is parsed.
internal object DocumentSizeLimit {
    // Parsing may take half of the heap; the host app and rendering keep the rest.
    private const val HEAP_DIVISOR = 2
    private const val USABLE_SHARE_OF_FREE_HEAP = 0.8

    fun check(file: File, format: DocumentFormat, override: Long?) {
        val runtime = Runtime.getRuntime()
        val usedHeap = runtime.totalMemory() - runtime.freeMemory()
        val limit = limitFor(format, override, runtime.maxMemory(), usedHeap) ?: return
        val size = ParsingCost.bytesToParse(file, format)
        if (size > limit) throw DocumentTooLargeException(size, limit)
    }

    // Null means no limit: PDF pages are rendered one by one straight from the file.
    fun limitFor(format: DocumentFormat, override: Long?, maxHeap: Long, usedHeap: Long): Long? {
        if (override != null) return override
        val peakPerByte = ParsingCost.peakMemoryPerByte(format)
        if (peakPerByte == 0) return null
        val freeHeap = ((maxHeap - usedHeap).coerceAtLeast(0) * USABLE_SHARE_OF_FREE_HEAP).toLong()
        return minOf(maxHeap / HEAP_DIVISOR, freeHeap) / peakPerByte
    }
}
