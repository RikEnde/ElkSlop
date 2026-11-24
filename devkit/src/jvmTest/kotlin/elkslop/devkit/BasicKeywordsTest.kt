// SPDX-FileCopyrightText: 2026 The Elkslop contributors
// SPDX-License-Identifier: BSD-3-Clause

package elkslop.devkit

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/** Checks the keyword table against the one in the BASIC ROM, roms/basic.rom. */
class BasicKeywordsTest {
    private val rom = File(System.getProperty("elkslop.romDirectory") ?: "roms", "basic.rom").readBytes()

    @Test
    fun matchesTheTableInTheBasicRom() {
        // BASIC II's table starts at &8071: each keyword, its token (top bit set),
        // then its flags. The tokeniser searches it as far as WIDTH.
        val fromRom = mutableListOf<String>()
        var offset = 0x71
        while (true) {
            val start = offset
            while (rom[offset].toInt() and 0x80 == 0) offset++
            val text = String(rom, start, offset - start, Charsets.US_ASCII)
            val token = rom[offset].toInt() and 0xFF
            val flags = rom[offset + 1].toInt() and 0xFF
            offset += 2
            fromRom += "%s %02X %02X".format(text, token, flags)
            if (text == "WIDTH") break
        }
        assertEquals(fromRom, BasicKeywords.all.map { "%s %02X %02X".format(it.text, it.token, it.flags) })
    }
}
