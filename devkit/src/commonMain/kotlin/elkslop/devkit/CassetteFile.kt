// SPDX-FileCopyrightText: 2026 The Elkslop contributors
// SPDX-License-Identifier: BSD-3-Clause

package elkslop.devkit

/**
 * A file as the cassette filing system stores it: a [name] of up to 10
 * characters, the address it [load]s at, the address it starts at ([exec]),
 * and its [data]. Addresses are 16 bits; on tape they are 32, with &FFFF on
 * top, as the Electron writes them, meaning the computer's own memory rather
 * than a second processor's.
 */
class CassetteFile(val name: String, val load: Int, val exec: Int, val data: ByteArray) {
    init {
        require(name.length in 1..MAX_NAME_LENGTH) { "A tape file name is 1 to $MAX_NAME_LENGTH characters, not \"$name\"" }
        require(name.all { it in '!'..'~' && it != '"' }) { "A tape file name has no spaces, quotes or odd characters: \"$name\"" }
        require(load in 0..0xFFFF && exec in 0..0xFFFF) { "Load and exec addresses are 16 bits" }
        require(data.size <= 0x10000 - load) { "\"$name\" runs past &FFFF" }
    }

    /** True for a BASIC program, which `CHAIN` loads at PAGE. */
    val isBasic: Boolean get() = load == PAGE && exec == BASIC_EXEC

    /**
     * What to type at BASIC's prompt to load and run it from tape: `CHAIN"NAME"`,
     * or for machine code `*LOAD NAME` and then `CALL &2000` on a second line.
     *
     * Not `*RUN`: in the Electron's OS 1.00, `*RUN` from tape writes the file's
     * details through a pointer at &C8 that only OSFILE sets, so it overwrites
     * BASIC's zero page, and code that returns gets "Syntax error". `CALL` also
     * passes A%, X% and Y% in the registers, as BASIC programmers expect.
     */
    val runCommand: String
        get() = if (isBasic) "CHAIN\"$name\"" else "*LOAD $name\nCALL &" + exec.toString(16).uppercase().padStart(4, '0')

    companion object {
        const val MAX_NAME_LENGTH = 10

        /** Where the Electron's BASIC keeps its program: PAGE. */
        const val PAGE = 0x0E00

        /** BASIC's language entry: what `SAVE` records as the exec address. */
        const val BASIC_EXEC = 0x8023

        /** A tokenised program from [BasicTokeniser], as BASIC's `SAVE` would write it. */
        fun basic(name: String, program: ByteArray) = CassetteFile(name, PAGE, BASIC_EXEC, program)

        /** Assembled code, loaded where it was assembled for and started at [exec]. */
        fun machineCode(name: String, assembly: Assembly, exec: Int = assembly.start) =
            CassetteFile(name, assembly.start, exec, assembly.code)
    }
}
