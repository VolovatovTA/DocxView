package io.github.volovatovta.docviewer.core

public open class DocumentException(message: String, cause: Throwable? = null) : Exception(message, cause)

public open class UnsupportedFormatException(message: String) : DocumentException(message)

public class PasswordProtectedException(public val wrongPassword: Boolean = false) :
    DocumentException(if (wrongPassword) "Wrong password" else "Document is password-protected")

public class CorruptedDocumentException(message: String, cause: Throwable? = null) : DocumentException(message, cause)

/** The document would need more memory to open than [limitBytes] allows; sizes are in bytes to parse. */
public class DocumentTooLargeException(public val sizeBytes: Long, public val limitBytes: Long) :
    DocumentException("Document needs $sizeBytes bytes to parse, the limit is $limitBytes")
