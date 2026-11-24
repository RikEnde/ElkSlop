// SPDX-FileCopyrightText: 2026 The Elkslop contributors
// SPDX-License-Identifier: BSD-3-Clause

package elkslop.devkit

/**
 * Writes cassette files to a UEF tape image in the cassette filing system's
 * format, so the Electron's OS loads them with `LOAD`, `CHAIN` or `*RUN`, in
 * Elkslop or on any emulator that reads UEF.
 *
 * Each file is split into blocks of 256 bytes. Each block is a sync byte &2A,
 * the name, ending in 0, the load and exec addresses, the block number, the
 * data length, a flag (&80 on the last block), the address of the next file
 * (unused, 0), a CRC of all that, the data and a CRC of the data. The CRCs are
 * CRC-16/XMODEM, high byte first. Carrier tone comes before every block, longer
 * before a file's first; this is the timing Acorn's own cassettes have.
 */
object UefWriter {
    private const val BLOCK_SIZE = 256
    private const val LEADER_CYCLES = 3000
    private const val BLOCK_GAP_CYCLES = 600
    private const val LAST_BLOCK = 0x80
    private const val EMPTY_BLOCK = 0x40

    fun write(files: List<CassetteFile>): ByteArray {
        val out = ArrayList<Byte>()
        out += "UEF File!".encodeToByteArray().toList()
        out += listOf<Byte>(0, 10, 0) // Terminator, then version 0.10.
        out.chunk(0x0000, "Elkslop devkit".encodeToByteArray().toList() + 0)
        for ((index, file) in files.withIndex()) {
            // A second of silence between files, as between two SAVEs.
            if (index > 0) out.chunk(0x0112, u16(2 * 1200))
            out.chunk(0x0110, u16(LEADER_CYCLES))
            val blocks = (file.data.size + BLOCK_SIZE - 1) / BLOCK_SIZE
            for (number in 0 until maxOf(blocks, 1)) {
                if (number > 0) out.chunk(0x0110, u16(BLOCK_GAP_CYCLES))
                out.chunk(0x0100, block(file, number, blocks))
            }
            out.chunk(0x0110, u16(BLOCK_GAP_CYCLES))
        }
        return out.toByteArray()
    }

    private fun block(file: CassetteFile, number: Int, blocks: Int): List<Byte> {
        val data = file.data.copyOfRange(number * BLOCK_SIZE, minOf(file.data.size, (number + 1) * BLOCK_SIZE)).toList()
        val last = number >= blocks - 1
        val flag = (if (last) LAST_BLOCK else 0) or (if (data.isEmpty()) EMPTY_BLOCK else 0)
        val header = file.name.encodeToByteArray().toList() + 0 +
            u32(0xFFFF0000.toInt() or file.load) + u32(0xFFFF0000.toInt() or file.exec) +
            u16(number) + u16(data.size) + flag.toByte() + u32(0)
        val block = mutableListOf<Byte>(0x2A)
        block += header
        block += crcBytes(header)
        if (data.isNotEmpty()) {
            block += data
            block += crcBytes(data)
        }
        return block
    }

    /** CRC-16/XMODEM: polynomial &1021, starting at 0. */
    internal fun crc(bytes: List<Byte>): Int {
        var crc = 0
        for (byte in bytes) {
            crc = crc xor ((byte.toInt() and 0xFF) shl 8)
            repeat(8) { crc = if (crc and 0x8000 != 0) (crc shl 1) xor 0x1021 else crc shl 1 }
            crc = crc and 0xFFFF
        }
        return crc
    }

    private fun crcBytes(bytes: List<Byte>): List<Byte> = crc(bytes).let { listOf((it shr 8).toByte(), it.toByte()) }

    private fun MutableList<Byte>.chunk(id: Int, payload: List<Byte>) {
        addAll(u16(id))
        addAll(u32(payload.size))
        addAll(payload)
    }

    private fun u16(value: Int): List<Byte> = listOf(value.toByte(), (value shr 8).toByte())

    private fun u32(value: Int): List<Byte> = u16(value) + u16(value shr 16)
}
