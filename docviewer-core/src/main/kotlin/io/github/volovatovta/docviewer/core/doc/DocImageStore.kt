package io.github.volovatovta.docviewer.core.doc

import io.github.volovatovta.docviewer.core.model.ImageStore

// Inline pictures live in the Data stream as PICF + OfficeArt records; the PNG/JPEG payload is found by its signature.
internal class DocImageStore(private val data: ByteArray) : ImageStore {

    class Picture(val id: String, val widthPt: Float?, val heightPt: Float?)

    fun describe(picf: Int): Picture? {
        if (picf < 0 || picf > data.size - Picf.HEADER_SIZE) return null
        if (data.uint16(picf + Picf.HEADER_SIZE_FIELD) != Picf.HEADER_SIZE || payloadRange(picf) == null) return null
        return Picture(
            id = ID_PREFIX + picf,
            widthPt = displaySize(picf + Picf.WIDTH_GOAL, picf + Picf.HORIZONTAL_SCALE),
            heightPt = displaySize(picf + Picf.HEIGHT_GOAL, picf + Picf.VERTICAL_SCALE),
        )
    }

    override fun open(imageId: String): ByteArray? {
        val picf = imageId.removePrefix(ID_PREFIX).toIntOrNull() ?: return null
        val payload = payloadRange(picf) ?: return null
        return data.copyOfRange(payload.first, payload.last + 1)
    }

    // The goal size in twips, scaled by a per-mille factor where 0 means "unscaled".
    private fun displaySize(goal: Int, scale: Int): Float? {
        val perMille = data.uint16(scale).takeIf { it > 0 } ?: Picf.UNSCALED
        return (data.uint16(goal) * perMille / Picf.UNSCALED.toFloat() / TWIPS_PER_POINT).takeIf { it > 0 }
    }

    private fun payloadRange(picf: Int): IntRange? {
        if (picf < 0 || picf > data.size - Int.SIZE_BYTES) return null
        val end = picf + data.int32(picf)
        if (end <= picf + Picf.HEADER_SIZE || end > data.size) return null
        val start = (picf + Picf.HEADER_SIZE until end).firstOrNull { offset ->
            IMAGE_SIGNATURES.any { data.startsWith(it, offset) }
        }
        return start?.let { it until end }
    }

    private object Picf {
        const val HEADER_SIZE = 0x44
        const val HEADER_SIZE_FIELD = 0x04
        const val WIDTH_GOAL = 0x1C
        const val HEIGHT_GOAL = 0x1E
        const val HORIZONTAL_SCALE = 0x20
        const val VERTICAL_SCALE = 0x22
        const val UNSCALED = 1000
    }

    private companion object {
        const val ID_PREFIX = "doc-pic:"
        const val TWIPS_PER_POINT = 20f

        val IMAGE_SIGNATURES = listOf(
            byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte()),
            byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()),
        )
    }
}
