// SPDX-FileCopyrightText: 2026 The Elkslop contributors
// SPDX-License-Identifier: BSD-3-Clause

package elkslop.cpu

/**
 * The documented opcodes, looked up by mnemonic and addressing mode, for an
 * assembler. It reads the same table the CPU and the disassembler use, so the
 * three can't disagree. The undocumented opcodes are left out: assemblers of
 * the time didn't know them.
 */
object Opcodes {
    private val byName: Map<String, Map<AddressingMode, Int>> =
        InstructionSet.instructions.withIndex()
            .filter { (_, instruction) -> instruction != null && instruction.documented }
            .groupBy({ (_, instruction) -> instruction!!.operation.name }) { (opcode, instruction) -> instruction!!.mode to opcode }
            .mapValues { (_, modes) -> modes.toMap() }

    /** The 56 documented mnemonics, such as `LDA`, in upper case. */
    val mnemonics: Set<String> get() = byName.keys

    /** The opcode for [mnemonic] (upper case) in [mode], or null if it has none. */
    fun find(mnemonic: String, mode: AddressingMode): Int? = byName[mnemonic]?.get(mode)

    /** The addressing modes [mnemonic] has; empty if it isn't one. */
    fun modes(mnemonic: String): Set<AddressingMode> = byName[mnemonic]?.keys.orEmpty()

    /** Bytes after the opcode: 0 to 2. */
    fun operandLength(mode: AddressingMode): Int = when (mode) {
        AddressingMode.IMPLIED, AddressingMode.ACCUMULATOR -> 0
        AddressingMode.ABSOLUTE, AddressingMode.ABSOLUTE_X, AddressingMode.ABSOLUTE_Y,
        AddressingMode.INDIRECT -> 2
        else -> 1
    }
}
