// SPDX-FileCopyrightText: 2026 The Elkslop contributors
// SPDX-License-Identifier: BSD-3-Clause

package elkslop.electron

/** Demodulated cassette input. SILENCE breaks framing and carrier detection. */
enum class TapeBit { ZERO, ONE, SILENCE }

/**
 * A transport adapter for decoded tape bits, such as [UefTape]. Called once per
 * bit period while the motor runs: [readBit] unless the ULA is writing to tape,
 * [writeBit] while it is. It must not re-enter the machine. This boundary
 * deliberately excludes analogue waveform demodulation and zero-crossing tolerance.
 */
interface CassettePort {
    fun readBit(): TapeBit = TapeBit.SILENCE
    fun writeBit(bit: TapeBit) {}
}

/**
 * 8N1 framing, LSB first, with receive-full, transmit-empty and carrier latches.
 * A bit lasts 1664 ticks at 2 MHz: the Electron's 16 MHz / 13 / 1024 clock.
 * This is a decoded-bit model, not an emulation of the analogue input counter.
 */
class UlaCassette internal constructor(private val interrupts: UlaInterrupts) {
    var port: CassettePort? = null
    var motorRunning: Boolean = false
        private set
    var mode: Int = 3
        private set
    var data: Int = 0
        private set

    /** The motor is running and the ULA is listening to the tape. */
    val receiving: Boolean get() = motorRunning && mode == 0

    private var bitTicks = 0
    private var receiveIndex = -1
    private var received = 0
    private var carrierBits = 0
    private var carrierReported = false
    private var transmitFrame = 0
    private var transmitBits = 0

    internal fun configure(newMode: Int, motor: Boolean) {
        if (newMode != mode) {
            bitTicks = 0
            resetReceiver()
            transmitBits = 0
            interrupts.raise(UlaInterrupt.TRANSMIT_EMPTY)
            if (newMode == 2) interrupts.raise(UlaInterrupt.RECEIVE_FULL)
        }
        if (motor != motorRunning) {
            bitTicks = 0
            resetReceiver()
        }
        mode = newMode
        motorRunning = motor
    }

    internal fun readData(): Int {
        interrupts.clear(UlaInterrupt.RECEIVE_FULL)
        return data
    }

    internal fun writeData(value: Int) {
        data = value and 0xFF
        // One start bit (0), eight data bits, one stop bit (1).
        transmitFrame = (data shl 1) or 0x200
        transmitBits = 10
        interrupts.clear(UlaInterrupt.TRANSMIT_EMPTY)
    }

    internal fun tick() {
        if (!motorRunning) return
        if (++bitTicks != BIT_TICKS) return
        bitTicks = 0
        when (mode) {
            0 -> receive(port?.readBit() ?: TapeBit.SILENCE)
            2 -> transmit()
            else -> port?.readBit() // The tape keeps moving; the ULA isn't listening.
        }
    }

    private fun receive(bit: TapeBit) {
        if (bit == TapeBit.SILENCE) {
            resetReceiver()
            return
        }
        val one = bit == TapeBit.ONE
        if (receiveIndex < 0) {
            if (one) {
                carrierBits++
                if (carrierBits >= 10 && !carrierReported) {
                    interrupts.raise(UlaInterrupt.HIGH_TONE)
                    carrierReported = true
                }
            } else {
                carrierBits = 0
                carrierReported = false
                received = 0
                receiveIndex = 0
            }
        } else if (receiveIndex < 8) {
            if (one) received = received or (1 shl receiveIndex)
            receiveIndex++
        } else {
            if (one) {
                data = received
                interrupts.raise(UlaInterrupt.RECEIVE_FULL)
            }
            receiveIndex = -1
        }
    }

    private fun transmit() {
        if (transmitBits == 0) {
            port?.writeBit(TapeBit.ONE) // Idle output is carrier.
            return
        }
        port?.writeBit(if (transmitFrame and 1 != 0) TapeBit.ONE else TapeBit.ZERO)
        transmitFrame = transmitFrame ushr 1
        if (--transmitBits == 0) interrupts.raise(UlaInterrupt.TRANSMIT_EMPTY)
    }

    private fun resetReceiver() {
        receiveIndex = -1
        received = 0
        carrierBits = 0
        carrierReported = false
    }

    companion object { const val BIT_TICKS = 1664 }
}
