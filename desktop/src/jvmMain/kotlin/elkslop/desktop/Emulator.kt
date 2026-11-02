// SPDX-FileCopyrightText: 2026 The Elkslop contributors
// SPDX-License-Identifier: BSD-3-Clause

package elkslop.desktop

import elkslop.electron.ElectronClock
import elkslop.electron.ElectronKey
import elkslop.electron.ElectronMachine
import elkslop.electron.UefTape
import elkslop.electron.UlaVideo
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Runs one Electron on a dedicated thread in real time.
 *
 * Only the emulation thread touches the machine. Other threads talk to it
 * through [post], and read video through [copyFrame]. With sound on, the
 * blocking audio device sets the pace (its sample clock is the master clock);
 * without sound, the thread sleeps to match emulated time to wall time.
 */
class Emulator(
    private val createMachine: () -> ElectronMachine,
    private val soundEnabled: Boolean,
    private val listener: Listener,
) {
    interface Listener {
        /** Called on the emulation thread after each completed frame. */
        fun frameReady(frameCount: Long)

        /** Called on the emulation thread when the machine stops on an error. */
        fun stopped(error: Throwable)

        /** Called on the emulation thread when the CAPS LK LED changes. */
        fun capsLockChanged(on: Boolean) {}

        /** Called on the emulation thread when the cassette motor starts or stops. */
        fun motorChanged(on: Boolean) {}
    }

    private val commands = ConcurrentLinkedQueue<(ElectronMachine) -> Unit>()
    private val frameLock = Any()
    private val published = IntArray(UlaVideo.WIDTH * UlaVideo.HEIGHT)
    private val typer = TextTyper()
    private var machine = createMachine().also { it.reset() }
    private var thread: Thread? = null
    private var capsLockLed: Boolean? = null
    private var motorRunning = false
    private var tape: UefTape? = null

    @Volatile private var running = false
    @Volatile var paused = false
    @Volatile var volume = 0.25f
    @Volatile var frameCount = 0L
        private set

    val typing: Boolean get() = typer.busy

    fun start() {
        check(thread == null)
        running = true
        thread = Thread(::run, "electron-emulation").apply {
            isDaemon = true
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    fun stop() {
        running = false
        thread?.join(1000)
        thread = null
    }

    /** Run [command] on the emulation thread before the next frame. */
    fun post(command: (ElectronMachine) -> Unit) { commands += command }

    fun press(key: ElectronKey) = post { it.keyboard.press(key) }
    fun release(key: ElectronKey) = post { it.keyboard.release(key) }
    fun releaseAll() = post { it.keyboard.releaseAll() }

    /** BREAK key: CPU reset; RAM survives, so BASIC programs can be OLDed. */
    fun pressBreak() = post { it.reset() }

    /**
     * BREAK with [modifier] held, as the OS reads it while restarting:
     * CONTROL gives a hard reset (memory cleared), SHIFT an autoboot.
     */
    fun pressBreakWith(modifier: ElectronKey) = post {
        typer.enqueueChord(listOf(modifier), holdFrames = MODIFIER_BREAK_FRAMES) { machine -> machine.reset() }
    }

    /** Press keys together briefly, for keys a menu click can't hold down. */
    fun tap(vararg keys: ElectronKey) = post { typer.enqueueChord(keys.toList()) }

    /** Switch off and on: fresh RAM, ULA power-on state, and cold OS start. */
    fun powerCycle() = post {
        typer.cancel(it)
        machine = createMachine().also { fresh ->
            fresh.reset()
            fresh.ula.cassette.port = tape // The cassette stays in the recorder.
        }
        paused = false
    }

    /** Put a cassette in the recorder, wound to its start. */
    fun insertTape(cassette: UefTape) = post {
        tape = cassette
        it.ula.cassette.port = cassette
    }

    fun ejectTape() = post {
        tape = null
        it.ula.cassette.port = null
    }

    fun rewindTape() = post { tape?.rewind() }

    /** Characters the Electron cannot type are skipped and returned. */
    fun type(text: String, delayFrames: Int = 0): Set<Char> {
        val skipped = text.filter { it != '\t' && KeyMapping.forCharacter(it, capsLock = true) == null }
        post { typer.enqueue(text, delayFrames) }
        return skipped.toSet()
    }

    fun cancelTyping() = post { typer.cancel(it) }

    fun copyFrame(destination: IntArray) {
        synchronized(frameLock) { published.copyInto(destination) }
    }

    private fun run() {
        val audio = if (soundEnabled) AudioOutput.open(SAMPLE_RATE, SAMPLE_RATE / 15) else null
        val samples = FloatArray(SAMPLE_RATE / 5)
        var soundMachine: ElectronMachine? = null
        var paceStart = System.nanoTime()
        var paceTicks = 0L

        try {
            while (running) {
                while (true) commands.poll()?.invoke(machine) ?: break
                if (machine !== soundMachine) {
                    machine.ula.sound.startSampling(SAMPLE_RATE, samples.size)
                    soundMachine = machine
                }
                if (paused) {
                    Thread.sleep(10)
                    paceStart = System.nanoTime()
                    paceTicks = 0
                    continue
                }

                val elapsed = try {
                    runFrame()
                } catch (e: Exception) {
                    paused = true
                    listener.stopped(e)
                    continue
                }

                val count = machine.ula.sound.drainSamples(samples)
                if (audio != null) {
                    audio.write(samples, count, volume)
                } else {
                    paceTicks += elapsed
                    val target = paceStart + paceTicks * NANOS_PER_TICK
                    val ahead = target - System.nanoTime()
                    if (ahead > 0) {
                        Thread.sleep(ahead / 1_000_000, (ahead % 1_000_000).toInt())
                    } else if (ahead < -100_000_000) {
                        // Fell far behind (host stall): don't try to catch up.
                        paceStart = System.nanoTime()
                        paceTicks = 0
                    }
                }
            }
        } finally {
            audio?.close()
        }
    }

    /** Runs until the ULA completes a frame; returns the elapsed machine ticks. */
    private fun runFrame(): Long {
        val machine = machine
        typer.onFrame(machine)
        val video = machine.ula.video
        val frame = video.frameNumber
        val start = machine.clock.ticks
        while (video.frameNumber == frame) machine.step()
        synchronized(frameLock) { video.copyFrameInto(published) }
        listener.frameReady(++frameCount)
        val caps = machine.ula.capsLockLed
        if (caps != capsLockLed) {
            capsLockLed = caps
            listener.capsLockChanged(caps)
        }
        val motor = machine.ula.cassette.motorRunning
        if (motor != motorRunning) {
            motorRunning = motor
            listener.motorChanged(motor)
        }
        if (machine.cpu.halted) {
            // Like the real machine, a jammed CPU leaves the display running and
            // only BREAK recovers it. Pause so the user knows why it froze.
            paused = true
            listener.stopped(IllegalStateException(
                "The 6502 executed a JAM opcode at &%04X and halted.".format(machine.cpu.programCounter - 1),
            ))
        }
        return machine.clock.ticks - start
    }

    companion object {
        const val SAMPLE_RATE = 48_000
        private const val MODIFIER_BREAK_FRAMES = 25
        private const val NANOS_PER_TICK = 1_000_000_000L / ElectronClock.TICKS_PER_SECOND
    }
}
