// SPDX-FileCopyrightText: 2026 The Elkslop contributors
// SPDX-License-Identifier: BSD-3-Clause

package elkslop.desktop

import elkslop.electron.UefTape
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.util.zip.GZIPInputStream

object TapeFile {
    /**
     * Reads a UEF image, gzipped (as they usually are) or plain.
     * @throws IOException if the file can't be read or isn't a UEF image.
     */
    fun load(file: File): UefTape {
        val bytes = file.readBytes()
        val gzipped = bytes.size >= 2 && bytes[0] == 0x1F.toByte() && bytes[1] == 0x8B.toByte()
        val image = if (gzipped) GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() } else bytes
        return try {
            UefTape.parse(image)
        } catch (e: IllegalArgumentException) {
            throw IOException(e.message, e)
        }
    }
}
