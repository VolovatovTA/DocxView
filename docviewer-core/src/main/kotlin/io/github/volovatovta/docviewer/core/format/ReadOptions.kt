package io.github.volovatovta.docviewer.core.format

import io.github.volovatovta.docviewer.core.CsvOptions
import io.github.volovatovta.docviewer.core.DocumentTooLargeException

/** How [DocumentReader] reads a document. */
public data class ReadOptions(
    val csv: CsvOptions = CsvOptions(),
    /** Heap a document parsed in memory may take; larger ones fail with [DocumentTooLargeException]. */
    val memoryBudgetBytes: Long? = null,
    /** A fixed limit on the bytes to parse that replaces the estimate from [memoryBudgetBytes]. */
    val maxBytesToParse: Long? = null,
)
