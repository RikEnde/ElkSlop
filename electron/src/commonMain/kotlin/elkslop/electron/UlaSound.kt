// SPDX-FileCopyrightText: 2026 The Elkslop contributors
// SPDX-License-Identifier: BSD-3-Clause

package elkslop.electron

import kotlin.math.PI
import kotlin.math.exp

/** One-bit speaker oscillator. Time is measured in 2 MHz machine ticks. */
class UlaSound internal constructor() {
    var divider: Int = 0
        private set
    var enabled: Boolean = false
        private set
    var outputHigh: Boolean = false
        private set
    private var ticksRemaining = 32

    // A full wave is 64 * (divider + 1) ticks: 31,250 / (divider + 1) Hz.
    // This agrees with measured hardware and MAME; the Electron Advanced User
    // Guide's formula gives twice this.
    val halfPeriodTicks: Int get() = 32 * (divider + 1)
    val outputLevel: Int get() = if (!enabled) 0 else if (outputHigh) 1 else -1

    // Optional host-rate resampling. Each output sample is the mean speaker
    // level over the ticks it covers, which acts as a simple anti-alias filter.
    private var sampleRate = 0
    private var samples = FloatArray(0)
    private var sampleCount = 0
    private var phase = 0
    private var levelSum = 0
    private var levelTicks = 0

    // A one-pole low-pass, for the Electron's small speaker, which never played
    // the square wave's sharpest harmonics. 0 leaves the wave as it is.
    private var smoothing = 1f
    private var filtered = 0f

    /** Samples available to [drainSamples]. */
    val bufferedSamples: Int get() = sampleCount

    /**
     * Start producing samples in -1..1 at [rate] Hz. Up to [capacity] samples
     * are buffered; if the host does not drain them, newer samples are dropped.
     * [lowPassHz] softens the wave as the speaker did; 0 turns that off.
     */
    fun startSampling(rate: Int, capacity: Int = rate / 5, lowPassHz: Int = SPEAKER_LOW_PASS_HZ) {
        require(rate in 1..ElectronClock.TICKS_PER_SECOND && capacity > 0 && lowPassHz >= 0)
        smoothing = if (lowPassHz == 0) 1f else (1 - exp(-2 * PI * lowPassHz / rate)).toFloat()
        filtered = 0f
        sampleRate = rate
        samples = FloatArray(capacity)
        sampleCount = 0
        phase = 0
        levelSum = 0
        levelTicks = 0
    }

    /** Move buffered samples into [destination]; returns how many were copied. */
    fun drainSamples(destination: FloatArray): Int {
        val count = minOf(sampleCount, destination.size)
        samples.copyInto(destination, 0, 0, count)
        samples.copyInto(samples, 0, count, sampleCount)
        sampleCount -= count
        return count
    }

    internal fun setEnabled(value: Boolean) {
        if (enabled == value) return
        enabled = value
        ticksRemaining = halfPeriodTicks
    }

    // The new pitch starts at the next edge. The OS rewrites the divider every
    // centisecond while a note plays, so restarting the count here would
    // break the wave 100 times a second and buzz.
    internal fun writeDivider(value: Int) {
        divider = value and 0xFF
    }

    internal fun tick() {
        if (enabled && --ticksRemaining == 0) {
            outputHigh = !outputHigh
            ticksRemaining = halfPeriodTicks
        }
        if (sampleRate != 0) sample()
    }

    private fun sample() {
        levelSum += outputLevel
        levelTicks++
        phase += sampleRate
        if (phase < ElectronClock.TICKS_PER_SECOND) return
        phase -= ElectronClock.TICKS_PER_SECOND
        filtered += smoothing * (levelSum.toFloat() / levelTicks - filtered)
        if (sampleCount < samples.size) samples[sampleCount++] = filtered
        levelSum = 0
        levelTicks = 0
    }

    companion object {
        /** Where the speaker filter starts to cut: a guess, tuned by ear. */
        const val SPEAKER_LOW_PASS_HZ = 4000
    }
}
