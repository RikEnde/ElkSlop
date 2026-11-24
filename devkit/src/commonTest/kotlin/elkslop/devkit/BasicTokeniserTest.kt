// SPDX-FileCopyrightText: 2026 The Elkslop contributors
// SPDX-License-Identifier: BSD-3-Clause

package elkslop.devkit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BasicTokeniserTest {
    /** One line's text as hex, after its line number, as BASIC stores it. */
    private fun line(text: String): String =
        BasicTokeniser.tokeniseLine(text).joinToString(" ") { (it.toInt() and 0xFF).toString(16).uppercase().padStart(2, '0') }

    private fun hex(text: String): String =
        text.map { it.code.toString(16).uppercase().padStart(2, '0') }.joinToString(" ")

    @Test
    fun storesLinesWithNumberLengthAndEndMarker() {
        val program = BasicTokeniser.tokenise("10 PRINT\n20 END\n")
        assertEquals(
            listOf(0x0D, 0x00, 0x0A, 0x06, 0x20, 0xF1, 0x0D, 0x00, 0x14, 0x06, 0x20, 0xE0, 0x0D, 0xFF),
            program.map { it.toInt() and 0xFF },
        )
    }

    @Test
    fun numbersLinesThatHaveNone() {
        val program = BasicTokeniser.tokenise("PRINT\n\n  \nEND\n100 STOP\nCLS")
        val numbers = mutableListOf<Int>()
        var offset = 0
        while (program[offset + 1].toInt() != -1) {
            numbers += ((program[offset + 1].toInt() and 0xFF) shl 8) or (program[offset + 2].toInt() and 0xFF)
            offset += program[offset + 3].toInt() and 0xFF
        }
        assertEquals(listOf(10, 20, 100, 110), numbers)
    }

    @Test
    fun refusesLineNumbersThatDontGoUp() {
        val error = assertFailsWith<SourceError> { BasicTokeniser.tokenise("20 PRINT\n10 END") }
        assertEquals(2, error.line)
        assertFailsWith<SourceError> { BasicTokeniser.tokenise("40000 END") }
        assertFailsWith<SourceError> { BasicTokeniser.tokenise("PRINT \"é\"") }
    }

    @Test
    fun encodesLineNumbersAfterGotoLikeBasic() {
        // GOTO 10 is &E5 &8D &54 &4A &40, as every BBC BASIC listing shows.
        assertEquals("E5 20 8D 54 4A 40", line("GOTO 10"))
        assertEquals("EE 58 20 E5 8D 54 4A 40 2C 8D 54 54 40", line("ONX GOTO10,20"))
        assertEquals("E7 20 41 20 8C 20 8D 64 68 7F", line("IF A THEN 16360"))
        // A number after the line number is a line number too, as when typed.
        assertEquals("8D 54 4A 40", line("10"))
        assertEquals("F1 20 31 30", line("PRINT 10"))
    }

    @Test
    fun pseudoVariablesHaveTheirOwnTokenAtTheStartOfAStatement() {
        assertEquals("D1 3D 30 3A F1 91", line("TIME=0:PRINTTIME"))
        assertEquals("E7 20 41 20 8C 20 D1 3D 30", line("IF A THEN TIME=0"))
    }

    @Test
    fun followsBasicsRulesForWhatIsAKeyword() {
        assertEquals("F1 58", line("PRINTX"))
        // COUNT is only a keyword on its own; TOP is TO and P, as in BASIC.
        assertEquals(hex("COUNTER"), line("COUNTER"))
        assertEquals("B8 50", line("TOP"))
        assertEquals("80 59", line("ANDY"))
        assertEquals("F1 20 " + hex("print"), line("PRINT print"))
        assertEquals("F1 20 F1", line("P. PRINT"))
        assertEquals("F2 " + hex("PRINTIT"), line("PROCPRINTIT"))
        assertEquals("F1 20 " + hex("&DEF"), line("PRINT &DEF"))
        assertEquals("F1 20 " + hex("\"PRINT\""), line("PRINT \"PRINT\""))
        assertEquals("F4 " + hex(" PRINT: GOTO 10"), line("REM PRINT: GOTO 10"))
        assertEquals(hex("*TAPE PRINT"), line("*TAPE PRINT"))
        assertEquals("F1 3A " + hex("*FX"), line("PRINT:*FX"))
    }

    @Test
    fun keepsThePoundSign() {
        assertEquals("F1 20 " + hex("\"`\""), line("PRINT \"`\""))
        assertEquals(BasicTokeniser.tokenise("PRINT \"`\"").toList(), BasicTokeniser.tokenise("PRINT \"£\"").toList())
    }
}
