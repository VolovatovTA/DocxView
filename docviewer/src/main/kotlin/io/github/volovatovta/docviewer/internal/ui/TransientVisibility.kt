package io.github.volovatovta.docviewer.internal.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

// Visible while active and for a short while after; a new restartKey restarts that grace period.
@Composable
internal fun rememberTransientVisibility(active: Boolean, hideDelayMillis: Long, restartKey: Any? = null): Boolean {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(active, restartKey) {
        if (active) {
            visible = true
        } else if (visible) {
            delay(hideDelayMillis)
            visible = false
        }
    }
    return visible
}
