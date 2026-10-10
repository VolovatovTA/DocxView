package io.github.volovatovta.docviewer.internal.pdf

import android.os.Build
import java.io.File
import java.io.RandomAccessFile

// Pre-Android 9 pdfium stays broken after any failed load, so files it would reject never reach it.
internal object PdfPreflight {
    private const val HEADER = "%PDF-"
    private const val END_OF_FILE = "%%EOF"
    private const val ENCRYPTION_KEY = "/Encrypt"
    private const val HEADER_WINDOW = 1024
    private const val TRAILER_WINDOW = 2048
    private const val SCAN_CHUNK = 64 * 1024

    val isNeeded: Boolean get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.P

    // Truncated downloads and non-PDF files named .pdf fail the header or end-of-file marker check.
    fun looksComplete(file: File): Boolean = RandomAccessFile(file, "r").use { input ->
        val length = input.length()
        val head = input.readWindow(0, minOf(length, HEADER_WINDOW.toLong()).toInt())
        val tailStart = (length - TRAILER_WINDOW).coerceAtLeast(0)
        HEADER in head && END_OF_FILE in input.readWindow(tailStart, (length - tailStart).toInt())
    }

    // Even an empty user password is a failed load first, so any encryption dictionary is a stop sign.
    fun isEncrypted(file: File): Boolean = RandomAccessFile(file, "r").use { input ->
        val overlap = ENCRYPTION_KEY.length - 1
        var start = 0L
        while (start < input.length()) {
            val size = minOf(SCAN_CHUNK.toLong(), input.length() - start).toInt()
            if (ENCRYPTION_KEY in input.readWindow(start, size)) return@use true
            start += size - overlap
            if (size <= overlap) break
        }
        false
    }

    private fun RandomAccessFile.readWindow(start: Long, size: Int): String {
        val bytes = ByteArray(size)
        seek(start)
        readFully(bytes)
        return String(bytes, Charsets.ISO_8859_1)
    }
}
