// SPDX-FileCopyrightText: 2026 The Elkslop contributors
// SPDX-FileCopyrightText: Wilbert Pol, Nigel Barnes (portions following MAME's Electron ULA; see NOTICE)
// SPDX-License-Identifier: BSD-3-Clause

package elkslop.electron

/**
 * Electron ULA register facade. Addresses are decoded by their low four bits.
 * Call through ElectronMemoryBus for normal timed CPU accesses; direct register
 * calls are useful for hardware tests and do not advance the clock.
 */
class ElectronUla(ram: ByteArray) {
    init { require(ram.size == 0x8000) { "The Electron ULA requires 32 KiB of RAM" } }

    val interrupts = UlaInterrupts()
    val palette = UlaPalette()
    val video = UlaVideo(ram, palette, interrupts)
    val sound = UlaSound()
    val cassette = UlaCassette(interrupts)

    var romPage: Int = 10
        private set
    var control: Int = 0x06
        private set
    var cpuHasRamPriority: Boolean = false
        private set
    val capsLockLed: Boolean get() = control and 0x80 != 0
    val irqAsserted: Boolean get() = interrupts.irqAsserted

    /** Register reads with side effects (FE00 acknowledges power-on, FE04 RX). */
    fun readRegister(address: Int, unmappedValue: Int = 0xFE): Int = when (address and 0x0F) {
        0 -> interrupts.readStatus()
        4 -> cassette.readData()
        else -> unmappedValue and 0xFF
    }

    /** Debugger read: does not clear any latched event. */
    fun peekRegister(address: Int, unmappedValue: Int = 0xFE): Int = when (address and 0x0F) {
        0 -> interrupts.status
        4 -> cassette.data
        else -> unmappedValue and 0xFF
    }

    fun writeRegister(address: Int, value: Int) {
        val byte = value and 0xFF
        when (address and 0x0F) {
            0x0 -> interrupts.enable(byte)
            0x2 -> video.setStartLow(byte)
            0x3 -> video.setStartHigh(byte)
            0x4 -> cassette.writeData(byte)
            0x5 -> {
                // Internal pages 8..11 cannot be deselected by selecting 0..7.
                // Software first selects 12..15, then the desired external page.
                if (romPage !in 8..11 || byte and 0x08 != 0) romPage = byte and 0x0F
                interrupts.clearFromPagingRegister(byte)
                if (byte and 0x80 != 0) cpuHasRamPriority = false
            }
            0x6 -> sound.writeDivider(byte)
            0x7 -> {
                control = byte
                video.setMode(byte ushr 3)
                val communicationsMode = (byte ushr 1) and 3
                sound.setEnabled(communicationsMode == 1)
                cassette.configure(communicationsMode, byte and 0x40 != 0)
            }
            in 0x8..0xF -> palette.write(address and 7, byte)
        }
    }

    /** External NMI gives the CPU RAM priority until FE05 bit 7 releases it. */
    fun notifyNmi() { cpuHasRamPriority = true }

    internal fun tick() {
        video.tick()
        sound.tick()
        cassette.tick()
    }
}
