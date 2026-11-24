// SPDX-FileCopyrightText: 2026 The Elkslop contributors
// SPDX-License-Identifier: BSD-3-Clause

package elkslop.devkit

import elkslop.cpu.AddressingMode
import elkslop.cpu.AddressingMode.ABSOLUTE
import elkslop.cpu.AddressingMode.ABSOLUTE_X
import elkslop.cpu.AddressingMode.ABSOLUTE_Y
import elkslop.cpu.AddressingMode.ACCUMULATOR
import elkslop.cpu.AddressingMode.IMMEDIATE
import elkslop.cpu.AddressingMode.IMPLIED
import elkslop.cpu.AddressingMode.INDEXED_INDIRECT
import elkslop.cpu.AddressingMode.INDIRECT
import elkslop.cpu.AddressingMode.INDIRECT_INDEXED
import elkslop.cpu.AddressingMode.RELATIVE
import elkslop.cpu.AddressingMode.ZERO_PAGE
import elkslop.cpu.AddressingMode.ZERO_PAGE_X
import elkslop.cpu.AddressingMode.ZERO_PAGE_Y
import elkslop.cpu.Opcodes

/** Machine code from [Assembler.assemble]: [code] runs from [start]. [symbols] are its labels and constants. */
class Assembly(val start: Int, val code: ByteArray, val symbols: Map<String, Int>) {
    /** The address after the last byte. */
    val end: Int get() = start + code.size
}

/**
 * A 6502 assembler for the syntax of BBC BASIC's inline assembler, so code
 * moves between a BASIC program's `[ ... ]` and a source file unchanged:
 *
 * ```
 * oswrch = &FFEE
 * P% = &2000
 * .start  LDX #0
 * .loop   LDA text,X : BEQ done
 *         JSR oswrch : INX : BNE loop
 * .done   RTS
 * .text   EQUS "Hello" : EQUB 13 : EQUB 0
 * ```
 *
 * - `.name` is a label, at the address in P%. It can share a line with an instruction.
 * - `name = expression` sets a constant, and `P% = expression` sets where code
 *   goes next, as BASIC does outside the brackets. Set P% before the first
 *   instruction. P% can be set again to start another block.
 * - `:` separates statements. `\` or `;` starts a comment, which runs to the
 *   end of the line (in BASIC it stops at the next `:`).
 * - Operands are BASIC expressions; see [Expression]. Mnemonics, `A` for the
 *   accumulator and `X` and `Y` may be any case; labels are case sensitive.
 * - `EQUB`, `EQUW` and `EQUD` store 1, 2 and 4 bytes, `EQUS` a string. Each can
 *   take a list separated by commas, which BASIC's can't.
 * - `OPT` is accepted and ignored: there is no listing, and every pass is checked.
 * - The documented opcodes only, as BASIC's.
 *
 * Two passes, as in BASIC with `FOR pass = 0 TO 3 STEP 3`, so a label can be
 * used before it is defined. An operand that fits in a byte in the first pass
 * gets zero page addressing; one that names a label further on gets absolute,
 * and keeps it, so the code doesn't move between passes. Unlike BASIC, which
 * lets a variable change, each name can be set once (P% apart), and every
 * mistake is an error with its line number.
 */
object Assembler {
    /**
     * @throws SourceError for the first mistake found.
     */
    fun assemble(source: String): Assembly {
        val statements = split(source)
        // The first pass fixes every instruction's size, so labels don't move
        // after it. More passes work out constants that depend on labels further
        // on; the last one checks that everything is known.
        var pass = Pass(null, strict = false).apply { run(statements) }
        for (count in 2..MAX_PASSES) {
            if (pass.unresolvedUses == 0) break
            val next = Pass(pass, strict = false).apply { run(statements) }
            if (next.unresolvedUses >= pass.unresolvedUses) break
            pass = next
        }
        return Pass(pass, strict = true).apply { run(statements) }.result()
    }

    private const val MAX_PASSES = 16

    private class Statement(val line: Int, val text: String)

    /** Every statement in [source], in order, without comments. */
    private fun split(source: String): List<Statement> {
        val statements = mutableListOf<Statement>()
        for ((index, line) in source.lines().withIndex()) {
            var inString = false
            var start = 0
            fun add(end: Int) {
                val text = line.substring(start, end).trim()
                if (text.isNotEmpty()) statements += Statement(index + 1, text)
            }
            var i = 0
            while (i < line.length) {
                val c = line[i]
                when {
                    c == '"' -> inString = !inString
                    inString -> {}
                    c == ':' -> { add(i); start = i + 1 }
                    c == '\\' || c == ';' -> break
                }
                i++
            }
            add(i)
        }
        return statements
    }

    /**
     * One pass over the source. Each after the first gets the one before, for
     * the names further on and the first pass's choices of mode. A [strict]
     * pass reports any name that is still unknown.
     */
    private class Pass(private val previous: Pass?, private val strict: Boolean) {
        val names = mutableMapOf<String, Int>()
        private val definedAt = mutableMapOf<String, Int>()
        val unresolved = mutableSetOf<String>()
        private var pc: Int? = null

        /** Expressions that used a name not yet known. */
        var unresolvedUses = 0
            private set

        /** The mode each instruction got, in order: the second pass keeps the first's. */
        val modes = mutableListOf<AddressingMode>()
        private var instructionCount = 0

        private val memory = IntArray(0x10000) { -1 }
        private var line = 0

        fun run(statements: List<Statement>) {
            for (statement in statements) {
                line = statement.line
                try {
                    statement(statement.text)
                } catch (e: IllegalArgumentException) {
                    throw SourceError(line, e.message ?: "Syntax error")
                }
            }
        }

        fun result(): Assembly {
            val start = memory.indexOfFirst { it >= 0 }
            if (start < 0) throw SourceError(line, "Nothing was assembled")
            val end = memory.indexOfLast { it >= 0 } + 1
            val code = ByteArray(end - start) { memory[start + it].coerceAtLeast(0).toByte() }
            return Assembly(start, code, names.filterKeys { it != "P%" })
        }

        private fun statement(text: String) {
            var rest = text
            while (rest.startsWith('.')) {
                val name = rest.drop(1).takeWhile { Expression.isNameChar(it) }
                    .let { if (rest.getOrNull(it.length + 1) == '%') "$it%" else it }
                if (name.isEmpty() || !Expression.isNameStart(name[0])) throw IllegalArgumentException("Bad label name after '.'")
                define(name, currentPc(), resolved = true)
                rest = rest.drop(1 + name.length).trimStart()
            }
            if (rest.isEmpty()) return
            ASSIGNMENT.matchEntire(rest)?.let { match ->
                assign(match.groupValues[1], match.groupValues[2])
                return
            }
            val upper = rest.uppercase()
            when {
                upper.startsWith("EQU") && rest.length >= 4 && upper[3] in "BWDS" -> data(upper[3], rest.substring(4))
                upper.startsWith("OPT") -> evaluate(rest.substring(3))
                rest.length >= 3 && upper.substring(0, 3) in Opcodes.mnemonics &&
                    (rest.length == 3 || !Expression.isNameChar(rest[3]) || rest[3].uppercaseChar() in "AXY") ->
                    instruction(upper.substring(0, 3), rest.substring(3).trim())
                else -> throw IllegalArgumentException("Not an instruction: $rest")
            }
        }

        private fun assign(name: String, expression: String) {
            val value = Expression(expression, ::lookup)
            val number = value.number()
            if (name == "P%") {
                // Code can't move between passes, so P% must be known in the first.
                if (value.unresolved) {
                    throw IllegalArgumentException("P% can't use ${value.unknownNames.first()}: set it before this line")
                }
                if (number !in 0..0xFFFF) throw IllegalArgumentException("P% must be from &0000 to &FFFF, not ${hex(number)}")
                pc = number
                return
            }
            if (value.unresolved) {
                if (strict) unknown(value)
                unresolvedUses++
            }
            define(name, number, resolved = !value.unresolved)
        }

        private fun define(name: String, value: Int, resolved: Boolean) {
            if (name == "P%") throw IllegalArgumentException("P% can't be a label")
            definedAt[name]?.let { throw IllegalArgumentException("$name is already defined, on line $it") }
            definedAt[name] = line
            names[name] = value
            if (!resolved) unresolved += name
            val before = previous?.names?.get(name)
            if (before != null && before != value && name !in previous!!.unresolved) {
                throw IllegalArgumentException("$name moved from ${hex(before)} to ${hex(value)} between passes")
            }
        }

        private fun lookup(name: String): Int? = when {
            name == "P%" -> currentPc()
            name in names -> names[name].takeIf { name !in unresolved }
            // A name further on, as the pass before found it.
            else -> previous?.let { pass -> pass.names[name].takeIf { name !in pass.unresolved } }
        }

        private fun unknown(value: Expression): Nothing =
            throw IllegalArgumentException("No such label or constant: ${value.unknownNames.first()}")

        /**
         * Works out [text], and whether it was known. A value that isn't known
         * yet is 0, which every operand accepts, so a guess can't be reported
         * as out of range; a strict pass reports the unknown name instead.
         */
        private fun evaluate(text: String): Pair<Int, Boolean> {
            val value = Expression(text, ::lookup)
            val number = value.number()
            if (!value.unresolved) return number to true
            if (strict) unknown(value)
            unresolvedUses++
            return 0 to false
        }

        private fun currentPc(): Int = pc ?: throw IllegalArgumentException("Set P% first, as in P% = &2000")

        private fun emit(byte: Int) {
            val address = currentPc()
            if (address > 0xFFFF) throw IllegalArgumentException("The code runs past &FFFF")
            if (memory[address] >= 0) throw IllegalArgumentException("${hex(address)} is assembled twice")
            memory[address] = byte and 0xFF
            pc = address + 1
        }

        private fun data(kind: Char, operand: String) {
            if (operand.isBlank()) throw IllegalArgumentException("EQU$kind needs a value")
            for (item in splitTopLevel(operand, ',')) {
                if (kind == 'S') {
                    val value = Expression(item, ::lookup)
                    val text = value.string()
                    if (value.unresolved) {
                        if (strict) unknown(value)
                        unresolvedUses++
                    }
                    text.forEach { emit(it.code) }
                    continue
                }
                val (value, _) = evaluate(item)
                val (bytes, min, max) = when (kind) {
                    'B' -> Triple(1, -0x80L, 0xFFL)
                    'W' -> Triple(2, -0x8000L, 0xFFFFL)
                    else -> Triple(4, Int.MIN_VALUE.toLong(), 0xFFFFFFFFL)
                }
                if (value.toLong() !in min..max) throw IllegalArgumentException("${hex(value)} doesn't fit in EQU$kind")
                repeat(bytes) { emit(value ushr (8 * it)) }
            }
        }

        private fun instruction(mnemonic: String, operand: String) {
            val available = Opcodes.modes(mnemonic)
            val (mode, value) = operandMode(mnemonic, operand, available)
            emit(Opcodes.find(mnemonic, mode)!!)
            when (Opcodes.operandLength(mode)) {
                1 -> emit(value)
                2 -> { emit(value); emit(value shr 8) }
            }
        }

        /** The addressing mode and operand value for [operand]. */
        private fun operandMode(mnemonic: String, operand: String, available: Set<AddressingMode>): Pair<AddressingMode, Int> {
            fun need(mode: AddressingMode): AddressingMode =
                mode.takeIf { it in available } ?: throw IllegalArgumentException("$mnemonic can't be used ${describe(mode)}")

            if (operand.isEmpty()) {
                return when {
                    IMPLIED in available -> IMPLIED to 0
                    ACCUMULATOR in available -> ACCUMULATOR to 0
                    else -> throw IllegalArgumentException("$mnemonic needs an operand")
                }
            }
            if (operand.equals("A", ignoreCase = true) && ACCUMULATOR in available) return ACCUMULATOR to 0
            if (IMPLIED in available) throw IllegalArgumentException("$mnemonic takes no operand")
            if (operand.startsWith('#')) {
                val mode = need(IMMEDIATE)
                val (value, _) = evaluate(operand.substring(1))
                if (value !in 0..0xFF) throw IllegalArgumentException("#${hex(value)} isn't a byte")
                return mode to value
            }
            if (RELATIVE in available) {
                val (target, known) = evaluate(operand)
                val offset = target - (currentPc() + 2)
                if (strict && offset !in -128..127) {
                    throw IllegalArgumentException("Branch out of range: ${hex(target)} is ${offset} bytes away, at most -128 to 127")
                }
                return RELATIVE to if (known) offset else 0
            }
            if (operand.startsWith('(')) {
                val close = matchingParen(operand)
                val inner = operand.substring(1, close)
                val after = operand.substring(close + 1).trim()
                val innerParts = splitTopLevel(inner, ',')
                when {
                    after.isEmpty() && innerParts.size == 2 && innerParts[1].equals("X", ignoreCase = true) ->
                        return need(INDEXED_INDIRECT) to zeroPage(innerParts[0], "($mnemonic (zp,X))")
                    after.isEmpty() && innerParts.size == 1 -> {
                        val mode = need(INDIRECT)
                        return mode to address(inner)
                    }
                    INDEX_Y.matches(after) && innerParts.size == 1 ->
                        return need(INDIRECT_INDEXED) to zeroPage(inner, "($mnemonic (zp),Y)")
                }
                // Brackets in an ordinary expression, as in (base+1)*2,X.
            }
            val parts = splitTopLevel(operand, ',')
            val index = if (parts.size == 2) parts[1].uppercase() else null
            if (parts.size > 2 || index != null && index != "X" && index != "Y") {
                throw IllegalArgumentException("Bad operand: $operand")
            }
            val (zeroPageMode, absoluteMode) = when (index) {
                "X" -> ZERO_PAGE_X to ABSOLUTE_X
                "Y" -> ZERO_PAGE_Y to ABSOLUTE_Y
                else -> ZERO_PAGE to ABSOLUTE
            }
            if (zeroPageMode !in available && absoluteMode !in available) need(absoluteMode)
            val (value, known) = evaluate(parts[0])
            if (value !in 0..0xFFFF) throw IllegalArgumentException("${hex(value)} isn't an address")
            val number = instructionCount++
            val mode = if (previous != null) {
                previous.modes[number]
            } else {
                when {
                    zeroPageMode in available && (known && value <= 0xFF || absoluteMode !in available) -> zeroPageMode
                    else -> absoluteMode
                }
            }
            modes += mode
            if (Opcodes.operandLength(mode) == 1 && value > 0xFF) {
                throw IllegalArgumentException("$mnemonic ${index?.let { "zp,$it" } ?: "zp"} needs a zero page address, not ${hex(value)}")
            }
            return mode to value
        }

        private fun zeroPage(expression: String, form: String): Int {
            val (value, _) = evaluate(expression)
            if (value !in 0..0xFF) throw IllegalArgumentException("$form needs a zero page address, not ${hex(value)}")
            return value
        }

        private fun address(expression: String): Int {
            val (value, _) = evaluate(expression)
            if (value !in 0..0xFFFF) throw IllegalArgumentException("${hex(value)} isn't an address")
            return value
        }
    }

    private val ASSIGNMENT = Regex("""([A-Za-z_`][A-Za-z0-9_`]*%?)\s*=(?!=)(.*)""")
    private val INDEX_Y = Regex("""\s*,\s*[Yy]\s*""")

    private fun describe(mode: AddressingMode): String = when (mode) {
        IMMEDIATE -> "with # (immediate)"
        INDIRECT -> "with (address) (indirect)"
        INDEXED_INDIRECT -> "with (zp,X)"
        INDIRECT_INDEXED -> "with (zp),Y"
        ABSOLUTE_X, ZERO_PAGE_X -> "with ,X"
        ABSOLUTE_Y, ZERO_PAGE_Y -> "with ,Y"
        else -> "with an address"
    }

    /** Where the bracket that opens [text] closes. */
    private fun matchingParen(text: String): Int {
        var depth = 0
        var inString = false
        for ((i, c) in text.withIndex()) {
            when {
                c == '"' -> inString = !inString
                inString -> {}
                c == '(' -> depth++
                c == ')' -> if (--depth == 0) return i
            }
        }
        throw IllegalArgumentException("Missing ')'")
    }

    /** [text] split at each [separator] outside brackets and strings, each part trimmed. */
    private fun splitTopLevel(text: String, separator: Char): List<String> {
        val parts = mutableListOf<String>()
        var depth = 0
        var inString = false
        var start = 0
        for ((i, c) in text.withIndex()) {
            when {
                c == '"' -> inString = !inString
                inString -> {}
                c == '(' -> depth++
                c == ')' -> depth--
                c == separator && depth == 0 -> { parts += text.substring(start, i).trim(); start = i + 1 }
            }
        }
        parts += text.substring(start).trim()
        return parts
    }

    private fun hex(value: Int): String =
        if (value < 0) "-&" + (-value.toLong()).toString(16).uppercase() else "&" + value.toString(16).uppercase()
}
