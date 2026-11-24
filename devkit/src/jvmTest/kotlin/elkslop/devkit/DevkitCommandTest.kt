// SPDX-FileCopyrightText: 2026 The Elkslop contributors
// SPDX-License-Identifier: BSD-3-Clause

package elkslop.devkit

import elkslop.electron.UefTape
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DevkitCommandTest {
    private val directory = Files.createTempDirectory("devkit").toFile().apply { deleteOnExit() }

    private fun source(name: String, text: String) = File(directory, name).apply { writeText(text) }

    private fun run(vararg args: String): List<String> = mutableListOf<String>().also { lines -> runDevkit(args.toList(), lines::add) }

    @Test
    fun buildsSeveralSourcesOntoOneTape() {
        val loader = source("loader.bas", "PRINT \"Loading\"\n*RUN code\n")
        val code = source("code.asm", "P%=&2000\n.entry RTS\n.data EQUB 1\n")
        val tape = File(directory, "both.uef")
        val output = run("-o", tape.path, "--symbols", loader.path, code.path)
        assertTrue(output.any { it.trim() == "data                 &2001" }, output.joinToString("\n"))
        assertTrue(output.last().endsWith("loader (BASIC, 30 bytes), code (&2000-&2001, exec &2000)"), output.last())
        UefTape.parse(tape.readBytes())
    }

    @Test
    fun takesTheExecAddressFromALabel() {
        val code = source("entry.asm", "P%=&2000\n.data EQUB 1\n.entry RTS\n")
        assertEquals(0x2001, Devkit.build(code, exec = "entry").file.exec)
        assertEquals(0x3000, Devkit.build(code, exec = "&3000").file.exec)
        assertFailsWith<BuildError> { Devkit.build(code, exec = "nowhere") }
    }

    @Test
    fun buildsAWholeDirectory() {
        source("one.bas", "PRINT 1\n")
        source("two.asm", "P%=&2000\nRTS\n")
        val output = File(directory, "out")
        run("--all", directory.path, "-o", output.path)
        assertEquals(listOf("one.uef", "two.uef"), output.list()!!.sorted())
        source("one.asm", "P%=&2000\nRTS\n")
        assertFailsWith<BuildError> { run("--all", directory.path, "-o", output.path) }
    }

    @Test
    fun namesErrorsByFileAndLine() {
        val broken = source("broken.bas", "10 PRINT\n5 PRINT\n")
        val error = assertFailsWith<BuildError> { Devkit.build(broken) }
        assertEquals("${broken.path}:2: Line 5 comes after line 10; line numbers must go up", error.message)
    }

    @Test
    fun buildsTheProgramsThatComeWithIt() {
        val programs = File("../programs").listFiles { file -> file.extension in Devkit.SOURCE_EXTENSIONS }!!
        assertTrue(programs.size >= 2)
        for (program in programs) Devkit.build(program)
    }
}
