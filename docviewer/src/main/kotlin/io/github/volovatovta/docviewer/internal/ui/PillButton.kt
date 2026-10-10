package io.github.volovatovta.docviewer.internal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.volovatovta.docviewer.DocumentViewerColors

private object PillButtonSpec {
    val HorizontalPadding = 28.dp
    val VerticalPadding = 12.dp
    val TextSize = 15.sp
    const val DISABLED_ALPHA = 0.4f
}

internal val PillShape = RoundedCornerShape(percent = 50)

@Composable
internal fun PillButton(
    text: String,
    colors: DocumentViewerColors,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier
            .alpha(if (enabled) 1f else PillButtonSpec.DISABLED_ALPHA)
            .background(colors.accent, PillShape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = PillButtonSpec.HorizontalPadding, vertical = PillButtonSpec.VerticalPadding),
    ) {
        BasicText(
            text = text,
            style = TextStyle(
                color = colors.onAccent,
                fontSize = PillButtonSpec.TextSize,
                fontWeight = FontWeight.Medium,
            ),
        )
    }
}
