// SPDX-FileCopyrightText: 2026 The Elkslop contributors
// SPDX-License-Identifier: BSD-3-Clause

package elkslop.desktop

import elkslop.electron.ElectronKey
import java.awt.event.KeyEvent

/**
 * Host keyboard to Electron keyboard.
 *
 * Physical keys map by position, following MAME's layout, so shifted symbols
 * are the Electron's own: Shift+2 is ", Shift+' is * (the : key), and = is
 * Shift+-. Pasted text goes through [forCharacter] instead, which picks the
 * Electron key and Shift state that produce each character.
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
}
