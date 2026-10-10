package io.github.volovatovta.docviewer.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private object SampleCardSpec {
    val CardShape = RoundedCornerShape(18.dp)
    val BadgeShape = RoundedCornerShape(12.dp)
    val BadgeSize = 48.dp
    val ContentPadding = 16.dp
    val BadgeGap = 16.dp
}

@Composable
internal fun SampleCard(sample: Sample, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = SampleCardSpec.CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(SampleCardSpec.ContentPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FormatBadge(sample.badge, sample.color)
            Spacer(Modifier.width(SampleCardSpec.BadgeGap))
            Column(Modifier.weight(1f)) {
                Text(sample.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = sample.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun FormatBadge(label: String, color: Color) {
    Box(
        modifier = Modifier.size(SampleCardSpec.BadgeSize).background(color, SampleCardSpec.BadgeShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
    }
}
