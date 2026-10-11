package io.github.volovatovta.docviewer.internal

import android.content.ComponentCallbacks2
import android.content.res.Configuration
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

// Rendered bitmaps are redrawn on demand, so they are the first thing given back when the system runs low.
@Composable
internal fun EvictOnLowMemory(cache: LruCache<*, *>) {
    val context = LocalContext.current.applicationContext
    DisposableEffect(cache, context) {
        val callbacks = LowMemoryCallbacks(cache)
        context.registerComponentCallbacks(callbacks)
        onDispose { context.unregisterComponentCallbacks(callbacks) }
    }
}

private class LowMemoryCallbacks(private val cache: LruCache<*, *>) : ComponentCallbacks2 {
    // RUNNING_LOW is deprecated from API 34 because it is no longer sent; older releases still send it.
    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) cache.evictAll()
    }

    override fun onConfigurationChanged(newConfig: Configuration) = Unit

    @Deprecated("Deprecated in Java")
    override fun onLowMemory() = cache.evictAll()
}
