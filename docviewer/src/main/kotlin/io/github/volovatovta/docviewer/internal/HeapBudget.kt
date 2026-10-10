package io.github.volovatovta.docviewer.internal

import kotlinx.coroutines.sync.Mutex

// The heap a document may take while it is parsed in memory: half of the maximum, and no more than is free.
internal object HeapBudget {
    private const val MAX_SHARE_DIVISOR = 2
    private const val USABLE_SHARE_OF_FREE_HEAP = 0.8

    // One open at a time, so each budget is measured after the previous document took its heap.
    val openGate = Mutex()

    fun forParsing(): Long {
        val runtime = Runtime.getRuntime()
        return of(maxHeap = runtime.maxMemory(), usedHeap = runtime.totalMemory() - runtime.freeMemory())
    }

    fun of(maxHeap: Long, usedHeap: Long): Long {
        val free = ((maxHeap - usedHeap).coerceAtLeast(0) * USABLE_SHARE_OF_FREE_HEAP).toLong()
        return minOf(maxHeap / MAX_SHARE_DIVISOR, free)
    }
}
