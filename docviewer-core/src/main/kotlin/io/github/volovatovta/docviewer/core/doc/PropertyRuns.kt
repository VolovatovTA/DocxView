package io.github.volovatovta.docviewer.core.doc

// Formatting runs keyed by byte offset in the WordDocument stream; text is walked in order, so the last hit is cached.
internal class PropertyRuns<T>(runs: List<Run<T>>, private val default: T) {

    class Run<T>(val start: Int, val end: Int, val properties: T)

    private val runs = runs.sortedBy { it.start }
    private var lastHit = 0

    fun at(offset: Int): T {
        val cached = runs.getOrNull(lastHit)
        if (cached != null && offset >= cached.start && offset < cached.end) return cached.properties
        val index = runs.binarySearch { run ->
            when {
                run.end <= offset -> -1
                run.start > offset -> 1
                else -> 0
            }
        }
        if (index < 0) return default
        lastHit = index
        return runs[index].properties
    }
}
