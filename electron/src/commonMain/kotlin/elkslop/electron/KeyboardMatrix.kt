// SPDX-FileCopyrightText: 2026 The Elkslop contributors
// SPDX-License-Identifier: BSD-3-Clause

package elkslop.electron

/** Physical Electron keys, independent of host keyboard layout/key codes. */
enum class ElectronKey(val column: Int, val row: Int) {
    RIGHT(0, 0), COPY(0, 1), SPACE(0, 3),
    LEFT(1, 0), DOWN(1, 1), RETURN(1, 2), DELETE(1, 3),
    MINUS(2, 0), UP(2, 1), COLON(2, 2),
    DIGIT_0(3, 0), P(3, 1), SEMICOLON(3, 2), SLASH(3, 3),
    DIGIT_9(4, 0), O(4, 1), L(4, 2), PERIOD(4, 3),
    DIGIT_8(5, 0), I(5, 1), K(5, 2), COMMA(5, 3),
    DIGIT_7(6, 0), U(6, 1), J(6, 2), M(6, 3),
    DIGIT_6(7, 0), Y(7, 1), H(7, 2), N(7, 3),
    DIGIT_5(8, 0), T(8, 1), G(8, 2), B(8, 3),
    DIGIT_4(9, 0), R(9, 1), F(9, 2), V(9, 3),
    DIGIT_3(10, 0), E(10, 1), D(10, 2), C(10, 3),
    DIGIT_2(11, 0), W(11, 1), S(11, 2), X(11, 3),
    DIGIT_1(12, 0), Q(12, 1), A(12, 2), Z(12, 3),
    ESCAPE(13, 0), FUNC(13, 1), CONTROL(13, 2), SHIFT(13, 3),
}

/** Selected address lines are active-low; pressed keys return active-high bits. */
class KeyboardMatrix {
    private val columns = IntArray(14)

    fun press(key: ElectronKey) { columns[key.column] = columns[key.column] or (1 shl key.row) }
    fun release(key: ElectronKey) { columns[key.column] = columns[key.column] and (1 shl key.row).inv() }
    fun releaseAll() { columns.fill(0) }

    fun read(address: Int): Int {
        var result = 0
        for (column in columns.indices) {
            if (address and (1 shl column) == 0) result = result or columns[column]
        }
        return result
    }
}
