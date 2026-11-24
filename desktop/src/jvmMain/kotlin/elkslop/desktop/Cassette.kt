// SPDX-FileCopyrightText: 2026 The Elkslop contributors
// SPDX-License-Identifier: BSD-3-Clause

package elkslop.desktop

import elkslop.devkit.BuildError
import elkslop.devkit.Devkit
import elkslop.electron.UefTape
import java.io.File
import java.io.IOException

/** A tape to put in the recorder, and what to type to run the program on it. */
class Cassette(val tape: UefTape, val runCommand: String) {
    companion object {
        /**
         * [file] as a tape. A BBC BASIC (.bas) or assembler (.asm) source is
         * built onto a tape first, and runs with `CHAIN"NAME"`, or `*LOAD NAME`
         * and `CALL`;
         * anything else is read as a UEF image, and runs with `CHAIN""`, which
         * loads the first program on the tape.
         *
         * @throws IOException if the file can't be read, built or isn't a tape.
         */
        fun open(file: File): Cassette {
            if (file.extension.lowercase() !in Devkit.SOURCE_EXTENSIONS) {
                return Cassette(TapeFile.load(file), "CHAIN\"\"")
            }
            val program = try {
                Devkit.build(file)
            } catch (e: BuildError) {
                throw IOException(e.message, e)
            }
            return Cassette(UefTape.parse(Devkit.tape(listOf(program))), program.file.runCommand)
        }
    }
}
