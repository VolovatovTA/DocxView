package io.github.volovatovta.docviewer.internal

import io.github.volovatovta.docviewer.core.UnsupportedFormatException
import java.io.IOException

internal class PasswordUnsupportedException(val isPdf: Boolean, cause: Throwable? = null) :
    UnsupportedFormatException(if (isPdf) PDF_MESSAGE else OFFICE_MESSAGE) {
    init {
        cause?.let(::initCause)
    }

    private companion object {
        const val PDF_MESSAGE = "Password-protected PDF requires Android 15+"
        const val OFFICE_MESSAGE = "Password-protected Office documents are not supported"
    }
}

internal class NetworkException(message: String, cause: Throwable? = null) : IOException(message, cause)
