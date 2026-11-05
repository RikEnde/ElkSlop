// SPDX-FileCopyrightText: 2026 The Elkslop contributors
// SPDX-License-Identifier: BSD-3-Clause

package elkslop.desktop

import elkslop.electron.ElectronKey
import java.awt.event.KeyEvent

/**
 * Host keyboard to Electron keyboard.
 *
 * In the Physical layout keys map by position, following MAME's layout, so
 * shifted symbols are the Electron's own: Shift+2 is ", Shift+' is * (the : key),
 * and = is Shift+-. On macOS, Java derives letter and digit key codes from the
 * host layout and punctuation from the physical key.
 *
 * Pasted text goes through [forCharacter], which picks the Electron key and
 * modifiers that produce each character. The Natural layout uses it too, through
 * [naturalStroke] and [NaturalKeyboard].
 */
object KeyMapping {
    private val physical: Map<Int, ElectronKey> = buildMap {
        for (c in 'A'..'Z') put(KeyEvent.VK_A + (c - 'A'), ElectronKey.valueOf(c.toString()))
        for (d in 0..9) put(KeyEvent.VK_0 + d, ElectronKey.valueOf("DIGIT_$d"))
        put(KeyEvent.VK_SPACE, ElectronKey.SPACE)
        put(KeyEvent.VK_ENTER, ElectronKey.RETURN)
        put(KeyEvent.VK_BACK_SPACE, ElectronKey.DELETE)
        put(KeyEvent.VK_DELETE, ElectronKey.DELETE)
        put(KeyEvent.VK_ESCAPE, ElectronKey.ESCAPE)
        put(KeyEvent.VK_SHIFT, ElectronKey.SHIFT)
        put(KeyEvent.VK_CONTROL, ElectronKey.CONTROL)
        // CAPS LK/FUNC. Host Caps Lock is a toggle, not a key, so it is unusable.
        put(KeyEvent.VK_TAB, ElectronKey.FUNC)
        put(KeyEvent.VK_ALT, ElectronKey.FUNC)
        put(KeyEvent.VK_END, ElectronKey.COPY)
        put(KeyEvent.VK_LEFT, ElectronKey.LEFT)
        put(KeyEvent.VK_RIGHT, ElectronKey.RIGHT)
        put(KeyEvent.VK_UP, ElectronKey.UP)
        put(KeyEvent.VK_DOWN, ElectronKey.DOWN)
        put(KeyEvent.VK_MINUS, ElectronKey.MINUS)
        put(KeyEvent.VK_SEMICOLON, ElectronKey.SEMICOLON)
        put(KeyEvent.VK_QUOTE, ElectronKey.COLON)
        put(KeyEvent.VK_COMMA, ElectronKey.COMMA)
        put(KeyEvent.VK_PERIOD, ElectronKey.PERIOD)
        put(KeyEvent.VK_SLASH, ElectronKey.SLASH)
        // The arrow keys also carry symbols on the Electron; these host keys sit
        // roughly where those symbols are on a PC keyboard.
        put(KeyEvent.VK_EQUALS, ElectronKey.LEFT)
        put(KeyEvent.VK_BACK_SLASH, ElectronKey.RIGHT)
        put(KeyEvent.VK_OPEN_BRACKET, ElectronKey.UP)
        put(KeyEvent.VK_CLOSE_BRACKET, ElectronKey.DOWN)
    }

    /** Key that triggers BREAK (reset) rather than a matrix key. */
    const val BREAK_KEY = KeyEvent.VK_F12

    fun forKeyCode(keyCode: Int): ElectronKey? = physical[keyCode]

    /** A key to press for a typed character, with any modifier it needs. */
    data class Stroke(val key: ElectronKey, val shift: Boolean, val control: Boolean = false)

    private val unshifted = mapOf(
        ' ' to ElectronKey.SPACE, '\n' to ElectronKey.RETURN, '\r' to ElectronKey.RETURN,
        '-' to ElectronKey.MINUS, ':' to ElectronKey.COLON, ';' to ElectronKey.SEMICOLON,
        ',' to ElectronKey.COMMA, '.' to ElectronKey.PERIOD, '/' to ElectronKey.SLASH,
    )
    private val shifted = mapOf(
        '!' to ElectronKey.DIGIT_1, '"' to ElectronKey.DIGIT_2, '#' to ElectronKey.DIGIT_3,
        '$' to ElectronKey.DIGIT_4, '%' to ElectronKey.DIGIT_5, '&' to ElectronKey.DIGIT_6,
        '\'' to ElectronKey.DIGIT_7, '(' to ElectronKey.DIGIT_8, ')' to ElectronKey.DIGIT_9,
        '@' to ElectronKey.DIGIT_0, '=' to ElectronKey.MINUS, '*' to ElectronKey.COLON,
        '+' to ElectronKey.SEMICOLON, '<' to ElectronKey.COMMA, '>' to ElectronKey.PERIOD,
        '?' to ElectronKey.SLASH, '^' to ElectronKey.LEFT, '|' to ElectronKey.RIGHT,
        '_' to ElectronKey.DOWN, '£' to ElectronKey.UP, '[' to ElectronKey.COPY,
    )
    private val controlled = mapOf(
        '~' to ElectronKey.LEFT, '\\' to ElectronKey.RIGHT, '{' to ElectronKey.UP,
        '}' to ElectronKey.DOWN, ']' to ElectronKey.COPY,
    )

    /**
     * Stroke for [char], or null if the Electron cannot type it.
     * With CAPS LK on, unshifted letters are capitals and Shift gives lower case.
     */
    fun forCharacter(char: Char, capsLock: Boolean): Stroke? {
        if (char in 'A'..'Z') return Stroke(ElectronKey.valueOf(char.toString()), shift = !capsLock)
        if (char in 'a'..'z') return Stroke(ElectronKey.valueOf(char.uppercase()), shift = capsLock)
        if (char in '0'..'9') return Stroke(ElectronKey.valueOf("DIGIT_$char"), shift = false)
        unshifted[char]?.let { return Stroke(it, shift = false) }
        shifted[char]?.let { return Stroke(it, shift = true) }
        controlled[char]?.let { return Stroke(it, shift = false, control = true) }
        return null
    }

    /**
     * A key press in the Natural layout. A null modifier follows what the host
     * is holding; true or false overrides it while the key is down.
     */
    data class NaturalStroke(
        val key: ElectronKey,
        val shift: Boolean? = null,
        val control: Boolean? = null,
        val func: Boolean? = null,
    )

    /** The Electron modifier a host key stands for, in the Natural layout. */
    fun naturalModifier(keyCode: Int): ElectronKey? =
        physical[keyCode]?.takeIf { it == ElectronKey.SHIFT || it == ElectronKey.CONTROL || it == ElectronKey.FUNC }

    /**
     * Stroke for a host key that produced [char], or null if it should do nothing.
     * On layouts where Option or AltGr types symbols, [alt] with a symbol means
     * the host composed it, so FUNC and CONTROL must not reach the Electron.
     */
    fun naturalStroke(keyCode: Int, char: Char, control: Boolean, alt: Boolean): NaturalStroke? {
        byPosition(keyCode)?.takeIf { keyCode in positionalInNatural }?.let { return NaturalStroke(it) }
        deadKeys[keyCode]?.let { return symbolStroke(it, alt) }
        // Ctrl and Alt/FUNC combine with the key itself, whatever character the host makes.
        if (control && !alt) return byPosition(keyCode)?.let { NaturalStroke(it) }
        if (char in 'a'..'z' || char in 'A'..'Z') return NaturalStroke(ElectronKey.valueOf(char.uppercase()))
        if (alt && char in '0'..'9') return byPosition(keyCode)?.let { NaturalStroke(it) }
        symbolStroke(char, alt)?.let { return it }
        // Option+A is a character the Electron lacks, but with FUNC it's a BASIC
        // keyword. An accented letter is what follows a dead key: type its letter.
        if (alt || keyCode in KeyEvent.VK_A..KeyEvent.VK_Z) return byPosition(keyCode)?.let { NaturalStroke(it) }
        return null
    }

    private fun symbolStroke(char: Char, alt: Boolean): NaturalStroke? {
        val stroke = forCharacter(char, capsLock = true) ?: return null
        return NaturalStroke(stroke.key, stroke.shift, stroke.control, if (alt) false else null)
    }

    /** The physical mapping, without the host keys that stand in for cursor symbols. */
    private fun byPosition(keyCode: Int): ElectronKey? = if (keyCode in cursorSymbolKeys) null else physical[keyCode]

    private val cursorSymbolKeys = setOf(
        KeyEvent.VK_EQUALS, KeyEvent.VK_BACK_SLASH, KeyEvent.VK_OPEN_BRACKET, KeyEvent.VK_CLOSE_BRACKET,
    )

    /** Keys that don't type a symbol, so the host's Shift and Ctrl apply as they are. */
    private val positionalInNatural = setOf(
        KeyEvent.VK_SPACE, KeyEvent.VK_ENTER, KeyEvent.VK_BACK_SPACE, KeyEvent.VK_DELETE, KeyEvent.VK_ESCAPE,
        KeyEvent.VK_END, KeyEvent.VK_LEFT, KeyEvent.VK_RIGHT, KeyEvent.VK_UP, KeyEvent.VK_DOWN,
    )

    /** Dead keys wait for the next key on the host. Here they type at once. */
    private val deadKeys = mapOf(
        KeyEvent.VK_DEAD_CIRCUMFLEX to '^', KeyEvent.VK_DEAD_TILDE to '~',
        KeyEvent.VK_DEAD_ACUTE to '\'', KeyEvent.VK_DEAD_DIAERESIS to '"',
    )
}
