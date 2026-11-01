// SPDX-FileCopyrightText: 2026 The Elkslop contributors
// SPDX-License-Identifier: BSD-3-Clause

package elkslop.electron

/** ULA interrupt sources. Bit 0 is the computed IRQ summary, not a source. */
object UlaInterrupt {
    const val POWER_ON = 0x02
    const val DISPLAY_END = 0x04
    const val REAL_TIME_CLOCK = 0x08
    const val RECEIVE_FULL = 0x10
    const val TRANSMIT_EMPTY = 0x20
    const val HIGH_TONE = 0x40
}

/** Events latch even while disabled. Enabling a pending source asserts IRQ. */
class UlaInterrupts internal constructor() {
    var enabled: Int = 0
        private set
    var pending: Int = UlaInterrupt.POWER_ON or UlaInterrupt.TRANSMIT_EMPTY
        private set

    val irqAsserted: Boolean get() = pending and enabled and 0x7C != 0
    val status: Int get() = 0x80 or pending or if (irqAsserted) 1 else 0

    internal fun enable(value: Int) { enabled = value and 0x7C }
    internal fun raise(source: Int) { pending = pending or (source and 0x7E) }
    internal fun clear(source: Int) { pending = pending and source.inv() }

    internal fun readStatus(): Int {
        val result = status
        clear(UlaInterrupt.POWER_ON)
        return result
    }

    internal fun clearFromPagingRegister(value: Int) {
        if (value and 0x10 != 0) clear(UlaInterrupt.DISPLAY_END)
        if (value and 0x20 != 0) clear(UlaInterrupt.REAL_TIME_CLOCK)
        if (value and 0x40 != 0) clear(UlaInterrupt.HIGH_TONE)
    }
}
