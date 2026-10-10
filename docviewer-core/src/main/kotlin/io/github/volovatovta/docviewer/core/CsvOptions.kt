package io.github.volovatovta.docviewer.core

public enum class CsvHeaderMode { Auto, FirstRow, None }

public data class CsvOptions(
    // null = detect from the content.
    val delimiter: Char? = null,
    val header: CsvHeaderMode = CsvHeaderMode.Auto,
    val maxRows: Int = DEFAULT_MAX_ROWS,
) {
    private companion object {
        const val DEFAULT_MAX_ROWS = 500_000
    }
}
