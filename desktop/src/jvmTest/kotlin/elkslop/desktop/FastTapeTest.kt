// SPDX-FileCopyrightText: 2026 The Elkslop contributors
// SPDX-License-Identifier: BSD-3-Clause

package elkslop.desktop

import elkslop.electron.ElectronMachine
import elkslop.electron.UefTape
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FastTapeTest {
    private val os = File("../roms/os.rom").readBytes()
    private val basic = File("../roms/basic.rom").readBytes()
    private val motorOn = CountDownLatch(1)
    private val motorOff = CountDownLatch(1)
    private val emulator = Emulator(
        createMachine = { ElectronMachine(os, basic) },
        soundEnabled = false,
        listener = object : Emulator.Listener {
            override fun frameReady(frameCount: Long) {}
            override fun stopped(error: Throwable) { throw AssertionError(error) }
            override fun motorChanged(on: Boolean) = (if (on) motorOn else motorOff).countDown()
        },
    )

    private fun intro(): UefTape = TapeFile.load(File("../tapes/intro-english.uef"))

    private fun startCommand(command: String) {
        emulator.type(command, delayFrames = BOOT_FRAMES)
        emulator.start()
        assertTrue(motorOn.await(30, TimeUnit.SECONDS), "the OS starts the motor")
    }

    /** Emulated seconds per wall-clock second, over the next two seconds. */
    private fun measureSpeed(): Double {
        val startFrame = emulator.frameCount
        val startTime = System.nanoTime()
        Thread.sleep(2000)
        val seconds = (System.nanoTime() - startTime) / 1e9
        return (emulator.frameCount - startFrame) / FRAMES_PER_SECOND / seconds
    }

    @Test
    fun fastLoadingLoadsTheProgramQuickly() {
        emulator.fastTape = true
        emulator.insertTape(intro())
        startCommand("LOAD\"INTRO\"\n")
        val startFrame = emulator.frameCount
        val startTime = System.nanoTime()
        try {
            assertTrue(motorOff.await(60, TimeUnit.SECONDS), "the OS stops the motor after loading")
            val seconds = (System.nanoTime() - startTime) / 1e9
            val speed = (emulator.frameCount - startFrame) / FRAMES_PER_SECOND / seconds
            println("Fast loading: ${"%.1f".format(speed)}x real time in ${"%.1f".format(seconds)} s")
            assertTrue(speed > 3, "ran at ${speed}x real time")

            // Give BASIC a moment to finish LOAD, then read its pointers.
            Thread.sleep(500)
            val result = CompletableFuture<Pair<Int, Int>>()
            emulator.post {
                val top = (it.ram[0x12].toInt() and 0xFF) or ((it.ram[0x13].toInt() and 0xFF) shl 8)
                result.complete(it.ram[PAGE].toInt() and 0xFF to top - PAGE)
            }
            val (firstByte, size) = result.get(5, TimeUnit.SECONDS)
            assertEquals(0x0D, firstByte, "a BASIC program starts with a line marker")
            assertEquals(INTRO_SIZE, size, "the whole program arrived")
        } finally {
            emulator.stop()
        }
    }

    @Test
    fun realTimeLoadingStaysAtRealTime() {
        emulator.insertTape(intro())
        startCommand("LOAD\"INTRO\"\n")
        try {
            val speed = measureSpeed()
            assertTrue(speed in 0.8..1.3, "ran at ${speed}x real time")
        } finally {
            emulator.stop()
        }
    }

    @Test
    fun fastLoadingWithNoTapeStaysAtRealTime() {
        emulator.fastTape = true
        startCommand("CHAIN\"\"\n")
        try {
            val speed = measureSpeed()
            assertTrue(speed in 0.8..1.3, "ran at ${speed}x real time")
        } finally {
            emulator.stop()
        }
    }

    private companion object {
        const val BOOT_FRAMES = 100
        const val FRAMES_PER_SECOND = 50.0
        const val PAGE = 0x0E00
        const val INTRO_SIZE = 4437
    }
}
