package io.github.volovatovta.docviewer.core.doc

// Colors as Word 97 stores them: an index into a fixed 16-color palette, or a COLORREF.
internal object WordColors {
    private const val OPAQUE = 0xFF000000.toInt()
    private const val CHANNEL_MASK = 0xFF
    private const val AUTO_MARKER = 0xFF
    private const val RED_SHIFT = 16
    private const val GREEN_MASK = 0xFF00
    private const val ALPHA_SHIFT = 24

    // Ico values 1..16; index 0 means "auto".
    private val PALETTE = intArrayOf(
        0,
        0xFF000000.toInt(), 0xFF0000FF.toInt(), 0xFF00FFFF.toInt(), 0xFF00FF00.toInt(),
        0xFFFF00FF.toInt(), 0xFFFF0000.toInt(), 0xFFFFFF00.toInt(), 0xFFFFFFFF.toInt(),
        0xFF000080.toInt(), 0xFF008080.toInt(), 0xFF008000.toInt(), 0xFF800080.toInt(),
        0xFF800000.toInt(), 0xFF808000.toInt(), 0xFF808080.toInt(), 0xFFC0C0C0.toInt(),
    )

    fun fromIndex(index: Int): Int? = PALETTE.getOrNull(index)?.takeIf { index > 0 }

    // COLORREF is 0x00BBGGRR, with 0xFF in the top byte meaning "auto".
    fun fromColorRef(colorRef: Int): Int? {
        if (colorRef ushr ALPHA_SHIFT == AUTO_MARKER) return null
        val red = colorRef and CHANNEL_MASK
        val blue = (colorRef shr RED_SHIFT) and CHANNEL_MASK
        return OPAQUE or (red shl RED_SHIFT) or (colorRef and GREEN_MASK) or blue
    }
}
