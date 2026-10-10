package io.github.volovatovta.docviewer.internal.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut

internal object OverlayFade {
    const val IN_MILLIS = 120
    const val OUT_MILLIS = 400
    val Enter = fadeIn(tween(IN_MILLIS))
    val Exit = fadeOut(tween(OUT_MILLIS))
}
