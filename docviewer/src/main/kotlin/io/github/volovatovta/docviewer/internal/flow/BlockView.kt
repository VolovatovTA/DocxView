package io.github.volovatovta.docviewer.internal.flow

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.unit.dp
import io.github.volovatovta.docviewer.core.model.Block
import io.github.volovatovta.docviewer.core.model.Divider
import io.github.volovatovta.docviewer.core.model.PageBreak
import io.github.volovatovta.docviewer.core.model.Paragraph
import io.github.volovatovta.docviewer.core.model.Table

private object BlockSpec {
    val DividerWidth = 96.dp
    val DividerThickness = 1.dp
    val DividerVerticalPadding = 16.dp
    val PageBreakHeight = 32.dp
    val PageBreakStroke = 1.dp
    val PageBreakDash = 6.dp
    val PageBreakGap = 5.dp
}

@Composable
internal fun FlowItemView(item: FlowItem, context: FlowContext, modifier: Modifier = Modifier) {
    when (item) {
        is FlowItem.ParagraphPart -> ParagraphPieceView(item.piece, context, modifier)
        is FlowItem.TableRowItem -> TableRowView(item.table, item.row, context, modifier)
        is FlowItem.WholeBlock -> BlockView(item.block, context, modifier)
    }
}

@Composable
internal fun BlockView(
    block: Block,
    context: FlowContext,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    when (block) {
        is Paragraph -> ParagraphView(block, context, modifier, compact)
        is Table -> TableView(block, context, modifier)
        PageBreak -> PageBreakView(context.colors.tableBorder, modifier)
        Divider -> DividerView(context.colors.tableBorder, modifier)
    }
}

@Composable
private fun DividerView(color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .padding(vertical = BlockSpec.DividerVerticalPadding)
            .width(BlockSpec.DividerWidth)
            .height(BlockSpec.DividerThickness)
            .background(color),
    )
}

@Composable
private fun PageBreakView(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(BlockSpec.PageBreakHeight)) {
        val middle = size.height / 2
        drawLine(
            color = color,
            start = Offset(0f, middle),
            end = Offset(size.width, middle),
            strokeWidth = BlockSpec.PageBreakStroke.toPx(),
            pathEffect = PathEffect.dashPathEffect(
                floatArrayOf(BlockSpec.PageBreakDash.toPx(), BlockSpec.PageBreakGap.toPx()),
            ),
        )
    }
}
