package io.github.volovatovta.docviewer.core.docx

import io.github.volovatovta.docviewer.core.model.ParagraphStyle

// Resolves paragraph properties through the style sheet and numbers list items as they appear.
internal class ParagraphFormatter(
    private val styles: StyleSheet,
    private val numbering: Numbering
) {

    fun properties(direct: ParagraphProperties): ParagraphProperties =
        styles.resolveParagraph(direct.styleId).first.overlay(direct)

    fun style(properties: ParagraphProperties): ParagraphStyle {
        val marker = properties.numberingId?.let {
            numbering.next(
                numberingId = it,
                level = properties.listLevel ?: 0
            )
        }
        val indent = Indent.of(properties, marker)
        return ParagraphStyle(
            alignment = properties.alignment ?: ParagraphStyle.Default.alignment,
            headingLevel = styles.headingLevel(properties.styleId, properties.outlineLevel),
            indentStartPt = indent.startPt,
            firstLineIndentPt = indent.firstLinePt,
            spaceBeforePt = properties.spacingBefore?.let(OoxmlUnits::twipsToPoints),
            spaceAfterPt = properties.spacingAfter?.let(OoxmlUnits::twipsToPoints),
            listMarker = marker?.text?.takeIf { it.isNotEmpty() },
            listLevel = marker?.level ?: 0,
            background = properties.shading,
        )
    }

    private class Indent(val startPt: Float, val firstLinePt: Float) {
        companion object {
            // List items hang their marker left of the text; plain paragraphs only shift the first line.
            fun of(properties: ParagraphProperties, marker: ListMarker?): Indent {
                val left = properties.indentLeft ?: marker?.indentLeft ?: 0
                val inheritedHanging = marker?.hanging.takeIf { properties.firstLine == null }
                val hanging = properties.hanging ?: inheritedHanging ?: 0
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
