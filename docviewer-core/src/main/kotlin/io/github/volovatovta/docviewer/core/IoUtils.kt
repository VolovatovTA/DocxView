package io.github.volovatovta.docviewer.core

import java.io.File
import java.io.InputStream

// InputStream.readNBytes is API 33 on Android, so the core module keeps its own helpers.
internal fun InputStream.readUpTo(limit: Int): ByteArray {
    val buffer = ByteArray(limit)
    var total = 0
    while (total < limit) {
        val n = read(buffer, total, limit - total)
        if (n < 0) break
        total += n
    }
    return if (total == limit) buffer else buffer.copyOf(total)
}

internal fun File.readPrefix(limit: Int): ByteArray = inputStream().use { it.readUpTo(limit) }
