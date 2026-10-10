package io.github.volovatovta.docviewer.core.doc

import io.github.volovatovta.docviewer.core.CorruptedDocumentException
import java.io.ByteArrayOutputStream

// Minimal reader for OLE2 / Compound File Binary ([MS-CFB]) containers.
internal class CompoundFile(private val data: ByteArray) {

    class Entry(val name: String, val type: Int, val startSector: Int, val size: Long)

    init {
        if (data.size < CfbHeader.SIZE || !data.startsWith(SIGNATURE)) {
            throw CorruptedDocumentException("Not an OLE2 compound file")
        }
    }

    private val sectorShift = data.uint16(CfbHeader.SECTOR_SHIFT).also { shift ->
        if (shift !in CfbLimits.SECTOR_SHIFTS) throw CorruptedDocumentException("Bad sector size")
    }
    private val sectorSize = 1 shl sectorShift
    private val miniSectorSize = 1 shl data.uint16(CfbHeader.MINI_SECTOR_SHIFT).coerceIn(CfbLimits.MINI_SECTOR_SHIFTS)
    private val miniStreamCutoff = data.int32(CfbHeader.MINI_STREAM_CUTOFF).takeIf { it > 0 }
        ?: CfbLimits.DEFAULT_MINI_STREAM_CUTOFF
    private val fat = readFat()

    val entries: List<Entry> = readDirectory()

    private val miniStream: ByteArray = entries.firstOrNull { it.type == TYPE_ROOT && it.startSector >= 0 }
        ?.let { root -> readSectorChain(root.startSector, root.size) }
        ?: ByteArray(0)
    private val miniFat: IntArray = data.int32(CfbHeader.FIRST_MINI_FAT_SECTOR)
        .takeIf { it >= 0 }
        ?.let { start -> readSectorChain(start, Long.MAX_VALUE).toIntArray() }
        ?: IntArray(0)

    fun find(name: String): Entry? =
        entries.firstOrNull { it.type == TYPE_STREAM && it.name.equals(name, ignoreCase = true) }

    fun stream(name: String): ByteArray? {
        val entry = find(name) ?: return null
        return if (entry.size < miniStreamCutoff) {
            readMiniChain(entry.startSector, entry.size)
        } else {
            readSectorChain(entry.startSector, entry.size)
        }
    }

    private fun readFat(): IntArray {
        val fatSectors = headerDifatSectors() + chainedDifatSectors()
        val entriesPerSector = sectorSize / Int.SIZE_BYTES
        return IntArray(fatSectors.size * entriesPerSector) { index ->
            val sectorStart = sectorOffset(fatSectors[index / entriesPerSector])
            val offset = sectorStart + index % entriesPerSector * Int.SIZE_BYTES
            if (offset in 0..data.size - Int.SIZE_BYTES) data.int32(offset) else END_OF_CHAIN
        }
    }

    private fun headerDifatSectors(): List<Int> =
        (0 until CfbHeader.DIFAT_ENTRIES)
            .map { data.int32(CfbHeader.DIFAT + it * Int.SIZE_BYTES) }
            .filter { it >= 0 }

    // Large files continue the list of FAT sectors in a chain of DIFAT sectors.
    private fun chainedDifatSectors(): List<Int> {
        val sectors = ArrayList<Int>()
        val entriesPerSector = sectorSize / Int.SIZE_BYTES - 1
        var difatSector = data.int32(CfbHeader.FIRST_DIFAT_SECTOR)
        var visited = 0
        while (difatSector >= 0 && visited++ < CfbLimits.MAX_DIFAT_SECTORS) {
            val offset = sectorOffset(difatSector)
            if (offset + sectorSize > data.size) break
            (0 until entriesPerSector)
                .map { data.int32(offset + it * Int.SIZE_BYTES) }
                .filterTo(sectors) { it >= 0 }
            difatSector = data.int32(offset + entriesPerSector * Int.SIZE_BYTES)
        }
        return sectors
    }

    private fun readDirectory(): List<Entry> {
        val directory = readSectorChain(data.int32(CfbHeader.FIRST_DIRECTORY_SECTOR), Long.MAX_VALUE)
        return (0..directory.size - DirectoryEntryLayout.SIZE step DirectoryEntryLayout.SIZE)
            .map { offset -> directory.readEntry(offset) }
    }

    private fun ByteArray.readEntry(offset: Int): Entry {
        val nameChars = uint16(offset + DirectoryEntryLayout.NAME_LENGTH) / Char.SIZE_BYTES - 1
        val name = utf16(offset, nameChars.coerceIn(0, DirectoryEntryLayout.MAX_NAME_CHARS))
        val size = uint32(offset + DirectoryEntryLayout.STREAM_SIZE)
        // Version 3 files only use the low 31 bits of the stream size.
        val isVersion3 = sectorSize == CfbLimits.VERSION_3_SECTOR_SIZE
        return Entry(
            name = name,
            type = uint8(offset + DirectoryEntryLayout.TYPE),
            startSector = int32(offset + DirectoryEntryLayout.START_SECTOR),
            size = if (isVersion3) size and CfbLimits.VERSION_3_SIZE_MASK else size,
        )
    }

    private fun readSectorChain(start: Int, size: Long): ByteArray =
        readChain(start, size, fat, data, sectorSize, ::sectorOffset)

    private fun readMiniChain(start: Int, size: Long): ByteArray =
        readChain(start, size, miniFat, miniStream, miniSectorSize) { sector -> sector * miniSectorSize }

    private fun readChain(
        start: Int,
        size: Long,
        nextSector: IntArray,
        source: ByteArray,
        unitSize: Int,
        offsetOf: (Int) -> Int,
    ): ByteArray {
        val out = ByteArrayOutputStream()
        var sector = start
        var visited = 0
        // The visit counter stops cyclic chains in damaged files.
        while (sector in nextSector.indices && visited++ <= nextSector.size && out.size() < size) {
            val offset = offsetOf(sector)
            if (offset !in source.indices) break
            out.write(source, offset, minOf(unitSize, source.size - offset))
            sector = nextSector[sector]
        }
        val bytes = out.toByteArray()
        return if (size < bytes.size) bytes.copyOf(size.toInt()) else bytes
    }

    // The header occupies the slot of sector -1.
    private fun sectorOffset(sector: Int): Int = (sector + 1) shl sectorShift

    private fun ByteArray.toIntArray(): IntArray = IntArray(size / Int.SIZE_BYTES) { int32(it * Int.SIZE_BYTES) }

    companion object {
        const val TYPE_STREAM = 2
        const val TYPE_ROOT = 5
        private const val END_OF_CHAIN = -2

        private val SIGNATURE = byteArrayOf(
            0xD0.toByte(),
            0xCF.toByte(),
            0x11,
            0xE0.toByte(),
            0xA1.toByte(),
            0xB1.toByte(),
            0x1A,
            0xE1.toByte(),
        )
    }
}

private object CfbHeader {
    const val SIZE = 512
    const val SECTOR_SHIFT = 0x1E
    const val MINI_SECTOR_SHIFT = 0x20
    const val FIRST_DIRECTORY_SECTOR = 0x30
    const val MINI_STREAM_CUTOFF = 0x38
    const val FIRST_MINI_FAT_SECTOR = 0x3C
    const val FIRST_DIFAT_SECTOR = 0x44
    const val DIFAT = 0x4C
    const val DIFAT_ENTRIES = 109
}

private object DirectoryEntryLayout {
    const val SIZE = 128
    const val NAME_LENGTH = 0x40
    const val TYPE = 0x42
    const val START_SECTOR = 0x74
    const val STREAM_SIZE = 0x78
    const val MAX_NAME_CHARS = 31
}

private object CfbLimits {
    val SECTOR_SHIFTS = 7..16
    val MINI_SECTOR_SHIFTS = 1..15
    const val DEFAULT_MINI_STREAM_CUTOFF = 4096
    const val MAX_DIFAT_SECTORS = 100_000
    const val VERSION_3_SECTOR_SIZE = 512
    const val VERSION_3_SIZE_MASK = 0x7FFFFFFFL
}
