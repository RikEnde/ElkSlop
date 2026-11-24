// SPDX-FileCopyrightText: 2026 The Elkslop contributors
// SPDX-License-Identifier: BSD-3-Clause

package elkslop.desktop

import elkslop.devkit.Assembler
import elkslop.devkit.BasicTokeniser
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * The devkit against the real machine: what it builds must be what the
 * Electron's own BASIC makes, and its tapes must load and run.
 */
class DevkitOnMachineTest {
    private val roms = Roms.load(null)

    private fun source(extension: String, text: String): File =
        File.createTempFile("devkit", ".$extension").apply {
            deleteOnExit()
            writeText(text)
        }

    @Test
    fun tokenisesExactlyAsBasicDoesWhenTheLinesAreTyped() {
        // Lines that exercise the tokeniser's rules; BASIC stores them as typed.
        val program = listOf(
            "10 REM Tokeniser: PRINT GOTO 20",
            "20 TIME=0:PRINT TIME, \"PRINT\" ;TAB(3);x%",
            "30 IF A=1 THEN 40 ELSE PRINT:GOTO 10",
            "40 ON B GOTO 10,20 , 30",
            "50 P.&DEF:COUNTER=1.5E3:TOP=PAGE",
            "60 PROCPRINTIT:DEF PROCPRINTIT",
            "70 *FX 200,3",
            "80 DATA PRINT,\"GOTO\",10",
            "90 FORI%=1TO10STEP2:NEXT:ENDPROC",
            "100 X=FNx(1)ANDY:RESTORE 80:GOSUB 30",
            "110 PRINT:*CAT",
            "120   LOCAL lower,case:VDU 23,1,0;0;0;0;",
        )
        val machine = runHeadless(roms, Options(screenshotFrames = 1, typeText = program.joinToString("\n", postfix = "\n"))).machine
        val expected = BasicTokeniser.tokenise(program.joinToString("\n"))
        val stored = machine.ram.copyOfRange(0x0E00, 0x0E00 + expected.size)
        assertContentEquals(expected.toList(), stored.toList())
    }

    @Test
    fun chainsABasicProgramBuiltFromSource() {
        val file = source("bas", "A% = 6 * 7\n?&70 = A%\nPROCmark\nEND\nDEF PROCmark\n?&71 = ASC\"Z\"\nENDPROC\n")
        val machine = runHeadless(roms, Options(screenshotFrames = 700, run = file)).machine
        assertEquals(42, machine.bus.peek(0x70))
        assertEquals('Z'.code, machine.bus.peek(0x71))
    }

    @Test
    fun runsMachineCodeBuiltFromSource() {
        val text = """
            oswrch = &FFEE
            P% = &2000
            .start  LDA #ASC"*" : JSR oswrch
                    LDX #0
            .loop   LDA bytes,X : STA &70,X : INX : CPX #3 : BNE loop
                    RTS
            .bytes  EQUB 1, 2, 3
        """.trimIndent()
        val file = source("asm", text)
        val command = Cassette.open(file).runCommand
        assertEquals("*LOAD ${file.nameWithoutExtension.take(10)}\nCALL &2000", command)
        // BASIC carries on after the code returns: the line after it runs.
        val machine = runHeadless(roms, Options(screenshotFrames = 1, tape = file, typeText = "$command\n?&73=9\n")).machine
        assertEquals(listOf(1, 2, 3), (0x70..0x72).map { machine.bus.peek(it) })
        assertEquals(9, machine.bus.peek(0x73))
        val code = Assembler.assemble(text).code
        assertContentEquals(code.toList(), machine.ram.copyOfRange(0x2000, 0x2000 + code.size).toList())
    }

    @Test
    fun loadsTheProgramsThatComeWithIt() {
        for (name in listOf("hello.bas", "greeting.asm")) {
            val cassette = Cassette.open(File("../programs/$name"))
            assertEquals(if (name.endsWith(".bas")) "CHAIN\"hello\"" else "*LOAD greeting\nCALL &2000", cassette.runCommand)
        }
    }

    @Test
    fun runGoesAloneAndReportsBadSources() {
        val file = source("bas", "PRINT\n")
        assertEquals(file, Options.parse(arrayOf("--run", file.path)).run)
        assertFailsWith<IllegalArgumentException> { Options.parse(arrayOf("--run", file.path, "--type", "X")) }
        assertFailsWith<IllegalArgumentException> { Options.parse(arrayOf("--run", file.path, "--tape", file.path)) }
        assertNull(Options.parse(arrayOf()).run)
        val broken = source("asm", "P%=&2000\nLDA nowhere\n")
        val error = assertFailsWith<java.io.IOException> { Cassette.open(broken) }
        assertEquals("${broken.path}:2: No such label or constant: nowhere", error.message)
    }
}
