package io.github.volovatovta.docviewer.core.model

public enum class DocumentFormat(public val extensions: Set<String>, public val mimeTypes: Set<String>) {
    PDF(
        extensions = setOf("pdf"),
        mimeTypes = setOf("application/pdf"),
    ),
    DOCX(
        extensions = setOf("docx", "docm", "dotx", "dotm"),
        mimeTypes = setOf(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.template",
            "application/vnd.ms-word.document.macroenabled.12",
        ),
    ),
    DOC(
        extensions = setOf("doc", "dot"),
        mimeTypes = setOf("application/msword", "application/vnd.ms-word"),
    ),
    CSV(
        extensions = setOf("csv", "tsv", "tab"),
        mimeTypes = setOf("text/csv", "text/comma-separated-values", "text/tab-separated-values", "application/csv"),
    ),
    TXT(
        extensions = setOf("txt", "text", "log", "md", "json", "xml"),
        mimeTypes = setOf("text/plain", "text/markdown", "application/json"),
    ),
    XLSX(
        extensions = setOf("xlsx", "xlsm", "xltx", "xltm"),
        mimeTypes = setOf(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.template",
            "application/vnd.ms-excel.sheet.macroenabled.12",
        ),
    ),
    XLS(
        extensions = setOf("xls", "xlt"),
        mimeTypes = setOf("application/vnd.ms-excel", "application/msexcel", "application/x-msexcel"),
    ),
    ;

    /** Spreadsheets and CSV open as sheets of cells. */
    public val isSpreadsheet: Boolean get() = this == XLSX || this == XLS || this == CSV

    public companion object {
        public fun fromExtension(extension: String?): DocumentFormat? {
            val normalized = extension?.lowercase()?.removePrefix(".") ?: return null
            return entries.firstOrNull { normalized in it.extensions }
        }

        public fun fromMimeType(mimeType: String?): DocumentFormat? {
            val normalized = mimeType?.lowercase()?.substringBefore(';')?.trim() ?: return null
            return entries.firstOrNull { normalized in it.mimeTypes }
        }
    }
}
