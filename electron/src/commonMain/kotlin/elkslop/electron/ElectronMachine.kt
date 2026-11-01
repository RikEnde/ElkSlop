// SPDX-FileCopyrightText: 2026 The Elkslop contributors
// SPDX-License-Identifier: BSD-3-Clause

package elkslop.electron

import elkslop.cpu.Cpu6502

/** Owns one machine's hardware; UI, file loading and real-time pacing live outside. */
class ElectronMachine(
    osRom: ByteArray,
    basicRom: ByteArray,
    readback: UlaReadback = UlaReadback.ISSUE_6_FE,
) {
    val ram = ByteArray(0x8000)
    val keyboard = KeyboardMatrix()
    val ula = ElectronUla(ram)
    val clock = ElectronClock(ula)
    val bus = ElectronMemoryBus(ram, osRom, basicRom, ula, clock, keyboard, readback)
    val cpu = Cpu6502(bus)

    /** Fetch the reset vector. BREAK preserves RAM and ULA state/power-on history. */
    fun reset() {
        cpu.reset()
        cpu.irqAsserted = ula.irqAsserted
    }

    /** Execute one CPU instruction/interrupt; return elapsed machine ticks. */
    fun step(): Int {
        val before = clock.ticks
        cpu.irqAsserted = ula.irqAsserted
        cpu.step()
        cpu.irqAsserted = ula.irqAsserted
        return (clock.ticks - before).toInt()
    }

    /** Run until at least count ticks have elapsed; stops at an instruction boundary. */
    fun runForTicks(count: Int) {
        require(count >= 0)
        val start = clock.ticks
        while (clock.ticks - start < count) step()
    }

    fun requestNmi() {
        ula.notifyNmi()
        cpu.requestNmi()
    }
}
