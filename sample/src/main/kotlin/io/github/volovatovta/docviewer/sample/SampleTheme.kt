package io.github.volovatovta.docviewer.sample

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private object Accent {
    val Light = Color(0xFF2F6FEB)
    val Dark = Color(0xFF7AA7FF)
}

// Spacing shared by the sample screens.
internal object SampleDimens {
    val ScreenPadding = 16.dp
    val ItemSpacing = 10.dp
    val SmallGap = 6.dp
    val SectionGap = 8.dp
    val ListBottomPadding = 24.dp
}

@Composable
internal fun SampleTheme(content: @Composable () -> Unit) {
    val colorScheme = if (isSystemInDarkTheme()) {
        darkColorScheme(primary = Accent.Dark)
    } else {
        lightColorScheme(primary = Accent.Light)
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
