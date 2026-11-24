// SPDX-FileCopyrightText: 2026 The Elkslop contributors
// SPDX-License-Identifier: BSD-3-Clause

package elkslop.devkit

/**
 * Reads and works out a BBC BASIC expression in an assembler operand, such as
 * `table+2*Y%`, `start DIV 256`, `ASC"A"` or `"Hello"+CHR$13`. Integers only,
 * 32 bits, as BASIC's integer variables.
 *
 * Operators, loosest first, as in BASIC: `OR` `EOR`; `AND`; `=` `<>` `<` `>`
 * `<=` `>=` (true is -1); `+` `-`; `*` `/` `DIV` `MOD`; then unary `-`, `+`
 * and `NOT`. Numbers are decimal, or hex after `&`. `TRUE` and `FALSE` are -1
 * and 0. Keywords are upper case, and as in BASIC a keyword can run into the
 * number after it: `start DIV256`.
 *
 * Names come from [lookup]. A name it doesn't know yet, such as a label
 * further on, counts as 0 and sets [unresolved], for the assembler's first pass.
 */
internal class Expression(
    private val text: String,
    private val lookup: (String) -> Int?,
) {
    private var pos = 0

    /** True if a name was not known; the value is then a guess. */
    var unresolved = false
        private set

    /** Names that were not known, for the error in the last pass. */
    val unknownNames = mutableListOf<String>()

    val atEnd: Boolean get() = skipSpaces().let { pos >= text.length }

    /** Reads a whole number expression, to the end of the text. */
    fun number(): Int {
        val value = or()
        expectEnd()
        return value
    }

    /** Reads a whole string expression, to the end of the text: `"Hi"+CHR$13`. */
    fun string(): String {
        val value = stringSum()
        expectEnd()
        return value
    }

    /** True if the text at the start is a string, not a number. */
    fun startsWithString(): Boolean {
        skipSpaces()
        return peek() == '"' || keywordAt("CHR$")
    }

    private fun expectEnd() {
        skipSpaces()
        if (pos < text.length) fail("Unexpected '${text.substring(pos)}'")
    }

    private fun or(): Int {
        var value = and()
        while (true) {
            value = when {
                keyword("OR") -> value or and()
                keyword("EOR") -> value xor and()
                else -> return value
            }
        }
    }

    private fun and(): Int {
        var value = comparison()
        while (keyword("AND")) value = value and comparison()
        return value
    }

    private fun comparison(): Int {
        val left = sum()
        skipSpaces()
        val op = when {
            text.startsWith("<>", pos) || text.startsWith("<=", pos) || text.startsWith(">=", pos) -> text.substring(pos, pos + 2)
            peek() == '<' || peek() == '>' || peek() == '=' -> text.substring(pos, pos + 1)
            else -> return left
        }
        pos += op.length
        val right = sum()
        val result = when (op) {
            "=" -> left == right
            "<>" -> left != right
            "<" -> left < right
            ">" -> left > right
            "<=" -> left <= right
            else -> left >= right
        }
        return if (result) -1 else 0
    }

    private fun sum(): Int {
        var value = product()
        while (true) {
            skipSpaces()
            value = when (peek()) {
                '+' -> { pos++; value + product() }
                '-' -> { pos++; value - product() }
                else -> return value
            }
        }
    }

    private fun product(): Int {
        var value = unary()
        while (true) {
            skipSpaces()
            value = when {
                peek() == '*' -> { pos++; value * unary() }
                peek() == '/' -> { pos++; divide(value, unary()) { a, b -> a / b } }
                keyword("DIV") -> divide(value, unary()) { a, b -> a / b }
                keyword("MOD") -> divide(value, unary()) { a, b -> a % b }
                else -> return value
            }
        }
    }

    private fun divide(a: Int, b: Int, op: (Int, Int) -> Int): Int {
        if (b != 0) return op(a, b)
        // A label further on is 0 in the first pass: don't complain about that yet.
        if (unresolved) return 0
        fail("Division by zero")
    }

    private fun unary(): Int {
        skipSpaces()
        return when {
            peek() == '-' -> { pos++; -unary() }
            peek() == '+' -> { pos++; unary() }
            keyword("NOT") -> unary().inv()
            else -> primary()
        }
    }

    private fun primary(): Int {
        skipSpaces()
        val c = peek() ?: fail("Missing a number")
        return when {
            c == '(' -> {
                pos++
                val value = or()
                expect(')')
                value
            }
            c == '&' -> {
                pos++
                val start = pos
                while (pos < text.length && text[pos].uppercaseChar() in HEX_DIGITS) pos++
                val digits = text.substring(start, pos)
                if (digits.isEmpty()) fail("Missing hex digits after &")
                digits.toLongOrNull(16)?.takeIf { it <= 0xFFFFFFFFL }?.toInt() ?: fail("&$digits is too big")
            }
            c.isDigit() -> {
                val start = pos
                while (pos < text.length && text[pos].isDigit()) pos++
                if (peek() == '.') fail("Only whole numbers in the assembler")
                text.substring(start, pos).toIntOrNull() ?: fail("${text.substring(start, pos)} is too big")
            }
            keyword("TRUE") -> -1
            keyword("FALSE") -> 0
            keyword("ASC") -> {
                val string = stringTerm()
                if (string.isEmpty()) -1 else string[0].code
            }
            c == '"' || keywordAt("CHR$") -> fail("A string where a number should be")
            isNameStart(c) -> {
                val name = name()
                lookup(name) ?: run {
                    unresolved = true
                    unknownNames += name
                    0
                }
            }
            else -> fail("Unexpected '${text.substring(pos)}'")
        }
    }

    private fun stringSum(): String {
        var value = stringTerm()
        while (true) {
            skipSpaces()
            if (peek() != '+') return value
            pos++
            value += stringTerm()
        }
    }

    private fun stringTerm(): String {
        skipSpaces()
        return when {
            peek() == '"' -> {
                pos++
                val result = StringBuilder()
                while (true) {
                    if (pos >= text.length) fail("Missing closing \"")
                    val c = text[pos++]
                    // BASIC writes a quote inside a string as "".
                    if (c == '"') {
                        if (peek() == '"') { result.append('"'); pos++ } else break
                    } else {
                        result.append(c)
                    }
                }
                result.toString()
            }
            keyword("CHR$") -> {
                val code = unary()
                (code and 0xFF).toChar().toString()
            }
            peek() == '(' -> {
                pos++
                val value = stringSum()
                expect(')')
                value
            }
            else -> fail("Missing a string")
        }
    }

    /** A name: a letter, `_` or `` ` ``, then those and digits, perhaps ending in %. */
    private fun name(): String {
        val start = pos
        while (pos < text.length && isNameChar(text[pos])) pos++
        if (peek() == '%') pos++
        return text.substring(start, pos)
    }

    /**
     * Takes [word] if it is next: on its own, or running into a number as in
     * `DIV256`, but not as the start of a longer name such as `ORIGIN`.
     */
    private fun keyword(word: String): Boolean {
        if (!keywordAt(word)) return false
        pos += word.length
        return true
    }

    private fun keywordAt(word: String): Boolean {
        skipSpaces()
        if (!text.startsWith(word, pos)) return false
        if (word.endsWith('$')) return true
        var end = pos + word.length
        if (end >= text.length || !isNameChar(text[end])) return true
        // DIV256: the rest of the name is all digits, so BASIC reads a keyword and a number.
        while (end < text.length && text[end].isDigit()) end++
        return end >= text.length || !isNameChar(text[end]) && text[end] != '%'
    }

    private fun expect(char: Char) {
        skipSpaces()
        if (peek() != char) fail("Missing '$char'")
        pos++
    }

    private fun peek(): Char? = text.getOrNull(pos)

    private fun skipSpaces() {
        while (pos < text.length && text[pos] == ' ') pos++
    }

    private fun fail(message: String): Nothing = throw IllegalArgumentException(message)

    companion object {
        private const val HEX_DIGITS = "0123456789ABCDEF"

        fun isNameStart(c: Char) = c in 'A'..'Z' || c in 'a'..'z' || c == '_' || c == '`'

        fun isNameChar(c: Char) = isNameStart(c) || c.isDigit()
    }
}
