// SPDX-FileCopyrightText: 2026 The Elkslop contributors
// SPDX-License-Identifier: BSD-3-Clause

package elkslop.devkit

import elkslop.electron.TapeBit
import elkslop.electron.UefTape
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class UefWriterTest {
    /** The bytes on [tape], read from its bits as the ULA would: a 0 start bit, 8 data bits, a 1 stop bit. */
    private fun bytesOn(tape: UefTape): List<Int> {
        val bits = List(tape.length) { tape.readBit() }
        val bytes = mutableListOf<Int>()
        var i = 0
        while (i < bits.size) {
            if (bits[i] != TapeBit.ZERO) { i++; continue }
            var value = 0
            for (bit in 0 until 8) if (bits[i + 1 + bit] == TapeBit.ONE) value = value or (1 shl bit)
            bytes += value
            i += 10
        }
        return bytes
    }

    @Test
    fun crcMatchesAcornsTapes() {
        // The first block header of Acorn's Introductory Cassette, from the name to
        // the next-file address, has the CRC &29D3.
        val header = listOf(0x49, 0x4E, 0x54, 0x52, 0x4F, 0x00, 0x00, 0x0E, 0xFF, 0xFF, 0x23, 0x80, 0xFF, 0xFF, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00)
        assertEquals(0x29D3, UefWriter.crc(header.map { it.toByte() }))
    }

    @Test
    fun writesBlocksTheOsCanRead() {
        val data = ByteArray(300) { it.toByte() }
        val tape = UefTape.parse(UefWriter.write(listOf(CassetteFile("TEST", 0x2000, 0x2010, data))))
        val bytes = bytesOn(tape)
        // Two blocks: 256 bytes, then 44, the last flagged &80.
        val first = bytes.indexOf(0x2A)
        assertEquals("TEST".map { it.code } + 0, bytes.subList(first + 1, first + 6))
        assertEquals(listOf(0x00, 0x20, 0xFF, 0xFF, 0x10, 0x20, 0xFF, 0xFF, 0, 0, 0, 1, 0), bytes.subList(first + 6, first + 19))
        val second = first + 1 + 5 + 17 + 2 + 256 + 2
        assertEquals(0x2A, bytes[second])
        assertEquals(listOf(1, 0, 44, 0, 0x80), bytes.subList(second + 14, second + 19))
        assertEquals(bytes.subList(first + 1 + 5 + 17 + 2, first + 1 + 5 + 17 + 2 + 256), data.take(256).map { it.toInt() and 0xFF })
    }

    @Test
    fun writesAnEmptyFileAsOneEmptyBlock() {
        val bytes = bytesOn(UefTape.parse(UefWriter.write(listOf(CassetteFile("E", 0x2000, 0x2000, ByteArray(0))))))
        val sync = bytes.indexOf(0x2A)
        assertEquals(listOf(0, 0, 0, 0, 0xC0), bytes.subList(sync + 3 + 8, sync + 3 + 13))
        // Header CRC, and no data or data CRC after it.
        assertEquals(sync + 3 + 17 + 2, bytes.size)
    }

    @Test
    fun refusesNamesTheOsCantTake() {
        assertFailsWith<IllegalArgumentException> { CassetteFile("ELEVENCHARS", 0, 0, ByteArray(1)) }
        assertFailsWith<IllegalArgumentException> { CassetteFile("TWO WORDS", 0, 0, ByteArray(1)) }
        assertFailsWith<IllegalArgumentException> { CassetteFile("", 0, 0, ByteArray(1)) }
        assertFailsWith<IllegalArgumentException> { CassetteFile("BIG", 0xFF00, 0, ByteArray(0x101)) }
    }

    @Test
    fun runsBasicWithChainAndCodeWithLoadAndCall() {
        assertEquals("CHAIN\"HELLO\"", CassetteFile.basic("HELLO", byteArrayOf(0x0D, -1)).runCommand)
        assertEquals("*LOAD CODE\nCALL &0E10", CassetteFile("CODE", 0x0E00, 0x0E10, ByteArray(0x20)).runCommand)
    }
}
