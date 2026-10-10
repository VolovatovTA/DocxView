package io.github.volovatovta.docviewer.core.docx

import io.github.volovatovta.docviewer.core.model.RunStyle
import io.github.volovatovta.docviewer.core.model.VerticalAlign
import kotlin.math.abs

// Resolves the cascade "document defaults → paragraph style → character style → direct formatting" to a RunStyle.
internal class RunFormatter(private val styles: StyleSheet) {

    fun effectiveProperties(paragraphStyleId: String?, direct: RunProperties): RunProperties {
        val paragraphRun = styles.resolveParagraph(paragraphStyleId).second
        val characterStyle = direct.styleId?.let(styles::resolveRunStyle)
        val styled = characterStyle?.let(paragraphRun::overlay) ?: paragraphRun
        return styled.overlay(direct)
    }

    fun style(properties: RunProperties): RunStyle = RunStyle(
        bold = properties.bold == true,
        italic = properties.italic == true,
        underline = properties.underline == true,
        strikethrough = properties.strike == true,
        color = properties.color,
        background = properties.highlight ?: properties.shading,
        relativeSize = relativeSize(properties.sizeHalfPoints),
        verticalAlign = properties.verticalAlign ?: VerticalAlign.Baseline,
        monospace = properties.monospace == true,
        allCaps = properties.caps == true,
        smallCaps = properties.smallCaps == true,
    )

    private fun relativeSize(sizeHalfPoints: Int?): Float? {
        val ratio = sizeHalfPoints?.let { it.toFloat() / styles.baseSizeHalfPoints } ?: return null
        return ratio.takeIf { abs(it - 1f) > SIZE_TOLERANCE }
    }

    private companion object {
        const val SIZE_TOLERANCE = 0.05f
    }
}
