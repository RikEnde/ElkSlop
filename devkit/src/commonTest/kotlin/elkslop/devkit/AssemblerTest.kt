// SPDX-FileCopyrightText: 2026 The Elkslop contributors
// SPDX-License-Identifier: BSD-3-Clause

package elkslop.devkit

import elkslop.cpu.AddressingMode
import elkslop.cpu.Disassembler
import elkslop.cpu.Opcodes
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AssemblerTest {
    private fun bytes(source: String): List<Int> = Assembler.assemble(source).code.map { it.toInt() and 0xFF }

    private fun error(source: String): SourceError = assertFailsWith<SourceError> { Assembler.assemble(source) }

    @Test
    fun assemblesEveryDocumentedOpcodeAsTheDisassemblerListsIt() {
        var checked = 0
        for (opcode in 0..0xFF) {
            val memory = intArrayOf(opcode, 0x34, 0x12)
            val instruction = Disassembler({ memory.getOrElse(it - 0x1000) { 0 } }).instruction(0x1000)
            if (!instruction.documented) continue
            val text = instruction.text.replace('$', '&')
            val assembled = bytes("P%=&1000\n$text")
            assertEquals(instruction.bytes, assembled, text)
            checked++
        }
        assertEquals(151, checked)
    }

    @Test
    fun assemblesTheExampleInTheDocumentation() {
        val assembly = Assembler.assemble(
            """
            oswrch = &FFEE
            P% = &2000
            .start  LDX #0
            .loop   LDA text,X : BEQ done
                    JSR oswrch : INX : BNE loop
            .done   RTS
            .text   EQUS "Hello" : EQUB 13 : EQUB 0
            """.trimIndent(),
        )
        assertEquals(0x2000, assembly.start)
        assertEquals(
            listOf(
                0xA2, 0x00, // LDX #0
                0xBD, 0x0E, 0x20, // LDA text,X: absolute, as text is further on
                0xF0, 0x06, // BEQ done
                0x20, 0xEE, 0xFF, // JSR oswrch
                0xE8, // INX
                0xD0, 0xF5, // BNE loop
                0x60, // RTS
            ) + "Hello".map { it.code } + listOf(13, 0),
            assembly.code.map { it.toInt() and 0xFF },
        )
        assertEquals(0x2002, assembly.symbols["loop"])
        assertEquals(0xFFEE, assembly.symbols["oswrch"])
    }

    @Test
    fun choosesZeroPageWhenTheAddressIsKnownToFit() {
        assertEquals(listOf(0xA5, 0x70, 0xAD, 0x00, 0x30), bytes("zp=&70\nP%=&2000\nLDA zp\nLDA &3000"))
        // A label further on gets absolute addressing, even if it turns out to fit.
        assertEquals(listOf(0xAD, 0x03, 0x00, 0x00), bytes("P%=0\nLDA later\n.later BRK"))
        // STX zp,Y has no absolute form.
        assertEquals(listOf(0x96, 0x80), bytes("P%=&2000\nSTX &80,Y"))
    }

    @Test
    fun readsBasicExpressions() {
        val code = bytes(
            """
            P%=&1234
            .here
            LDA #here MOD 256 : LDX #here DIV256 : LDY #ASC"A"
            LDA #(3+4)*2 : LDA #&FF AND NOT 15 : LDA #-1 AND &FF : LDA #TRUE AND 7
            EQUS "Quote "" mark"+CHR$13
            EQUW here : EQUD &12345678 : EQUB 1, 2, 3
            """.trimIndent(),
        )
        assertEquals(
            listOf(0xA9, 0x34, 0xA2, 0x12, 0xA0, 0x41, 0xA9, 14, 0xA9, 0xF0, 0xA9, 0xFF, 0xA9, 7) +
                "Quote \" mark\r".map { it.code } + listOf(0x34, 0x12, 0x78, 0x56, 0x34, 0x12, 1, 2, 3),
            code,
        )
    }

    @Test
    fun readsBasicStatementsAndComments() {
        // No spaces, any case for mnemonics, \ and ; comments, P% and OPT.
        val code = bytes("P%=&900:OPT 3\n.a lda#1:STA&70 \\ store: it\nasl A ; shift\nJMP a\nJMP (P%)")
        assertEquals(listOf(0xA9, 1, 0x85, 0x70, 0x0A, 0x4C, 0x00, 0x09, 0x6C, 0x08, 0x09), code)
    }

    @Test
    fun resolvesConstantsThatDependOnLabelsFurtherOn() {
        val code = bytes("P%=&2000\nLDA #length\nlength = finish - start\n.start EQUB 0,0,0\n.finish")
        assertEquals(listOf(0xA9, 3, 0, 0, 0), code)
    }

    @Test
    fun assemblesSeparateBlocksWithGapsBetween() {
        val assembly = Assembler.assemble("P%=&2000\nRTS\nP%=&2003\nBRK")
        assertContentEquals(byteArrayOf(0x60, 0, 0, 0), assembly.code)
    }

    @Test
    fun reportsMistakesWithTheirLine() {
        assertEquals(2, error("P%=&2000\nLDA nowhere").line)
        assertTrue("nowhere" in error("P%=&2000\nLDA nowhere").reason)
        assertTrue("Set P%" in error("LDA #1").reason)
        assertTrue("isn't a byte" in error("P%=0\nLDA #256").reason)
        assertTrue("out of range" in error("P%=&2000\n.back\nP%=&2100\nBNE back").reason)
        assertTrue("already defined" in error("P%=0\n.a\n.a").reason)
        assertTrue("assembled twice" in error("P%=0\nNOP\nP%=0\nNOP").reason)
        assertTrue("Not an instruction" in error("P%=0\nFOO").reason)
        assertTrue("can't be used" in error("P%=0\nSTA #1").reason)
        assertTrue("zero page" in error("P%=0\nLDA (&1234),Y").reason)
        assertTrue("further on" in error("P%=later\n.later").reason || "set it before" in error("P%=later\n.later").reason)
        assertTrue("past &FFFF" in error("P%=&FFFF\nNOP\nNOP").reason)
        assertTrue("Nothing" in error("x=1").reason)
    }

    @Test
    fun hasEveryDocumentedMnemonic() {
        assertEquals(56, Opcodes.mnemonics.size)
        assertEquals(0xEA, Opcodes.find("NOP", AddressingMode.IMPLIED))
    }
}
