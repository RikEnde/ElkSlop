// SPDX-FileCopyrightText: 2026 The Elkslop contributors
// SPDX-License-Identifier: BSD-3-Clause

package elkslop.devkit

import elkslop.devkit.BasicKeywords.CONDITIONAL
import elkslop.devkit.BasicKeywords.FN_PROC
import elkslop.devkit.BasicKeywords.LINE_NUMBER
import elkslop.devkit.BasicKeywords.MIDDLE
import elkslop.devkit.BasicKeywords.PSEUDO_VARIABLE
import elkslop.devkit.BasicKeywords.REST_OF_LINE
import elkslop.devkit.BasicKeywords.START

/**
 * Turns BBC BASIC source text into a tokenised program, byte for byte what
 * BASIC II stores at PAGE when the same lines are typed in, ready to save to
 * tape and `CHAIN`.
 *
 * Lines may start with a line number. A line without one is numbered 10 more
 * than the line before (the first is 10), so a program that never uses GOTO
 * needs none. Numbers must go up. Blank lines are skipped.
 *
 * Keywords are upper case, as on the machine: `print` is a variable. The rules
 * that decide what becomes a token are BASIC II's own: `PRINTX` is `PRINT X`,
 * `ANDY` is `AND Y`, but `COUNTER` stays a name; nothing is tokenised in strings,
 * after `REM` or `DATA`, or after a `*` that starts a statement; and the line
 * numbers after `GOTO`, `GOSUB`, `THEN`, `ELSE` and `RESTORE` are encoded so
 * `RENUMBER` can find them.
 *
 * Text is the Electron's character set, printable ASCII, with `£` for its
 * pound sign (&60). Tabs become spaces.
 */
object BasicTokeniser {
    /** The highest line number BASIC allows. */
    const val MAX_LINE_NUMBER = 32767

    /** The longest tokenised line BASIC can hold, without its 4 bytes of line number and length. */
    const val MAX_LINE_BYTES = 251

    /**
     * The program as it lies in memory from PAGE: each line is &0D, the line
     * number (high byte first), its length and its text; &0D &FF ends it.
     *
     * @throws SourceError for a line BASIC couldn't hold.
     */
    fun tokenise(source: String): ByteArray {
        val program = ArrayList<Byte>()
        var previous = -1
        for ((index, rawLine) in source.lines().withIndex()) {
            val lineNumber = index + 1
            val text = toElectronText(rawLine, lineNumber)
            if (text.isBlank()) continue
            val (number, rest) = splitLineNumber(text, lineNumber, previous)
            val tokens = tokeniseLine(rest)
            if (tokens.size > MAX_LINE_BYTES) {
                throw SourceError(lineNumber, "Line too long for BASIC: ${tokens.size} bytes once tokenised, at most $MAX_LINE_BYTES")
            }
            program += 0x0D.toByte()
            program += (number shr 8).toByte()
            program += number.toByte()
            program += (tokens.size + 4).toByte()
            program += tokens
            previous = number
        }
        program += 0x0D.toByte()
        program += 0xFF.toByte()
        return program.toByteArray()
    }

    private fun toElectronText(line: String, lineNumber: Int): String = buildString {
        for (char in line) {
            append(
                when (char) {
                    '\t' -> ' '
                    '£' -> '`'
                    in ' '..'~' -> char
                    else -> throw SourceError(lineNumber, "The Electron has no character '$char' (U+${char.code.toString(16).uppercase().padStart(4, '0')})")
                },
            )
        }
    }

    /** The line number, given or the next free one, and the text after it. */
    private fun splitLineNumber(text: String, lineNumber: Int, previous: Int): Pair<Int, String> {
        val start = text.indexOfFirst { it != ' ' }
        var end = start
        while (end < text.length && text[end].isDigit()) end++
        if (end == start) {
            val number = if (previous < 0) 10 else previous + 10
            if (number > MAX_LINE_NUMBER) throw SourceError(lineNumber, "No line numbers left after $previous")
            return number to text
        }
        val number = text.substring(start, end).toIntOrNull()?.takeIf { it <= MAX_LINE_NUMBER }
            ?: throw SourceError(lineNumber, "Line numbers go up to $MAX_LINE_NUMBER")
        if (number <= previous) throw SourceError(lineNumber, "Line $number comes after line $previous; line numbers must go up")
        return number to text.substring(end)
    }

    /**
     * Tokenises one line's text, after its line number. Follows BASIC II's
     * tokeniser step by step: it starts at the start of a statement, reading
     * line numbers, as it does just after the line number it has taken off.
     */
    internal fun tokeniseLine(text: String): List<Byte> {
        val input = text.map { it.code }
        val out = ArrayList<Byte>(input.size)
        var i = 0
        var startOfStatement = true
        var lineNumbers = true

        fun copy() { out += input[i++].toByte() }
        fun copyRest() { while (i < input.size) copy() }
        fun middle() { startOfStatement = false; lineNumbers = false }
        fun copyName() {
            while (i < input.size && isNameChar(input[i])) copy()
            middle()
        }
        fun copyNumber() {
            while (i < input.size && (input[i] == '.'.code || isDigit(input[i]))) copy()
            middle()
        }

        while (i < input.size) {
            val c = input[i]
            when {
                c == ' '.code -> copy()
                // Hex digits aren't keywords: &DEF is a number.
                c == '&'.code -> {
                    copy()
                    while (i < input.size && (isDigit(input[i]) || input[i] in 'A'.code..'F'.code)) copy()
                }
                c == '"'.code -> {
                    copy()
                    while (i < input.size && input[i] != '"'.code) copy()
                    if (i < input.size) copy()
                }
                c == ':'.code -> {
                    copy()
                    startOfStatement = true
                    lineNumbers = false
                }
                c == ','.code -> copy()
                // A star command: the OS reads the rest of the line.
                c == '*'.code -> if (startOfStatement) copyRest() else { copy(); middle() }
                c == '.'.code -> copyNumber()
                isDigit(c) -> if (lineNumbers) encodeLineNumber(input, i, out)?.let { i = it } ?: copyNumber() else copyNumber()
                c < 'A'.code -> { copy(); middle() }
                // No keyword starts with X, Y or Z, or a lower-case letter.
                c >= 'X'.code -> if (isNameChar(c)) copyName() else { copy(); middle() }
                else -> {
                    val match = matchKeyword(input, i)
                    if (match == null) {
                        copyName()
                        continue
                    }
                    val (keyword, length) = match
                    val flags = keyword.flags
                    val token = if (flags and PSEUDO_VARIABLE != 0 && startOfStatement) keyword.token + 0x40 else keyword.token
                    out += token.toByte()
                    i += length
                    if (flags and MIDDLE != 0) middle()
                    if (flags and START != 0) { startOfStatement = true; lineNumbers = false }
                    if (flags and FN_PROC != 0) while (i < input.size && isNameChar(input[i])) copy()
                    if (flags and LINE_NUMBER != 0) lineNumbers = true
                    if (flags and REST_OF_LINE != 0) copyRest()
                }
            }
        }
        return out
    }

    /**
     * The keyword at [start] and how many characters it takes, counting the
     * dot of an abbreviation, or null if a name starts there instead.
     */
    private fun matchKeyword(input: List<Int>, start: Int): Pair<BasicKeywords.Keyword, Int>? {
        val first = input[start]
        for (keyword in BasicKeywords.all) {
            val text = keyword.text
            // The table is in order of first letter: past it, no keyword fits.
            if (first < text[0].code) return null
            if (first != text[0].code) continue
            var j = 1
            while (j < text.length && start + j < input.size && input[start + j] == text[j].code) j++
            val length = when {
                j == text.length -> j
                start + j < input.size && input[start + j] == '.'.code -> j + 1
                else -> continue
            }
            if (keyword.flags and CONDITIONAL != 0 && start + length < input.size && isNameChar(input[start + length])) return null
            return keyword to length
        }
        return null
    }

    /**
     * Writes the line number in the digits at [start] as &8D and three bytes,
     * and returns where they end; null if it is too big to be a line number.
     */
    private fun encodeLineNumber(input: List<Int>, start: Int, out: MutableList<Byte>): Int? {
        var end = start
        var number = 0
        while (end < input.size && isDigit(input[end])) {
            number = number * 10 + (input[end] - '0'.code)
            if (number > MAX_LINE_NUMBER) return null
            end++
        }
        val low = number and 0xFF
        val high = number shr 8
        out += 0x8D.toByte()
        out += ((((low and 0xC0) or ((high and 0xC0) shr 2)) shr 2) xor 0x54).toByte()
        out += ((low and 0x3F) or 0x40).toByte()
        out += ((high and 0x3F) or 0x40).toByte()
        return end
    }

    private fun isDigit(c: Int) = c in '0'.code..'9'.code

    /** Letters, digits, `_` and `` ` ``: what a variable name is made of. */
    private fun isNameChar(c: Int) = isDigit(c) || c in 'A'.code..'Z'.code || c in 0x5F..0x7A
}
