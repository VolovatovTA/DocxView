package io.github.volovatovta.docviewer.internal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.volovatovta.docviewer.DocumentViewerColors
import io.github.volovatovta.docviewer.R

private object PasswordSpec {
    val FieldGap = 20.dp
    val ButtonGap = 16.dp
    val FieldMaxWidth = 320.dp
    val FieldShape = RoundedCornerShape(12.dp)
    val FieldBorder = 1.dp
    val FieldHorizontalPadding = 16.dp
    val FieldVerticalPadding = 14.dp
    val TextSize = 16.sp
    val ErrorColor = Color(0xFFE5484D)
}

@Composable
internal fun PasswordView(
    wrongPassword: Boolean,
    onSubmit: (String) -> Unit,
    colors: DocumentViewerColors,
    modifier: Modifier = Modifier,
) {
    // Deliberately not saveable: a password must not end up in the saved instance state.
    var password by remember { mutableStateOf("") }
    val submit = { if (password.isNotEmpty()) onSubmit(password) }
    StatusScreen(colors = colors, glyph = { DocumentGlyph(colors, locked = true) }, modifier = modifier) {
        StatusTitle(stringResource(R.string.dv_password_title), colors)
        StatusMessage(
            text = stringResource(if (wrongPassword) R.string.dv_password_wrong else R.string.dv_password_subtitle),
            color = if (wrongPassword) PasswordSpec.ErrorColor else colors.secondaryText,
        )
        Spacer(Modifier.height(PasswordSpec.FieldGap))
        PasswordField(password, { password = it }, wrongPassword, colors, onDone = submit)
        Spacer(Modifier.height(PasswordSpec.ButtonGap))
        PillButton(stringResource(R.string.dv_password_open), colors, enabled = password.isNotEmpty(), onClick = submit)
    }
}

@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    wrongPassword: Boolean,
    colors: DocumentViewerColors,
    onDone: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(focus) { focus.requestFocus() }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(color = colors.text, fontSize = PasswordSpec.TextSize),
        cursorBrush = SolidColor(colors.accent),
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = Modifier
            .focusRequester(focus)
            .widthIn(max = PasswordSpec.FieldMaxWidth)
            .fillMaxWidth(),
        decorationBox = { innerField ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(colors.page, PasswordSpec.FieldShape)
                    .border(
                        PasswordSpec.FieldBorder,
                        if (wrongPassword) PasswordSpec.ErrorColor else colors.tableBorder,
                        PasswordSpec.FieldShape,
                    )
                    .padding(
                        horizontal = PasswordSpec.FieldHorizontalPadding,
                        vertical = PasswordSpec.FieldVerticalPadding,
                    ),
            ) {
                if (value.isEmpty()) {
                    BasicText(
                        text = stringResource(R.string.dv_password_hint),
                        style = TextStyle(color = colors.secondaryText, fontSize = PasswordSpec.TextSize),
                    )
                }
                innerField()
            }
        },
    )
}
