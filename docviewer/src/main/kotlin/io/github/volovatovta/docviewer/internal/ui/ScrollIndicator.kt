package io.github.volovatovta.docviewer.internal.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.volovatovta.docviewer.DocumentViewerColors

// Pill shown while scrolling, e.g. "12 / 300".
@Composable
internal fun ScrollIndicator(
    text: String,
    visibleKey: Any,
    isScrolling: Boolean,
    colors: DocumentViewerColors,
    modifier: Modifier = Modifier,
) {
    val visible = rememberTransientVisibility(isScrolling, ScrollIndicatorSpec.HIDE_DELAY_MILLIS, visibleKey)
    AnimatedVisibility(visible, modifier, enter = OverlayFade.Enter, exit = OverlayFade.Exit) {
        Box(
            Modifier
                .background(colors.overlay, PillShape)
                .padding(
                    horizontal = ScrollIndicatorSpec.HorizontalPadding,
                    vertical = ScrollIndicatorSpec.VerticalPadding,
                ),
        ) {
            BasicText(
                text = text,
                style = TextStyle(
                    color = colors.onOverlay,
                    fontSize = ScrollIndicatorSpec.TextSize,
                    fontWeight = FontWeight.Medium,
                ),
            )
        }
    }
}

private object ScrollIndicatorSpec {
    const val HIDE_DELAY_MILLIS = 1400L
    val HorizontalPadding = 14.dp
    val VerticalPadding = 6.dp
    val TextSize = 13.sp
}
