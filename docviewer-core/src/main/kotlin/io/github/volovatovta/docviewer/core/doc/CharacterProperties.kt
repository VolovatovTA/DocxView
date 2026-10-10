package io.github.volovatovta.docviewer.core.doc

import io.github.volovatovta.docviewer.core.binary.int32
import io.github.volovatovta.docviewer.core.binary.uint16
import io.github.volovatovta.docviewer.core.binary.uint8
import io.github.volovatovta.docviewer.core.model.RunStyle
import io.github.volovatovta.docviewer.core.model.VerticalAlign
import kotlin.math.abs

// Character formatting of a text run, decoded from a CHPX grpprl.
internal data class CharacterProperties(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strike: Boolean = false,
    val hidden: Boolean = false,
    val color: Int? = null,
    val highlight: Int? = null,
    val sizeHalfPoints: Int? = null,
    val verticalAlign: VerticalAlign = VerticalAlign.Baseline,
    val caps: Boolean = false,
    val smallCaps: Boolean = false,
    // Special characters stand for objects (pictures, footnote marks) rather than text.
    val special: Boolean = false,
    // Offset of the picture's PICF structure in the Data stream, or -1.
    val pictureLocation: Int = -1,
) {
    val isVisiblePicture: Boolean get() = special && !hidden && pictureLocation >= 0

    fun toRunStyle(bodySizeHalfPoints: Int): RunStyle = RunStyle(
        bold = bold,
        italic = italic,
        underline = underline,
        strikethrough = strike,
        color = color,
        background = highlight,
        relativeSize = sizeHalfPoints
            ?.let { it.toFloat() / bodySizeHalfPoints }
            ?.takeIf { abs(it - 1f) > SAME_SIZE_TOLERANCE },
        verticalAlign = verticalAlign,
        allCaps = caps,
        smallCaps = smallCaps,
    )

    companion object {
        private const val SAME_SIZE_TOLERANCE = 0.05f

        fun read(grpprl: ByteArray, range: IntRange): CharacterProperties {
            var properties = CharacterProperties()
            grpprl.forEachSprm(range) { sprm, operand -> properties = properties.with(sprm, grpprl, operand) }
            return properties
        }
    }
}

private fun CharacterProperties.with(sprm: Int, bytes: ByteArray, operand: Int): CharacterProperties = when (sprm) {
    Sprm.CHAR_BOLD -> copy(bold = bytes.toggleAt(operand))
    Sprm.CHAR_ITALIC -> copy(italic = bytes.toggleAt(operand))
    Sprm.CHAR_STRIKE, Sprm.CHAR_DOUBLE_STRIKE -> copy(strike = bytes.toggleAt(operand))
    Sprm.CHAR_HIDDEN -> copy(hidden = bytes.toggleAt(operand))
    Sprm.CHAR_SMALL_CAPS -> copy(smallCaps = bytes.toggleAt(operand))
    Sprm.CHAR_CAPS -> copy(caps = bytes.toggleAt(operand))
    Sprm.CHAR_SPECIAL -> copy(special = bytes.uint8(operand) != 0)
    Sprm.CHAR_PICTURE_LOCATION -> copy(pictureLocation = bytes.int32(operand))
    Sprm.CHAR_UNDERLINE -> copy(underline = bytes.uint8(operand) != 0)
    Sprm.CHAR_FONT_SIZE -> copy(sizeHalfPoints = bytes.uint16(operand))
    Sprm.CHAR_COLOR_INDEX -> copy(color = WordColors.fromIndex(bytes.uint8(operand)))
    Sprm.CHAR_COLOR -> copy(color = WordColors.fromColorRef(bytes.int32(operand)))
    Sprm.CHAR_HIGHLIGHT -> copy(highlight = WordColors.fromIndex(bytes.uint8(operand)))
    Sprm.CHAR_VERTICAL_POSITION -> copy(verticalAlign = verticalAlignOf(bytes.uint8(operand)))
    else -> this
}

private object ToggleOperand {
    const val ON = 0x01

    // 0x80 keeps the style's value and 0x81 inverts it; plain text styles are never bold, so it means "on".
    const val OPPOSITE_OF_STYLE = 0x81
}

private object VerticalPosition {
    const val SUPERSCRIPT = 1
    const val SUBSCRIPT = 2
}

private fun ByteArray.toggleAt(operand: Int): Boolean =
    uint8(operand).let { it == ToggleOperand.ON || it == ToggleOperand.OPPOSITE_OF_STYLE }

private fun verticalAlignOf(position: Int): VerticalAlign = when (position) {
    VerticalPosition.SUPERSCRIPT -> VerticalAlign.Superscript
    VerticalPosition.SUBSCRIPT -> VerticalAlign.Subscript
    else -> VerticalAlign.Baseline
}
