package io.github.volovatovta.docviewer.internal.pdf

import android.graphics.Bitmap
import android.util.LruCache

internal class BitmapCache private constructor(maxBytes: Int) : LruCache<String, Bitmap>(maxBytes) {

    override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount

    companion object {
        private const val HEAP_FRACTION = 8
        private const val MAX_BYTES = 64L * 1024 * 1024

        fun sizedForHeap(): BitmapCache =
            BitmapCache((Runtime.getRuntime().maxMemory() / HEAP_FRACTION).coerceAtMost(MAX_BYTES).toInt())
    }
}
