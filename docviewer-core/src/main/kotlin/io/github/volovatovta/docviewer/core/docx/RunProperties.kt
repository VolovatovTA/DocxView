package io.github.volovatovta.docviewer.core.docx

import io.github.volovatovta.docviewer.core.model.VerticalAlign

// Partial run properties as found in one style or one w:rPr; null means "inherit".
internal data class RunProperties(
    var bold: Boolean? = null,
    var italic: Boolean? = null,
    var underline: Boolean? = null,
    var strike: Boolean? = null,
    var color: Int? = null,
    var colorAuto: Boolean? = null,
    var highlight: Int? = null,
    var shading: Int? = null,
    var sizeHalfPoints: Int? = null,
    var verticalAlign: VerticalAlign? = null,
    var monospace: Boolean? = null,
    var caps: Boolean? = null,
    var smallCaps: Boolean? = null,
    var hidden: Boolean? = null,
    var styleId: String? = null,
) {
    // A flat field-by-field merge: every elvis counts as a branch, yet there is no branching logic to untangle.
    @Suppress("CyclomaticComplexMethod")
    fun overlay(top: RunProperties): RunProperties = RunProperties(
        bold = top.bold ?: bold,
        italic = top.italic ?: italic,
        underline = top.underline ?: underline,
        strike = top.strike ?: strike,
        color = if (top.colorAuto == true) null else top.color ?: color,
        colorAuto = top.colorAuto ?: colorAuto,
        highlight = top.highlight ?: highlight,
        shading = top.shading ?: shading,
        sizeHalfPoints = top.sizeHalfPoints ?: sizeHalfPoints,
        verticalAlign = top.verticalAlign ?: verticalAlign,
        monospace = top.monospace ?: monospace,
        caps = top.caps ?: caps,
        smallCaps = top.smallCaps ?: smallCaps,
        hidden = top.hidden ?: hidden,
        styleId = top.styleId ?: styleId,
    )
}
