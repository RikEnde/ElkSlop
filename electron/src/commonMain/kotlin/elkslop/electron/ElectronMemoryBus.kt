// SPDX-FileCopyrightText: 2026 The Elkslop contributors
// SPDX-License-Identifier: BSD-3-Clause

package elkslop.electron

import elkslop.cpu.MemoryBus

/** Reads of write-only ULA registers differ between board revisions. */
enum class UlaReadback { ISSUE_4_OS_ROM, ISSUE_6_FE }

/** Stock Electron address decoding, including the keyboard in sideways ROM space. */
class ElectronMemoryBus(
    val ram: ByteArray,
    osRom: ByteArray,
    basicRom: ByteArray,
    val ula: ElectronUla,
    val clock: ElectronClock,
    val keyboard: KeyboardMatrix,
    private val readback: UlaReadback = UlaReadback.ISSUE_6_FE,
) : MemoryBus {
    private val os = osRom.copyOf()
    private val basic = basicRom.copyOf()
    private val sideways = arrayOfNulls<ByteArray>(16)

    init {
        require(ram.size == 0x8000) { "RAM must be 32 KiB" }
        require(os.size == 0x4000) { "OS ROM must be 16 KiB" }
        require(basic.size == 0x4000) { "BASIC ROM must be 16 KiB" }
    }

    fun installRom(page: Int, image: ByteArray) {
        require(page in 0..15 && page !in 8..11) { "Pages 8..11 belong to the keyboard and BASIC" }
        require(image.size == 0x4000) { "Sideways ROM must be 16 KiB" }
        sideways[page] = image.copyOf()
    }

    override fun read(address: Int): Int {
        val location = address and 0xFFFF
        advanceForAccess(location)
        return readMapped(location, sideEffects = true)
    }

    override fun write(address: Int, value: Int) {
        val location = address and 0xFFFF
        advanceForAccess(location)
        when (location) {
            in 0x0000..0x7FFF -> ram[location] = value.toByte()
            in 0xFE00..0xFEFF -> ula.writeRegister(location, value)
            // ROM, absent expansion slots, FRED and JIM ignore writes.
        }
    }

    /** Debugger read without changing time, interrupt flags, or peripherals. */
    fun peek(address: Int): Int = readMapped(address and 0xFFFF, sideEffects = false)

    private fun readMapped(address: Int, sideEffects: Boolean): Int = when (address) {
        in 0x0000..0x7FFF -> ram[address].toInt() and 0xFF
        in 0x8000..0xBFFF -> when (ula.romPage) {
            8, 9 -> keyboard.read(address)
            10, 11 -> basic[address and 0x3FFF].toInt() and 0xFF
            else -> sideways[ula.romPage]?.get(address and 0x3FFF)?.toInt()?.and(0xFF) ?: 0xFF
        }
        in 0xFC00..0xFDFF -> 0xFF
        in 0xFE00..0xFEFF -> {
            val unmapped = if (readback == UlaReadback.ISSUE_6_FE) 0xFE else os[address and 0x3FFF].toInt() and 0xFF
            if (sideEffects) ula.readRegister(address, unmapped) else ula.peekRegister(address, unmapped)
        }
        else -> os[address and 0x3FFF].toInt() and 0xFF
    }

    private fun advanceForAccess(address: Int) {
        when {
            address < 0x8000 -> clock.accessRam()
            address in 0xFC00..0xFEFF -> clock.accessSlowDevice()
            address < 0xC000 && ula.romPage in 8..9 -> clock.accessSlowDevice()
            else -> clock.accessRom()
        }
    }
}
