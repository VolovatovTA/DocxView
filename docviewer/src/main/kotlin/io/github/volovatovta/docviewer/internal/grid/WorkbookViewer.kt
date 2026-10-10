package io.github.volovatovta.docviewer.internal.grid

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.volovatovta.docviewer.DocumentViewerColors
import io.github.volovatovta.docviewer.DocumentViewerConfig
import io.github.volovatovta.docviewer.DocumentViewerState
import io.github.volovatovta.docviewer.core.model.Workbook
import io.github.volovatovta.docviewer.internal.flow.BindController

private object SheetTabSpec {
    val BarHeight = 44.dp
    val TabPadding = 16.dp
    val IndicatorHeight = 2.dp
    val TextSize = 14.sp
}

/** The sheet on screen, with the workbook's tabs below it as in Excel; a single sheet shows no tabs. */
@Composable
internal fun WorkbookViewer(
    workbook: Workbook,
    state: DocumentViewerState,
    config: DocumentViewerConfig,
    colors: DocumentViewerColors,
    modifier: Modifier = Modifier,
) {
    val controller = remember(workbook) { WorkbookController(workbook) }
    BindController(state, controller)
    Column(modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            key(controller.sheet) {
                SheetGrid(workbook.sheets[controller.sheet].table, controller.sheet, controller, state, config, colors)
            }
        }
        if (workbook.sheets.size > 1) {
            SheetTabs(workbook.sheets.map { it.name }, controller.sheet, colors, onSelect = { controller.sheet = it })
        }
    }
}

@Composable
private fun SheetTabs(
    names: List<String>,
    selected: Int,
    colors: DocumentViewerColors,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(SheetTabSpec.BarHeight)
            .background(colors.tableHeader)
            .horizontalScroll(rememberScrollState()),
    ) {
        names.forEachIndexed { index, name ->
            SheetTab(name, isSelected = index == selected, colors, onClick = { onSelect(index) })
        }
    }
}

@Composable
private fun SheetTab(
    name: String,
    isSelected: Boolean,
    colors: DocumentViewerColors,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxSize()
            .background(if (isSelected) colors.page else colors.tableHeader)
            .drawBehind {
                if (isSelected) {
                    val stroke = SheetTabSpec.IndicatorHeight.toPx()
                    drawLine(colors.accent, Offset(0f, stroke / 2), Offset(size.width, stroke / 2), stroke)
                }
            }
            .semantics { selected = isSelected }
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = SheetTabSpec.TabPadding),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = name,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(
                color = if (isSelected) colors.accent else colors.secondaryText,
                fontSize = SheetTabSpec.TextSize,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            ),
        )
    }
}
