package io.github.volovatovta.docviewer

/** Lifecycle of the document shown by a [DocumentViewer]. */
public sealed interface DocumentStatus {
    public data object Idle : DocumentStatus

    // progress is 0..1 while the file is being downloaded or copied, null when unknown.
    public data class Loading(val progress: Float? = null) : DocumentStatus

    public data class Ready(val info: DocumentInfo) : DocumentStatus

    public data class PasswordRequired(val wrongPassword: Boolean) : DocumentStatus

    public data class Failed(val error: Throwable) : DocumentStatus
}
