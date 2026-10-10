package io.github.volovatovta.docviewer.core.docx

import io.github.volovatovta.docviewer.core.model.ParagraphBorders
import io.github.volovatovta.docviewer.core.model.ParagraphStyle

// Resolves paragraph properties through the style sheet and numbers list items as they appear.
internal class ParagraphFormatter(
    private val styles: StyleSheet,
    private val numbering: Numbering
) {

    fun properties(direct: ParagraphProperties): ParagraphProperties =
        styles.resolveParagraph(direct.styleId).first.overlay(direct)

    // Word's spacing is zero unless a style or the paragraph sets it; direct indents win over the list level's.
    fun style(properties: ParagraphProperties, direct: ParagraphProperties): ParagraphStyle {
        val marker = properties.numberingId?.let {
            numbering.next(
                numberingId = it,
                level = properties.listLevel ?: 0
            )
        }
        val indent = Indent.of(properties, direct, marker)
        return ParagraphStyle(
            alignment = properties.alignment ?: ParagraphStyle.Default.alignment,
            headingLevel = styles.headingLevel(properties.styleId, properties.outlineLevel),
            indentStartPt = indent.startPt,
            firstLineIndentPt = indent.firstLinePt,
            spaceBeforePt = OoxmlUnits.twipsToPoints(properties.spacingBefore ?: 0),
            spaceAfterPt = OoxmlUnits.twipsToPoints(properties.spacingAfter ?: 0),
            listMarker = marker?.text?.takeIf { it.isNotEmpty() },
            listLevel = marker?.level ?: 0,
            background = properties.shading,
            lineSpacing = properties.lineSpacing,
            borders = properties.borders(),
        )
    }

    private fun ParagraphProperties.borders(): ParagraphBorders? {
        val sides = ParagraphBorders(
            top = borderTop?.takeIf { it.widthPt > 0f },
            bottom = borderBottom?.takeIf { it.widthPt > 0f },
            start = borderStart?.takeIf { it.widthPt > 0f },
            end = borderEnd?.takeIf { it.widthPt > 0f },
        )
        return sides.takeIf { it != ParagraphBorders() }
    }

    private class Indent(val startPt: Float, val firstLinePt: Float) {
        companion object {
            // List items hang their marker left of the text; plain paragraphs only shift the first line.
            // The list level's indent overrides the paragraph style's, but not the paragraph's own.
            fun of(properties: ParagraphProperties, direct: ParagraphProperties, marker: ListMarker?): Indent {
                val left = direct.indentLeft ?: marker?.indentLeft ?: properties.indentLeft ?: 0
                val directIndent = direct.hanging != null || direct.firstLine != null
                val hanging = when {
                    directIndent -> direct.hanging
                    marker?.hanging != null -> marker.hanging
                    else -> properties.hanging
                } ?: 0
                if (marker != null) {
                    return Indent(
                        OoxmlUnits.twipsToPoints(
                            (left - hanging).coerceAtLeast(
                                0
                            )
                        ),
                        0f
                    )
                }
                val firstLine = (properties.firstLine ?: 0) - hanging
                return Indent(
                    OoxmlUnits.twipsToPoints(left.coerceAtLeast(0)),
                    OoxmlUnits.twipsToPoints(firstLine)
                )
            }
        }
    }
}
