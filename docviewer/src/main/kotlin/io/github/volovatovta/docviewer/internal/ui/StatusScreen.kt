package io.github.volovatovta.docviewer.internal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.volovatovta.docviewer.DocumentViewerColors

// Shared frame of the error and password screens: a glyph, a title, a message and an action, centered.
@Composable
internal fun StatusScreen(
    colors: DocumentViewerColors,
    glyph: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(StatusScreenSpec.ContentPadding),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        glyph()
        Spacer(Modifier.height(StatusScreenSpec.GlyphGap))
        content()
    }
}

@Composable
internal fun StatusTitle(text: String, colors: DocumentViewerColors, modifier: Modifier = Modifier) {
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(
            color = colors.text,
            fontSize = StatusScreenSpec.TitleSize,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        ),
    )
}

@Composable
internal fun StatusMessage(text: String, color: Color, modifier: Modifier = Modifier) {
    BasicText(
        text = text,
        modifier = modifier
            .padding(top = StatusScreenSpec.TitleGap)
            .widthIn(max = StatusScreenSpec.MessageMaxWidth),
        style = TextStyle(color = color, fontSize = StatusScreenSpec.MessageSize, textAlign = TextAlign.Center),
    )
}

internal object StatusScreenSpec {
    val ContentPadding = 32.dp
    val GlyphGap = 20.dp
    val TitleGap = 8.dp
    val ActionGap = 24.dp
    val MessageMaxWidth = 360.dp
    val TitleSize = 18.sp
    val MessageSize = 14.sp
}
