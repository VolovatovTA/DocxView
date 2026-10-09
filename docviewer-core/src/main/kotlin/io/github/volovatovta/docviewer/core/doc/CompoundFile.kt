package io.github.volovatovta.docviewer.core.doc

import io.github.volovatovta.docviewer.core.CorruptedDocumentException

// Minimal reader for OLE2 / Compound File Binary ([MS-CFB]) containers.
internal class CompoundFile(private val data: ByteArray) {

    class Entry(val name: String, val type: Int, val start: Int, val size: Long)

    private val sectorShift: Int
    private val sectorSize: Int
    private val miniSectorSize: Int
    private val miniCutoff: Int
    private val fat: IntArray
    private val miniFat: IntArray
    val entries: List<Entry>
    private val miniStream: ByteArray

    init {
        if (data.size < 512 || u32(0) != 0xE011CFD0.toInt() || u32(4) != 0xE11AB1A1.toInt()) {
            throw CorruptedDocumentException("Not an OLE2 compound file")
        }
        sectorShift = u16(0x1E)
        if (sectorShift !in 7..16) throw CorruptedDocumentException("Bad sector size")
        sectorSize = 1 shl sectorShift
        miniSectorSize = 1 shl u16(0x20).coerceIn(1, 15)
        miniCutoff = u32(0x38).takeIf { it > 0 } ?: 4096
        fat = readFat()
        entries = readDirectory(u32(0x30))
        val root = entries.firstOrNull { it.type == TYPE_ROOT }
        miniStream = if (root != null && root.start >= 0) readChain(root.start, root.size) else ByteArray(0)
        val firstMiniFat = u32(0x3C)
        miniFat = if (firstMiniFat >= 0) toInts(readChain(firstMiniFat, Long.MAX_VALUE)) else IntArray(0)
    }

    fun find(name: String): Entry? = entries.firstOrNull { it.type == TYPE_STREAM && it.name.equals(name, ignoreCase = true) }

    fun stream(name: String): ByteArray? {
        val entry = find(name) ?: return null
        return if (entry.size < miniCutoff) readMiniChain(entry.start, entry.size) else readChain(entry.start, entry.size)
    }

    private fun readFat(): IntArray {
        val fatSectors = ArrayList<Int>()
        for (i in 0 until 109) {
            val s = u32(0x4C + i * 4)
            if (s >= 0) fatSectors += s
        }
        // Large files continue the FAT sector list in DIFAT sectors.
        var difat = u32(0x44)
        var guard = 0
        val perSector = sectorSize / 4 - 1
        while (difat >= 0 && guard++ < 100_000) {
            val offset = sectorOffset(difat)
            if (offset + sectorSize > data.size) break
            for (i in 0 until perSector) {
                val s = u32(offset + i * 4)
                if (s >= 0) fatSectors += s
            }
            difat = u32(offset + perSector * 4)
        }
        val result = IntArray(fatSectors.size * (sectorSize / 4))
        var index = 0
        for (sector in fatSectors) {
            val offset = sectorOffset(sector)
            for (i in 0 until sectorSize / 4) {
                result[index++] = if (offset + i * 4 + 4 <= data.size) u32(offset + i * 4) else END_OF_CHAIN
            }
        }
        return result
    }

    private fun readDirectory(firstSector: Int): List<Entry> {
        val bytes = readChain(firstSector, Long.MAX_VALUE)
        val result = ArrayList<Entry>()
        for (offset in 0 until bytes.size - 127 step 128) {
            val nameLength = (u16(bytes, offset + 0x40) / 2 - 1).coerceIn(0, 31)
            val name = String(CharArray(nameLength) { (u16(bytes, offset + it * 2)).toChar() })
            val type = bytes[offset + 0x42].toInt() and 0xFF
            val start = u32(bytes, offset + 0x74)
            val size = (u32(bytes, offset + 0x78).toLong() and 0xFFFFFFFFL).let { if (sectorSize == 512) it and 0x7FFFFFFF else it }
            result += Entry(name, type, start, size)
        }
        return result
    }

    private fun readChain(start: Int, size: Long): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        var sector = start
        var guard = 0
        while (sector >= 0 && sector < fat.size && guard++ < fat.size + 1 && out.size() < size) {
            val offset = sectorOffset(sector)
            if (offset >= data.size) break
            out.write(data, offset, minOf(sectorSize, data.size - offset))
            sector = fat[sector]
        }
        val bytes = out.toByteArray()
        return if (size < bytes.size) bytes.copyOf(size.toInt()) else bytes
    }

    private fun readMiniChain(start: Int, size: Long): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        var sector = start
        var guard = 0
        while (sector >= 0 && sector < miniFat.size && guard++ < miniFat.size + 1 && out.size() < size) {
            val offset = sector * miniSectorSize
            if (offset >= miniStream.size) break
            out.write(miniStream, offset, minOf(miniSectorSize, miniStream.size - offset))
            sector = miniFat[sector]
        }
        val bytes = out.toByteArray()
        return if (size < bytes.size) bytes.copyOf(size.toInt()) else bytes
    }

    private fun sectorOffset(sector: Int): Int = (sector + 1) shl sectorShift

    private fun toInts(bytes: ByteArray) = IntArray(bytes.size / 4) { u32(bytes, it * 4) }

    private fun u16(offset: Int) = u16(data, offset)

    private fun u32(offset: Int) = u32(data, offset)

    companion object {
        const val TYPE_STREAM = 2
        const val TYPE_ROOT = 5
        const val END_OF_CHAIN = -2

        fun u16(b: ByteArray, o: Int): Int = (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8)

        fun u32(b: ByteArray, o: Int): Int =
            (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8) or ((b[o + 2].toInt() and 0xFF) shl 16) or ((b[o + 3].toInt() and 0xFF) shl 24)
    }
}
