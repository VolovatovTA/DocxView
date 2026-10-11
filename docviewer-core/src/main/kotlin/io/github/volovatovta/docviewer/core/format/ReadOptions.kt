package io.github.volovatovta.docviewer.core.format

import io.github.volovatovta.docviewer.core.CsvOptions
import io.github.volovatovta.docviewer.core.DocumentTooLargeException
import java.util.Objects

/** How [DocumentReader] reads a document; built with [Builder] so new options stay compatible. */
public class ReadOptions private constructor(
    public val csv: CsvOptions,
    /** Heap a document parsed in memory may take; larger ones fail with [DocumentTooLargeException]. */
    public val memoryBudgetBytes: Long?,
    /**
     * Most bytes to parse (unpacked XML for DOCX) before refusing a file, replacing the estimate from
     * [memoryBudgetBytes]; the same limit as the viewer's `DocumentViewerConfig.maxDocumentSizeBytes`.
     */
    public val maxDocumentSizeBytes: Long?,
) {

    /** The default options: CSV detected from the file, no memory budget and no size limit. */
    public constructor() : this(CsvOptions(), memoryBudgetBytes = null, maxDocumentSizeBytes = null)

    /** A builder holding these options, to change some of them. */
    public fun newBuilder(): Builder = Builder(this)

    override fun equals(other: Any?): Boolean = other is ReadOptions && csv == other.csv &&
        memoryBudgetBytes == other.memoryBudgetBytes && maxDocumentSizeBytes == other.maxDocumentSizeBytes

    override fun hashCode(): Int = Objects.hash(csv, memoryBudgetBytes, maxDocumentSizeBytes)

    override fun toString(): String = "ReadOptions(csv=$csv, memoryBudgetBytes=$memoryBudgetBytes, " +
        "maxDocumentSizeBytes=$maxDocumentSizeBytes)"

    /** Options for a [ReadOptions]; anything not set keeps its default. */
    public class Builder() {
        private var csv: CsvOptions = CsvOptions()
        private var memoryBudgetBytes: Long? = null
        private var maxDocumentSizeBytes: Long? = null

        internal constructor(options: ReadOptions) : this() {
            csv = options.csv
            memoryBudgetBytes = options.memoryBudgetBytes
            maxDocumentSizeBytes = options.maxDocumentSizeBytes
        }

        public fun csv(value: CsvOptions): Builder = apply { csv = value }

        public fun memoryBudgetBytes(value: Long?): Builder = apply { memoryBudgetBytes = value }

        public fun maxDocumentSizeBytes(value: Long?): Builder = apply { maxDocumentSizeBytes = value }

        public fun build(): ReadOptions = ReadOptions(csv, memoryBudgetBytes, maxDocumentSizeBytes)
    }
}

/** Kotlin shorthand: `ReadOptions { memoryBudgetBytes(64L shl 20) }`. */
public inline fun ReadOptions(block: ReadOptions.Builder.() -> Unit): ReadOptions =
    ReadOptions.Builder().apply(block).build()
