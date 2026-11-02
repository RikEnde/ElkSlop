// SPDX-FileCopyrightText: 2026 The Elkslop contributors
// SPDX-License-Identifier: BSD-3-Clause

package elkslop.electron

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UefTapeTest {
    private val header = "UEF File!".encodeToByteArray() + byteArrayOf(0, 10, 0)

    private fun chunk(id: Int, vararg payload: Int): ByteArray =
        byteArrayOf(id.toByte(), (id shr 8).toByte(), payload.size.toByte(), 0, 0, 0) +
            ByteArray(payload.size) { payload[it].toByte() }

    private fun tape(vararg chunks: ByteArray): UefTape =
        UefTape.parse(chunks.fold(header) { image, next -> image + next })

    private fun UefTape.play(): String = buildString {
        repeat(this@play.length) {
            append(when (readBit()) {
                TapeBit.ZERO -> '0'
                TapeBit.ONE -> '1'
                TapeBit.SILENCE -> '.'
            })
        }
    }

    @Test
    fun dataChunkFramesBytesLsbFirst() {
        assertEquals("0" + "10100000" + "1", tape(chunk(0x0100, 0x05)).play())
    }

    @Test
    fun carrierIsTwoCyclesPerBit() {
        assertEquals("1".repeat(4), tape(chunk(0x0110, 8, 0)).play())
    }

    @Test
    fun carrierWithDummyByteWrapsAA() {
        assertEquals("11" + "0" + "01010101" + "1" + "1", tape(chunk(0x0111, 4, 0, 2, 0)).play())
    }

    @Test
    fun integerGapCountsHalfBits() {
        assertEquals("...", tape(chunk(0x0112, 6, 0)).play())
    }

    @Test
    fun floatGapIsInSeconds() {
        val half = 0.5f.toRawBits() // 600 bit periods.
        val gap = chunk(0x0116, half and 0xFF, (half shr 8) and 0xFF, (half shr 16) and 0xFF, (half shr 24) and 0xFF)
        assertEquals(".".repeat(600), tape(gap).play())
    }

    @Test
    fun explicitBitsAreRawWithUnusedBitsDropped() {
        assertEquals("101" + "00", tape(chunk(0x0102, 3, 0x05)).play())
    }

    @Test
    fun definedFormatAddsParityAndStopBits() {
        // 7E2: start, seven data bits, even parity (three ones: 1), two stop bits.
        assertEquals("0" + "1110000" + "1" + "11", tape(chunk(0x0104, 7, 'E'.code, 2, 0x07)).play())
    }

    @Test
    fun unknownChunksAreSkipped() {
        assertEquals("11", tape(chunk(0x0000, 'x'.code), chunk(0x0114, 1, 2, 3), chunk(0x0110, 4, 0)).play())
    }

    @Test
    fun rejectsBadImages() {
        assertFailsWith<IllegalArgumentException> { UefTape.parse(ByteArray(40)) }
        assertFailsWith<IllegalArgumentException> { UefTape.parse(header + byteArrayOf(0, 1, 9, 0, 0, 0, 1)) }
        assertFailsWith<IllegalArgumentException> { tape(chunk(0x0110, 1)) }
        val hugeGap = Float.POSITIVE_INFINITY.toRawBits()
        assertFailsWith<IllegalArgumentException> {
            tape(chunk(0x0116, hugeGap and 0xFF, (hugeGap shr 8) and 0xFF, (hugeGap shr 16) and 0xFF, (hugeGap shr 24) and 0xFF))
        }
    }

    @Test
    fun endOfTapeIsSilentAndRewindRestarts() {
        val tape = tape(chunk(0x0110, 2, 0))
        assertEquals(TapeBit.ONE, tape.readBit())
        assertEquals(TapeBit.SILENCE, tape.readBit())
        assertEquals(1, tape.position)
        tape.rewind()
        assertEquals(TapeBit.ONE, tape.readBit())
    }

    @Test
    fun ulaReceivesBytesAndHighToneFromTape() {
        val ula = ElectronUla(ByteArray(0x8000))
        val tape = tape(chunk(0x0110, 40, 0), chunk(0x0100, 0x5A))
        ula.cassette.port = tape
        ula.writeRegister(0xFE07, 0x40) // Motor on, cassette input.
        repeat(UlaCassette.BIT_TICKS * 20) { ula.tick() }
        assertTrue(ula.interrupts.pending and UlaInterrupt.HIGH_TONE != 0)
        assertFalse(ula.interrupts.pending and UlaInterrupt.RECEIVE_FULL != 0)
        repeat(UlaCassette.BIT_TICKS * 10) { ula.tick() }
        assertTrue(ula.interrupts.pending and UlaInterrupt.RECEIVE_FULL != 0)
        assertEquals(0x5A, ula.readRegister(0xFE04))
        assertFalse(ula.interrupts.pending and UlaInterrupt.RECEIVE_FULL != 0)
    }

    @Test
    fun tapeMovesOnlyWhileTheMotorRuns() {
        val ula = ElectronUla(ByteArray(0x8000))
        val tape = tape(chunk(0x0110, 40, 0))
        ula.cassette.port = tape
        ula.writeRegister(0xFE07, 0x00) // Motor off.
        repeat(UlaCassette.BIT_TICKS * 3) { ula.tick() }
        assertEquals(0, tape.position)
        ula.writeRegister(0xFE07, 0x40)
        repeat(UlaCassette.BIT_TICKS * 3) { ula.tick() }
        assertEquals(3, tape.position)
        ula.writeRegister(0xFE07, 0x42) // Still rolling while the ULA makes sound.
        repeat(UlaCassette.BIT_TICKS * 2) { ula.tick() }
        assertEquals(5, tape.position)
    }
}
