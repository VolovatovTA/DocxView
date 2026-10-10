package io.github.volovatovta.docviewer.core.docx

import io.github.volovatovta.docviewer.core.model.TextAlignment

// Partial paragraph properties as found in one style or one w:pPr; null means "inherit". Lengths are in twips.
internal data class ParagraphProperties(
    var styleId: String? = null,
    var alignment: TextAlignment? = null,
    var indentLeft: Int? = null,
    var hanging: Int? = null,
    var firstLine: Int? = null,
    var spacingBefore: Int? = null,
    var spacingAfter: Int? = null,
    var numberingId: String? = null,
    var listLevel: Int? = null,
    var outlineLevel: Int? = null,
    var shading: Int? = null,
    var pageBreakBefore: Boolean? = null,
) {
    fun overlay(top: ParagraphProperties): ParagraphProperties {
        // Hanging and first-line indents are mutually exclusive in Word, so they are inherited together.
        val indentFromTop = top.hanging != null || top.firstLine != null
        return ParagraphProperties(
            styleId = top.styleId ?: styleId,
            alignment = top.alignment ?: alignment,
            indentLeft = top.indentLeft ?: indentLeft,
            hanging = if (indentFromTop) top.hanging else hanging,
            firstLine = if (indentFromTop) top.firstLine else firstLine,
            spacingBefore = top.spacingBefore ?: spacingBefore,
            spacingAfter = top.spacingAfter ?: spacingAfter,
            numberingId = top.numberingId ?: numberingId,
            listLevel = top.listLevel ?: listLevel,
            outlineLevel = top.outlineLevel ?: outlineLevel,
            shading = top.shading ?: shading,
            pageBreakBefore = top.pageBreakBefore ?: pageBreakBefore,
        )
    }
}
