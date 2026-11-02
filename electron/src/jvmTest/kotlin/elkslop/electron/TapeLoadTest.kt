// SPDX-FileCopyrightText: 2026 The Elkslop contributors
// SPDX-License-Identifier: BSD-3-Clause

package elkslop.electron

import java.io.File
import java.util.zip.GZIPInputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue

/**
 * Loads programs from the real Introductory Cassette images through the OS's own
 * tape filing system, comparing RAM against the file as stored on the tape.
 */
class TapeLoadTest {
    private val romDirectory = File(System.getProperty("elkslop.romDirectory") ?: "roms")
    private val tapeDirectory = File(System.getProperty("elkslop.tapeDirectory") ?: "tapes")

    @Test
    fun loadsFirstProgramFromEnglishTape() = assertLoads("intro-english.uef", "INTRO")

    @Test
    fun loadsFirstProgramFromGermanTape() = assertLoads("intro-german.uef", "WILLKOMMEN")

    private fun assertLoads(tapeName: String, program: String) {
        val image = GZIPInputStream(File(tapeDirectory, tapeName).inputStream()).use { it.readBytes() }
        val expected = fileFromTape(image, program)
        val machine = ElectronMachine(File(romDirectory, "os.rom").readBytes(), File(romDirectory, "basic.rom").readBytes())
        machine.ula.cassette.port = UefTape.parse(image)
        machine.reset()
        machine.runForSeconds(2.0)
        machine.typeLoadCommand(program)
        // 1200 baud is 120 bytes a second, plus block overheads and leader tone.
        machine.runForSeconds(expected.size / 80.0 + 15.0)
        assertTrue(!machine.ula.cassette.motorRunning, "the OS stops the motor after loading")
        val loaded = machine.ram.copyOfRange(PAGE, PAGE + expected.size)
        assertContentEquals(expected, loaded)
    }

    /** Reads a file's bytes straight out of the tape's blocks, without the emulator. */
    private fun fileFromTape(image: ByteArray, name: String): ByteArray {
        val stream = ArrayList<Byte>()
        var offset = 12
        while (offset < image.size) {
            val id = u16(image, offset)
            val size = u16(image, offset + 2) or (u16(image, offset + 4) shl 16)
            if (id == 0x0100) for (i in 0 until size) stream += image[offset + 6 + i]
            offset += 6 + size
        }
        val data = stream.toByteArray()
        val out = java.io.ByteArrayOutputStream()
        var p = 0
        while (p < data.size) {
            if (data[p].toInt() != 0x2A) { p++; continue }
            val end = (p + 1 until data.size).first { data[it].toInt() == 0 }
            val blockName = String(data, p + 1, end - p - 1, Charsets.ISO_8859_1)
            val length = u16(data, end + 11)
            val start = end + 20 // Name terminator + 19 bytes of header and CRC.
            if (blockName == name) out.write(data, start, length)
            p = start + length + if (length > 0) 2 else 0
        }
        check(out.size() > 0) { "$name is not on the tape" }
        return out.toByteArray()
    }

    private fun u16(bytes: ByteArray, at: Int) =
        (bytes[at].toInt() and 0xFF) or ((bytes[at + 1].toInt() and 0xFF) shl 8)

    private fun ElectronMachine.runForSeconds(seconds: Double) =
        runForTicks((seconds * ElectronClock.TICKS_PER_SECOND).toInt())

    private fun ElectronMachine.typeLoadCommand(program: String) {
        fun tap(key: ElectronKey, shift: Boolean = false) {
            if (shift) keyboard.press(ElectronKey.SHIFT)
            keyboard.press(key)
            runForSeconds(0.06)
            keyboard.releaseAll()
            runForSeconds(0.06)
        }
        for (c in "LOAD") tap(ElectronKey.valueOf(c.toString()))
        tap(ElectronKey.DIGIT_2, shift = true)
        for (c in program) tap(ElectronKey.valueOf(c.toString()))
        tap(ElectronKey.DIGIT_2, shift = true)
        tap(ElectronKey.RETURN)
    }

    private companion object {
        const val PAGE = 0x0E00
    }
}
