package io.github.volovatovta.docviewer.core

public open class DocumentException(message: String, cause: Throwable? = null) : Exception(message, cause)

public class UnsupportedFormatException(message: String) : DocumentException(message)

public class PasswordProtectedException(public val wrongPassword: Boolean = false) :
    DocumentException(if (wrongPassword) "Wrong password" else "Document is password-protected")

public class CorruptedDocumentException(message: String, cause: Throwable? = null) : DocumentException(message, cause)
