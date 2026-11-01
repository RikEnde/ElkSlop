// SPDX-FileCopyrightText: 2026 The Elkslop contributors
// SPDX-License-Identifier: BSD-3-Clause

package elkslop.electron

/**
 * Deterministic machine time in 0.5-microsecond (2 MHz) ticks, not CPU cycles.
 * Video, sound and cassette keep running while the CPU waits for RAM.
 */
class ElectronClock(private val ula: ElectronUla) {
    var ticks: Long = 0
        private set

    /** Advance peripherals without executing the CPU (also useful in tests). */
    fun advance(count: Int) {
        require(count >= 0)
        repeat(count) {
            ula.tick()
            ticks++
        }
    }

    internal fun accessRam() {
        synchronizeSlowClock()
        while (!ula.cpuHasRamPriority && ula.video.ramContended) advance(2)
        advance(2)
    }

    internal fun accessSlowDevice() {
        synchronizeSlowClock()
        advance(2)
    }

    internal fun accessRom() { advance(1) }

    private fun synchronizeSlowClock() {
        if (ticks and 1L != 0L) advance(1)
    }

    companion object { const val TICKS_PER_SECOND = 2_000_000 }
}
