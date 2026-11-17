// SPDX-FileCopyrightText: 2026 The Elkslop contributors
// SPDX-License-Identifier: BSD-3-Clause

package elkslop.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DisassembleOptionsTest {
    @Test
    fun parsesRangesAndOrigin() {
        assertEquals(0xD940..0xD9FF, Options.parse(arrayOf("--disassemble", "D940-D9FF")).disassemble)
        assertEquals(0xFFEE..0xFFEE, Options.parse(arrayOf("--disassemble", "\$FFEE")).disassemble)
        assertEquals(0xFFEE..0xFFF0, Options.parse(arrayOf("--disassemble", "&FFEE-&FFF0")).disassemble)
        assertFailsWith<IllegalArgumentException> { Options.parse(arrayOf("--disassemble", "FFFF-0000")) }
        assertFailsWith<IllegalArgumentException> { Options.parse(arrayOf("--disassemble", "G000")) }
        assertEquals(0x0E00, Options.parse(arrayOf("--disassemble", "3000-30FF", "--origin", "0E00")).origin)
        assertFailsWith<IllegalArgumentException> { Options.parse(arrayOf("--origin", "0E00")) }
        assertFailsWith<IllegalArgumentException> { Options.parse(arrayOf("--disassemble", "3000", "--origin", "10000")) }
        assertNull(Options.parse(arrayOf()).disassemble)
    }

    @Test
    fun disassemblesTheOs() {
        val machine = runHeadless(Roms.load(null), Options(screenshotFrames = 1)).machine
        // OSWRCH, in the OS's jump table, goes through the vector WRCHV at $020E.
        assertEquals("FFEE  6C 0E 02  JMP (\$020E)", disassemble(machine, 0xFFEE..0xFFEE))
    }

    @Test
    fun runsUntilTheTextIsTyped() {
        // PRINT leaves 42 on screen; ?&70 then holds what was typed into it.
        val machine = runHeadless(Roms.load(null), Options(screenshotFrames = 1, typeText = "?&70=&EA\n")).machine
        assertEquals(0xEA, machine.bus.peek(0x70))
        assertTrue(disassemble(machine, 0x70..0x70).endsWith("NOP"))
    }

    @Test
    fun parsesBreakpoints() {
        assertEquals(listOf(0x3FB6, 0xFFEE), Options.parse(arrayOf("--break", "3FB6", "--break", "&FFEE")).breakpoints)
        assertFailsWith<IllegalArgumentException> { Options.parse(arrayOf("--break", "OSWRCH")) }
        assertEquals(emptyList(), Options.parse(arrayOf()).breakpoints)
    }

    @Test
    fun stopsAtABreakpoint() {
        val roms = Roms.load(null)
        val os = roms.os
        // The IRQ handler, from the OS's vector at $FFFE: the 50 Hz interrupts reach it soon.
        val handler = (os[0x3FFE].toInt() and 0xFF) or ((os[0x3FFF].toInt() and 0xFF) shl 8)
        val run = runHeadless(roms, Options(screenshotFrames = 50, breakpoints = listOf(handler)))
        assertEquals(handler, run.breakpoint)
        assertEquals(handler, run.machine.cpu.programCounter)
        assertEquals(elkslop.cpu.StepKind.IRQ, run.machine.cpu.lastStep)
    }

    @Test
    fun saysWhenNoBreakpointWasReached() {
        val run = runHeadless(Roms.load(null), Options(screenshotFrames = 2, breakpoints = listOf(0x7FFF)))
        assertNull(run.breakpoint)
    }
}
