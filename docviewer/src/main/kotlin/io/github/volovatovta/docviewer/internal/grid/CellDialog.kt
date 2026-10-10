package io.github.volovatovta.docviewer.internal.grid

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import io.github.volovatovta.docviewer.DocumentViewerColors
import io.github.volovatovta.docviewer.R
import io.github.volovatovta.docviewer.internal.ui.PillButton

private object CellDialogSpec {
    val MaxWidth = 520.dp
    val MaxValueHeight = 420.dp
    val CornerRadius = 20.dp
    val Padding = 24.dp
    val TitleGap = 10.dp
    val ButtonGap = 20.dp
    val TitleSize = 13.sp
    val ValueSize = 16.sp
}

// Full text of a cell that the grid truncates to a few lines.
@Composable
internal fun CellDialog(title: String, value: String, colors: DocumentViewerColors, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .widthIn(max = CellDialogSpec.MaxWidth)
                .fillMaxWidth()
                .background(colors.page, RoundedCornerShape(CellDialogSpec.CornerRadius))
                .padding(CellDialogSpec.Padding),
        ) {
            BasicText(
                text = title,
                style = TextStyle(
                    color = colors.secondaryText,
                    fontSize = CellDialogSpec.TitleSize,
                    fontWeight = FontWeight.Medium,
                ),
            )
            Spacer(Modifier.height(CellDialogSpec.TitleGap))
            SelectionContainer(
                Modifier
                    .heightIn(max = CellDialogSpec.MaxValueHeight)
                    .verticalScroll(rememberScrollState()),
            ) {
                BasicText(value, style = TextStyle(color = colors.text, fontSize = CellDialogSpec.ValueSize))
            }
            Spacer(Modifier.height(CellDialogSpec.ButtonGap))
            Box(Modifier.align(Alignment.End)) {
                PillButton(stringResource(R.string.dv_close), colors, onClick = onDismiss)
            }
        }
    }
}
