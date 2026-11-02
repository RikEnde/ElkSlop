// SPDX-FileCopyrightText: 2026 The Elkslop contributors
// SPDX-License-Identifier: BSD-3-Clause

package elkslop.desktop

import java.io.File
import java.io.IOException
import java.util.zip.GZIPInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TapeFileTest {
    private val gzipped = File("../tapes/intro-english.uef")

    @Test
    fun loadsGzippedAndPlainImagesAlike() {
        val tape = TapeFile.load(gzipped)
        assertTrue(tape.length > 1_000_000, "a whole cassette, was ${tape.length} bits")

        val plain = File.createTempFile("elkslop", ".uef")
        try {
            plain.writeBytes(GZIPInputStream(gzipped.inputStream()).use { it.readBytes() })
            assertEquals(tape.length, TapeFile.load(plain).length)
        } finally {
            plain.delete()
        }
    }

    @Test
    fun reportsFilesThatAreNotTapes() {
        val junk = File.createTempFile("elkslop", ".uef")
        try {
            junk.writeText("not a tape, but long enough to have a header")
            assertFailsWith<IOException> { TapeFile.load(junk) }
        } finally {
            junk.delete()
        }
    }
}
