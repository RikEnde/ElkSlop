// SPDX-FileCopyrightText: 2026 The Elkslop contributors
// SPDX-License-Identifier: BSD-3-Clause

package elkslop.electron

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ElectronUlaTest {
    private val ram = ByteArray(0x8000)
    private val ula = ElectronUla(ram)

    @Test
    fun powerOnFlagClearsOnStatusRead() {
        assertEquals(0x02, ula.readRegister(0xFE00) and 0x02)
        assertEquals(0x80, ula.readRegister(0xFE00) and 0x82)
    }

    @Test
    fun peekDoesNotAcknowledgePowerOn() {
        ula.peekRegister(0xFE00)
        assertEquals(0x02, ula.readRegister(0xFE00) and 0x02)
    }

    @Test
    fun pendingSourceAssertsIrqOnlyWhenEnabled() {
        ula.interrupts.raise(UlaInterrupt.REAL_TIME_CLOCK)
        assertFalse(ula.irqAsserted)
        ula.writeRegister(0xFE00, UlaInterrupt.REAL_TIME_CLOCK)
        assertTrue(ula.irqAsserted)
        assertEquals(1, ula.peekRegister(0xFE00) and 1)
        ula.writeRegister(0xFE05, 0x20 or 0x0A) // Clear RTC, keep BASIC paged.
        assertFalse(ula.irqAsserted)
    }

    @Test
    fun powerOnSourceCannotAssertIrq() {
        ula.writeRegister(0xFE00, 0xFF)
        ula.interrupts.clear(UlaInterrupt.TRANSMIT_EMPTY)
        assertFalse(ula.irqAsserted)
    }

    @Test
    fun registersRepeatAcrossPageFE() {
        ula.writeRegister(0xFE36, 0x7F)
        assertEquals(0x7F, ula.sound.divider)
    }

    @Test
    fun basicAndKeyboardPagesNeedAnExternalPageBetweenThem() {
        assertEquals(10, ula.romPage)
        ula.writeRegister(0xFE05, 3)
        assertEquals(10, ula.romPage, "pages 0..7 are locked out while 8..11 is selected")
        ula.writeRegister(0xFE05, 12)
        ula.writeRegister(0xFE05, 3)
        assertEquals(3, ula.romPage)
        ula.writeRegister(0xFE05, 8)
        assertEquals(8, ula.romPage)
    }

    @Test
    fun screenStartUsesDocumentedBits() {
        ula.writeRegister(0xFE02, 0xFF)
        ula.writeRegister(0xFE03, 0xFF)
        assertEquals(0x7FC0, ula.video.screenStart)
        ula.writeRegister(0xFE02, 0x00)
        ula.writeRegister(0xFE03, 0x2C)
        assertEquals(0x5800, ula.video.screenStart)
    }

    @Test
    fun paletteMatchesAdvancedUserGuideWiring() {
        // Mode 0/4 pens 0 and 8: FE08 bit 4 blue, FE09 bits 0/4 red/green (inverted).
        ula.writeRegister(0xFE08, 0xFF)
        ula.writeRegister(0xFE09, 0xFF xor 0x01) // pen 0 red
        assertEquals(0xFF0000, ula.palette.rgb(0))
        ula.writeRegister(0xFE09, 0xFF xor 0x04) // pen 8 red
        ula.writeRegister(0xFE08, 0xFF xor 0x44) // pen 8 green, blue
        assertEquals(0xFFFFFF, ula.palette.rgb(8))
        assertEquals(0x000000, ula.palette.rgb(0))
    }

    @Test
    fun soundDividerSetsSquareWavePeriod() {
        ula.writeRegister(0xFE06, 0)
        ula.writeRegister(0xFE07, 0x02) // Communications mode 1: sound.
        assertTrue(ula.sound.enabled)
        val start = ula.sound.outputHigh
        repeat(31) { ula.tick() }
        assertEquals(start, ula.sound.outputHigh)
        ula.tick()
        assertEquals(!start, ula.sound.outputHigh)
        ula.writeRegister(0xFE07, 0x06)
        assertEquals(0, ula.sound.outputLevel)
    }

    @Test
    fun samplerResamplesSpeakerToHostRate() {
        ula.sound.startSampling(50_000) // 40 ticks per sample.
        ula.writeRegister(0xFE06, 0x7F) // Half period 4096 ticks.
        ula.writeRegister(0xFE07, 0x02)
        repeat(8192) { ula.tick() }
        val samples = FloatArray(1000)
        assertEquals(204, ula.sound.drainSamples(samples))
        assertEquals(0, ula.sound.bufferedSamples)
        assertTrue(samples.take(102).all { it == -1f })
        assertTrue(samples.slice(103 until 204).all { it == 1f })
    }

    @Test
    fun keyboardReadsActiveLowColumns() {
        val keyboard = KeyboardMatrix()
        keyboard.press(ElectronKey.A)
        keyboard.press(ElectronKey.SHIFT)
        assertEquals(0b0100, keyboard.read(0xBFFF xor (1 shl 12)))
        assertEquals(0b1000, keyboard.read(0xBFFF xor (1 shl 13)))
        assertEquals(0, keyboard.read(0xBFFF))
        assertEquals(0b1100, keyboard.read(0x8000))
    }

    @Test
    fun interruptsFireOncePerFrameAtExpectedLines() {
        ula.writeRegister(0xFE07, 6 shl 3) // Mode 6: display ends after 250 lines.
        repeat(UlaVideo.TICKS_PER_LINE * 100 - 1) { ula.tick() }
        assertEquals(0, ula.interrupts.pending and UlaInterrupt.REAL_TIME_CLOCK)
        ula.tick()
        assertEquals(UlaInterrupt.REAL_TIME_CLOCK, ula.interrupts.pending and UlaInterrupt.REAL_TIME_CLOCK)
        repeat(UlaVideo.TICKS_PER_LINE * 150 - 1) { ula.tick() }
        assertEquals(0, ula.interrupts.pending and UlaInterrupt.DISPLAY_END)
        ula.tick()
        assertEquals(UlaInterrupt.DISPLAY_END, ula.interrupts.pending and UlaInterrupt.DISPLAY_END)
    }

    @Test
    fun mode0RendersOneBitPerPixelAcrossFullWidth() {
        ula.writeRegister(0xFE07, 0)
        ula.writeRegister(0xFE02, 0x00)
        ula.writeRegister(0xFE03, 0x18) // 0x3000
        ula.writeRegister(0xFE08, 0xFF)
        ula.writeRegister(0xFE09, 0xFF xor 0x04) // pen 0 black, pen 8 red.
        ram[0x3000] = 0x80.toByte() // First pixel lit.
        ram[0x3001] = 0x01 // Second raster line, last pixel of the cell.
        runFrames(2)
        assertEquals(0xFF0000, ula.video.pixel(0, 0))
        assertEquals(0x000000, ula.video.pixel(1, 0))
        assertEquals(0xFF0000, ula.video.pixel(7, 1))
    }

    @Test
    fun spacedModesBlankTwoRasterLinesPerRow() {
        ula.writeRegister(0xFE07, 6 shl 3)
        ula.writeRegister(0xFE02, 0x00)
        ula.writeRegister(0xFE03, 0x30) // 0x6000
        ula.writeRegister(0xFE08, 0xFF)
        ula.writeRegister(0xFE09, 0xFF xor 0x04) // pen 8 red
        for (i in 0 until 0x2000) ram[0x6000 + i] = 0xFF.toByte()
        runFrames(2)
        assertEquals(0xFF0000, ula.video.pixel(0, 7))
        assertEquals(0, ula.video.pixel(0, 8))
        assertEquals(0, ula.video.pixel(0, 9))
        assertEquals(0xFF0000, ula.video.pixel(0, 10))
        assertEquals(0, ula.video.pixel(0, 250), "below the 25 text rows")
    }

    @Test
    fun wideModesContendRamOnlyDuringFetches() {
        ula.writeRegister(0xFE07, 0)
        assertTrue(ula.video.ramContended)
        repeat(UlaVideo.DISPLAY_TICKS) { ula.tick() }
        assertFalse(ula.video.ramContended, "horizontal blanking")
        ula.writeRegister(0xFE07, 4 shl 3)
        repeat(UlaVideo.TICKS_PER_LINE - UlaVideo.DISPLAY_TICKS) { ula.tick() }
        assertFalse(ula.video.ramContended, "modes 4..6 never contend")
    }

    private fun runFrames(count: Int) {
        repeat(count * 313 * UlaVideo.TICKS_PER_LINE) { ula.tick() }
    }
}
