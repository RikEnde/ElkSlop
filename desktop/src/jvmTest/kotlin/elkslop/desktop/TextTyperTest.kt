// SPDX-FileCopyrightText: 2026 The Elkslop contributors
// SPDX-License-Identifier: BSD-3-Clause

package elkslop.desktop

import elkslop.electron.ElectronKey
import elkslop.electron.ElectronMachine
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TextTyperTest {
    private val romDirectory = File(System.getProperty("elkslop.romDirectory") ?: "../roms")

    @Test
    fun lettersFollowCapsLock() {
        assertEquals(KeyMapping.Stroke(ElectronKey.A, false), KeyMapping.forCharacter('A', capsLock = true))
        assertEquals(KeyMapping.Stroke(ElectronKey.A, true), KeyMapping.forCharacter('a', capsLock = true))
        assertEquals(KeyMapping.Stroke(ElectronKey.A, true), KeyMapping.forCharacter('A', capsLock = false))
        assertNull(KeyMapping.forCharacter('`', capsLock = true))
    }

    @Test
    fun typesEveryMappedCharacterIntoBasic() {
        val machine = bootedMachine() ?: return
        val symbols = "!#$%&'()@=*+<>?-:;,./^|_[]\\~{}"
        val typer = TextTyper()
        assertTrue(typer.enqueue("A\$=\"$symbols\"\nPRINT A\$\nprint 1\n").isEmpty())
        while (typer.busy) {
            typer.onFrame(machine)
            machine.runForTicks(FRAME_TICKS)
        }
        machine.runForTicks(FRAME_TICKS * 10)
        val screen = machine.screenText()
        val text = screen.joinToString("\n")
        assertTrue(screen.any { it == symbols }, text)
        // Lower case with CAPS LK on is typed with Shift; BASIC rejects it.
        assertTrue(screen.any { it == ">print 1" }, text)
    }

    @Test
    fun capsLockKeyTogglesTheLed() {
        val machine = bootedMachine() ?: return
        assertTrue(machine.ula.capsLockLed, "CAPS LK is on after boot")
        val typer = TextTyper()
        typer.enqueueChord(listOf(ElectronKey.FUNC))
        machine.drain(typer)
        assertTrue(machine.ula.capsLockLed, "CAPS LK alone is FUNC and does not toggle")
        typer.enqueueChord(listOf(ElectronKey.SHIFT, ElectronKey.FUNC))
        machine.drain(typer)
        assertFalse(machine.ula.capsLockLed)
        typer.enqueueChord(listOf(ElectronKey.SHIFT, ElectronKey.FUNC))
        machine.drain(typer)
        assertTrue(machine.ula.capsLockLed)
    }

    @Test
    fun functionKeyChordRunsKeyDefinition() {
        val machine = bootedMachine() ?: return
        val typer = TextTyper()
        typer.enqueue("*KEY1 PRINT 6*7|M\n")
        typer.enqueueChord(listOf(ElectronKey.FUNC, ElectronKey.DIGIT_1))
        machine.drain(typer)
        val screen = machine.screenText()
        assertTrue(screen.any { it == ">PRINT 6*7" }, screen.joinToString("\n"))
        assertTrue(screen.any { it.trim() == "42" }, screen.joinToString("\n"))
    }

    @Test
    fun breakChordHoldsModifierThroughReset() {
        val machine = bootedMachine() ?: return
        val typer = TextTyper()
        var resetAt = -1
        var frame = 0
        typer.enqueueChord(listOf(ElectronKey.CONTROL), holdFrames = 25) { resetAt = frame }
        val control = 1 shl 13
        val held = mutableListOf<Boolean>()
        while (typer.busy) {
            typer.onFrame(machine)
            held += machine.keyboard.read(0xBFFF xor control) and 0b0100 != 0
            frame++
        }
        assertEquals(0, resetAt)
        assertTrue(held.take(26).all { it }, "CONTROL held while the OS restarts")
        assertFalse(held.last())
    }

    private fun ElectronMachine.drain(typer: TextTyper) {
        while (typer.busy) {
            typer.onFrame(this)
            runForTicks(FRAME_TICKS)
        }
        runForTicks(FRAME_TICKS * 10)
    }

    private fun bootedMachine(): ElectronMachine? {
        val os = File(romDirectory, "os.rom")
        val basic = File(romDirectory, "basic.rom")
        if (!os.isFile || !basic.isFile) {
            println("Skipping: Electron ROMs not found in $romDirectory")
            return null
        }
        return ElectronMachine(os.readBytes(), basic.readBytes()).apply {
            reset()
            runForTicks(FRAME_TICKS * 100)
        }
    }

    /** MODE 6 text decoded by matching cells against the OS font at &C000. */
    private fun ElectronMachine.screenText(): List<String> {
        val glyphs = (0x20..0x7E).associateBy { code -> (0 until 8).map { bus.peek(0xC000 + (code - 0x20) * 8 + it) } }
        return (0 until 25).map { row ->
            (0 until 40).map { column ->
                val cell = (0 until 8).map { line ->
                    var address = ula.video.screenStart + row * 320 + column * 8 + line
                    if (address >= 0x8000) address -= 0x2000
                    ram[address].toInt() and 0xFF
                }
                glyphs[cell]?.toChar() ?: '?'
            }.joinToString("").trimEnd()
        }
    }

    private companion object {
        const val FRAME_TICKS = 40_000
    }
}
