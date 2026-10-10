package io.github.volovatovta.docviewer.core.docx

// Word measures layout in twentieths of a point (twips) and DrawingML in English Metric Units.
internal object OoxmlUnits {
    const val TWIPS_PER_POINT = 20f
    const val EMU_PER_POINT = 12_700f

    fun twipsToPoints(twips: Int): Float = twips / TWIPS_PER_POINT

    fun emuToPoints(emu: Long): Float = emu / EMU_PER_POINT
}
