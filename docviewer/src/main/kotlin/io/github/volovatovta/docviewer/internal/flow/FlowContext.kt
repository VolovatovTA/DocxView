package io.github.volovatovta.docviewer.internal.flow

import android.util.LruCache
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.TextUnit
import io.github.volovatovta.docviewer.DocumentViewerColors
import io.github.volovatovta.docviewer.core.FlowMatch
import io.github.volovatovta.docviewer.core.model.ImageStore
import io.github.volovatovta.docviewer.core.model.Paragraph
import io.github.volovatovta.docviewer.core.model.Table

// Everything a block needs to render itself, passed down instead of a long parameter list.
@Immutable
internal class FlowContext(
    val colors: DocumentViewerColors,
    val textSize: TextUnit,
    val scale: Float,
    val search: SearchHighlights,
    val onLink: (String) -> Unit,
    val images: FlowImages,
    // Survives list items leaving the screen: each table is measured once and its rows share one scroll.
    val tables: MutableMap<Table, TableRuntime>,
)

@Immutable
internal class SearchHighlights(private val byParagraph: Map<Paragraph, List<FlowMatch>>, val current: FlowMatch?) {
    fun matchesIn(paragraph: Paragraph): List<FlowMatch>? = byParagraph[paragraph]
}

internal class FlowImages(val store: ImageStore, val cache: LruCache<String, ImageBitmap>)

internal val LocalInHeaderRow = staticCompositionLocalOf { false }
