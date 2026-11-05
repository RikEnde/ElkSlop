// SPDX-FileCopyrightText: 2026 The Elkslop contributors
// SPDX-License-Identifier: BSD-3-Clause

package elkslop.desktop

import elkslop.electron.ElectronKey
import elkslop.electron.ElectronMachine
import java.awt.event.KeyEvent
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NaturalKeyboardTest {
    private val romDirectory = File(System.getProperty("elkslop.romDirectory") ?: "../roms")

    @Test
    fun symbolsOverrideTheHostModifiers() {
        assertEquals(
            KeyMapping.NaturalStroke(ElectronKey.MINUS, shift = true, control = false),
            stroke(KeyEvent.VK_EQUALS, '='),
        )
        assertEquals(
            KeyMapping.NaturalStroke(ElectronKey.COLON, shift = false, control = false),
            stroke(KeyEvent.VK_SEMICOLON, ':'),
        )
        assertEquals(
            KeyMapping.NaturalStroke(ElectronKey.COPY, shift = false, control = true),
            stroke(KeyEvent.VK_CLOSE_BRACKET, ']'),
        )
    }

    @Test
    fun lettersAndControlKeysFollowTheHostModifiers() {
        assertEquals(KeyMapping.NaturalStroke(ElectronKey.A), stroke(KeyEvent.VK_A, 'a'))
        assertEquals(KeyMapping.NaturalStroke(ElectronKey.A), stroke(KeyEvent.VK_A, 'A'))
        assertEquals(KeyMapping.NaturalStroke(ElectronKey.G), stroke(KeyEvent.VK_G, '\u0007', control = true))
        assertEquals(KeyMapping.NaturalStroke(ElectronKey.RETURN), stroke(KeyEvent.VK_ENTER, '\n'))
        assertEquals(KeyMapping.NaturalStroke(ElectronKey.DIGIT_1), stroke(KeyEvent.VK_1, '1', alt = true))
    }

    @Test
    fun optionComposedSymbolsDropFuncButOptionLettersKeepIt() {
        // German Mac layout: Option+5 is [. US Mac layout: Option+A is å.
        assertEquals(
            KeyMapping.NaturalStroke(ElectronKey.COPY, shift = true, control = false, func = false),
            stroke(KeyEvent.VK_5, '[', alt = true),
        )
        assertEquals(KeyMapping.NaturalStroke(ElectronKey.A), stroke(KeyEvent.VK_A, 'å', alt = true))
    }

    @Test
    fun charactersTheElectronLacksDoNothing() {
        assertNull(stroke(KeyEvent.VK_2, 'é'))
        assertNull(stroke(KeyEvent.VK_BACK_QUOTE, '`'))
        assertNull(stroke(KeyEvent.VK_OPEN_BRACKET, '\u001B', control = true))
    }

    @Test
    fun deadKeysTypeTheirSymbolAndAccentedLettersTheirKey() {
        assertEquals(ElectronKey.LEFT, stroke(KeyEvent.VK_DEAD_CIRCUMFLEX, KeyEvent.CHAR_UNDEFINED)?.key)
        assertEquals(KeyMapping.NaturalStroke(ElectronKey.E), stroke(KeyEvent.VK_E, 'é'))
    }

    @Test
    fun typesWithShiftInEitherState() {
        val rig = rig() ?: return
        rig.tap(KeyEvent.VK_EQUALS, '=')
        rig.withShift { tap(KeyEvent.VK_SEMICOLON, ':') }
        rig.withShift { tap(KeyEvent.VK_2, '@') }
        rig.withShift { tap(KeyEvent.VK_QUOTE, '"') }
        rig.withShift { tap(KeyEvent.VK_OPEN_BRACKET, '{') }
        rig.tap(KeyEvent.VK_CLOSE_BRACKET, ']')
        rig.tap(KeyEvent.VK_A, 'a')
        rig.withShift { tap(KeyEvent.VK_A, 'A') }
        assertEquals("=:@\"{]Aa", rig.typed())
    }

    @Test
    fun fastRolloverKeepsEverySymbol() {
        val rig = rig() ?: return
        for ((code, char) in listOf(KeyEvent.VK_A to 'A', KeyEvent.VK_EQUALS to '=', KeyEvent.VK_B to 'B', KeyEvent.VK_SEMICOLON to ';')) {
            // Each key goes down only a frame after the last, and goes up later.
            rig.natural.keyDown(code, KeyMapping.naturalStroke(code, char, control = false, alt = false)!!)
            rig.frames(1)
        }
        rig.frames(8)
        listOf(KeyEvent.VK_A, KeyEvent.VK_EQUALS, KeyEvent.VK_B, KeyEvent.VK_SEMICOLON).forEach(rig.natural::keyUp)
        rig.frames(10)
        assertEquals("A=B;", rig.typed().replace(Regex(";+$"), ";"))
    }

    @Test
    fun aSharedKeyIsReleasedBetweenCharacters() {
        val rig = rig() ?: return
        // - and = share a key on the Electron, and = then - go down a frame apart.
        rig.natural.keyDown(KeyEvent.VK_EQUALS, KeyMapping.naturalStroke(KeyEvent.VK_EQUALS, '=', false, false)!!)
        rig.frames(1)
        rig.natural.keyDown(KeyEvent.VK_MINUS, KeyMapping.naturalStroke(KeyEvent.VK_MINUS, '-', false, false)!!)
        rig.frames(8)
        rig.natural.keyUp(KeyEvent.VK_EQUALS)
        rig.natural.keyUp(KeyEvent.VK_MINUS)
        rig.frames(10)
        assertEquals("=-", rig.typed())
    }

    @Test
    fun hostRepeatsDoNotRetypeAndFastRepeatsTypeTwice() {
        val rig = rig() ?: return
        repeat(3) {
            rig.natural.keyDown(KeyEvent.VK_EQUALS, KeyMapping.naturalStroke(KeyEvent.VK_EQUALS, '=', false, false)!!)
            rig.frames(1)
        }
        rig.natural.keyUp(KeyEvent.VK_EQUALS)
        rig.natural.keyDown(KeyEvent.VK_EQUALS, KeyMapping.naturalStroke(KeyEvent.VK_EQUALS, '=', false, false)!!)
        rig.frames(3)
        rig.natural.keyUp(KeyEvent.VK_EQUALS)
        rig.frames(10)
        assertEquals("==", rig.typed())
    }

    @Test
    fun funcAndDigitStillGiveAFunctionKey() {
        val rig = rig() ?: return
        val typer = TextTyper()
        typer.enqueue("*KEY1 PRINT 6*7|M\n")
        while (typer.busy) {
            typer.onFrame(rig.machine)
            rig.machine.runForTicks(FRAME_TICKS)
        }
        rig.frames(10)
        rig.natural.modifier(ElectronKey.FUNC, down = true)
        rig.frames(2)
        rig.tap(KeyEvent.VK_1, '1', alt = true)
        rig.natural.modifier(ElectronKey.FUNC, down = false)
        rig.frames(10)
        assert(rig.screen().any { it.trim() == "42" }) { rig.screen().joinToString("\n") }
    }

    private fun stroke(keyCode: Int, char: Char, control: Boolean = false, alt: Boolean = false) =
        KeyMapping.naturalStroke(keyCode, char, control, alt)

    private inner class Rig(val machine: ElectronMachine) {
        val natural = NaturalKeyboard()

        fun frames(count: Int) = repeat(count) {
            natural.onFrame(machine.keyboard)
            machine.runForTicks(FRAME_TICKS)
        }

        fun tap(keyCode: Int, char: Char, alt: Boolean = false) {
            natural.keyDown(keyCode, KeyMapping.naturalStroke(keyCode, char, control = false, alt = alt)!!)
            frames(4)
            natural.keyUp(keyCode)
            frames(4)
        }

        fun withShift(action: Rig.() -> Unit) {
            natural.modifier(ElectronKey.SHIFT, down = true)
            frames(3)
            action()
            natural.modifier(ElectronKey.SHIFT, down = false)
            frames(3)
        }

        /** The input line, without its prompt and cursor. */
        fun typed() = screen().last { it.startsWith(">") }.removePrefix(">").removeSuffix("_")

        /** MODE 6 text decoded by matching cells against the OS font at &C000. */
        fun screen(): List<String> {
            val glyphs = (0x20..0x7E).associateBy { code -> (0 until 8).map { machine.bus.peek(0xC000 + (code - 0x20) * 8 + it) } }
            return (0 until 25).map { row ->
                (0 until 40).map { column ->
                    val cell = (0 until 8).map { line ->
                        var address = machine.ula.video.screenStart + row * 320 + column * 8 + line
                        if (address >= 0x8000) address -= 0x2000
                        machine.ram[address].toInt() and 0xFF
                    }
                    glyphs[cell]?.toChar() ?: '?'
                }.joinToString("").trimEnd()
            }
        }
    }

    private fun rig(): Rig? {
        val os = File(romDirectory, "os.rom")
        val basic = File(romDirectory, "basic.rom")
        if (!os.isFile || !basic.isFile) {
            println("Skipping: Electron ROMs not found in $romDirectory")
            return null
        }
        return Rig(ElectronMachine(os.readBytes(), basic.readBytes()).apply {
            reset()
            runForTicks(FRAME_TICKS * 100)
        })
    }

    private companion object {
        const val FRAME_TICKS = 40_000
    }
}
