package io.github.volovatovta.docviewer.internal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.volovatovta.docviewer.DocumentViewerColors

private object BannerSpec {
    val HorizontalPadding = 16.dp
    val VerticalPadding = 10.dp
    val TextSize = 13.sp
}

@Composable
internal fun Banner(text: String, colors: DocumentViewerColors, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .background(colors.overlay)
            .padding(horizontal = BannerSpec.HorizontalPadding, vertical = BannerSpec.VerticalPadding),
    ) {
        BasicText(text, style = TextStyle(color = colors.onOverlay, fontSize = BannerSpec.TextSize))
    }
}
