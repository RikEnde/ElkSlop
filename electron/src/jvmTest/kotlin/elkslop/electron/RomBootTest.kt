// SPDX-FileCopyrightText: 2026 The Elkslop contributors
// SPDX-License-Identifier: BSD-3-Clause

package elkslop.electron

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Boots the real OS and BASIC ROMs from roms/. The tests skip, with a message,
 * if the images are missing.
 */
class RomBootTest {
    private val romDirectory = File(System.getProperty("elkslop.romDirectory") ?: "roms")
    private val os = File(romDirectory, "os.rom")
    private val basic = File(romDirectory, "basic.rom")

    @Test
    fun bootsToBasicPrompt() = withMachine { machine ->
        machine.runForSeconds(2.0)
        val screen = machine.screenText()
        assertTrue(screen.any { it.startsWith("Acorn Electron") }, screen.joinToString("\n"))
        assertTrue(screen.any { it.startsWith("BASIC") }, screen.joinToString("\n"))
        assertTrue(screen.any { it == ">" }, screen.joinToString("\n"))
    }

    @Test
    fun runsTypedBasicStatement() = withMachine { machine ->
        machine.runForSeconds(2.0)
        machine.type("PRINT 6*7\r")
        machine.runForSeconds(0.5)
        val screen = machine.screenText()
        assertTrue(screen.any { it == ">PRINT 6*7" }, screen.joinToString("\n"))
        assertTrue(screen.any { it.trim() == "42" }, screen.joinToString("\n"))
        machine.saveFrame(File("build/test-frames/basic-print.png"))
    }

    @Test
    fun runsUndocumentedOpcodeFromBasic() = withMachine { machine ->
        machine.runForSeconds(2.0)
        // SAX &71: RTS. BASIC's CALL loads A and X from A% and X%.
        machine.type("?&900=&87:?&901=&71:?&902=&60:A%=&F0:X%=&3C:CALL&900:PRINT?&71\r")
        machine.runForSeconds(0.5)
        val screen = machine.screenText()
        assertTrue(screen.any { it.trim() == "48" }, screen.joinToString("\n"))
    }

    @Test
    fun rendersMode2ColourBars() = withMachine { machine ->
        machine.runForSeconds(2.0)
        machine.type("MODE2:FORC=0TO7:COLOUR128+C:PRINTSPC20;:NEXT\r")
        machine.runForSeconds(1.0)
        machine.saveFrame(File("build/test-frames/mode2-colours.png"))
        assertEquals(2, machine.ula.video.mode)
        val expected = listOf(0x000000, 0xFF0000, 0x00FF00, 0xFFFF00, 0x0000FF, 0xFF00FF, 0x00FFFF, 0xFFFFFF)
        for ((row, rgb) in expected.withIndex()) {
            assertEquals(rgb, machine.ula.video.pixel(320, row * 8 + 4), "row $row")
        }
    }

    private fun withMachine(block: (ElectronMachine) -> Unit) {
        if (!os.isFile || !basic.isFile) {
            println("Skipping: Electron ROMs not found in $romDirectory")
            return
        }
        val machine = ElectronMachine(os.readBytes(), basic.readBytes())
        machine.reset()
        block(machine)
    }

    /** Writes the last completed frame, doubling lines for a 4:3-ish aspect. */
    private fun ElectronMachine.saveFrame(file: File) {
        val image = BufferedImage(UlaVideo.WIDTH, UlaVideo.HEIGHT * 2, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until UlaVideo.HEIGHT) for (x in 0 until UlaVideo.WIDTH) {
            val rgb = ula.video.pixel(x, y)
            image.setRGB(x, y * 2, rgb)
            image.setRGB(x, y * 2 + 1, rgb)
        }
        file.parentFile.mkdirs()
        ImageIO.write(image, "png", file)
    }

    private fun ElectronMachine.runForSeconds(seconds: Double) =
        runForTicks((seconds * ElectronClock.TICKS_PER_SECOND).toInt())

    private fun ElectronMachine.type(text: String) {
        for (char in text) {
            val (key, shifted) = KEYS[char] ?: error("No key for '$char'")
            if (shifted) keyboard.press(ElectronKey.SHIFT)
            keyboard.press(key)
            runForSeconds(0.06)
            keyboard.releaseAll()
            runForSeconds(0.06)
        }
    }

    /** Decodes MODE 6 text by matching character cells against the OS font. */
    private fun ElectronMachine.screenText(): List<String> {
        check(ula.video.mode == 6) { "Expected the boot screen in MODE 6, was ${ula.video.mode}" }
        val glyphs = (0x20..0x7E).associateBy { code ->
            (0 until 8).map { bus.peek(0xC000 + (code - 0x20) * 8 + it) }
        }
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
        val KEYS: Map<Char, Pair<ElectronKey, Boolean>> = buildMap {
            for (c in 'A'..'Z') put(c, ElectronKey.valueOf(c.toString()) to false)
            for (d in '0'..'9') put(d, ElectronKey.valueOf("DIGIT_$d") to false)
            put(' ', ElectronKey.SPACE to false)
            put('\r', ElectronKey.RETURN to false)
            put('*', ElectronKey.COLON to true)
            put('"', ElectronKey.DIGIT_2 to true)
            put(':', ElectronKey.COLON to false)
            put(';', ElectronKey.SEMICOLON to false)
            put('+', ElectronKey.SEMICOLON to true)
            put('=', ElectronKey.MINUS to true)
            put(',', ElectronKey.COMMA to false)
            put('?', ElectronKey.SLASH to true)
            put('&', ElectronKey.DIGIT_6 to true)
            put('%', ElectronKey.DIGIT_5 to true)
        }
    }
}
