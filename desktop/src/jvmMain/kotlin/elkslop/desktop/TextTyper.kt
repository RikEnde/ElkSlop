// SPDX-FileCopyrightText: 2026 The Elkslop contributors
// SPDX-License-Identifier: BSD-3-Clause

package elkslop.desktop

import elkslop.electron.ElectronKey
import elkslop.electron.ElectronMachine

/**
 * Presses matrix keys frame by frame, like a very steady typist: text from
 * the clipboard, or key chords from the Keys menu. Runs on the emulation thread.
 * The OS scans the keyboard from its interrupts, so each key is held, then
 * released, for several frames; RETURN gets extra time so BASIC can tokenise
 * the line before the next one arrives.
 *
 * In a chord, the modifiers go down first and the last key follows a couple
 * of frames later, as a person would press them. The OS needs this for FUNC:
 * FUNC and a digit pressed in the same frame produce nothing.
 */
class TextTyper {
    private sealed interface Item {
        data class Text(val char: Char) : Item

        /** Keys held together; [onPress] runs after they go down (e.g. BREAK). */
        data class Chord(
            val keys: List<ElectronKey>,
            val holdFrames: Int,
            val onPress: ((ElectronMachine) -> Unit)?,
        ) : Item
    }

    private val pending = ArrayDeque<Item>()
    private var waitFrames = 0
    private var held: List<ElectronKey> = emptyList()
    private var heldReturn = false
    private var finalKey: ElectronKey? = null
    private var finalHoldFrames = 0
    private var finalAction: ((ElectronMachine) -> Unit)? = null

    val busy: Boolean get() = pending.isNotEmpty() || held.isNotEmpty() || waitFrames > 0

    /** Characters the Electron cannot type are skipped and returned. */
    fun enqueue(text: String, delayFrames: Int = 0): Set<Char> {
        val skipped = mutableSetOf<Char>()
        // Normalise line endings; tabs become spaces.
        val normalised = text.replace("\r\n", "\n").replace('\t', ' ')
        for (char in normalised) {
            if (KeyMapping.forCharacter(char, capsLock = true) == null) skipped += char else pending += Item.Text(char)
        }
        waitFrames = maxOf(waitFrames, delayFrames)
        return skipped
    }

    /** Press [keys] together for [holdFrames] frames, then release them. */
    fun enqueueChord(keys: List<ElectronKey>, holdFrames: Int = PRESS_FRAMES, onPress: ((ElectronMachine) -> Unit)? = null) {
        pending += Item.Chord(keys, holdFrames, onPress)
    }

    fun cancel(machine: ElectronMachine) {
        pending.clear()
        waitFrames = 0
        finalKey = null
        finalAction = null
        releaseHeld(machine)
    }

    /** Call once per emulated frame. */
    fun onFrame(machine: ElectronMachine) {
        if (waitFrames > 0) {
            waitFrames--
            return
        }
        finalKey?.let { key ->
            machine.keyboard.press(key)
            finalKey = null
            waitFrames = finalHoldFrames
            finalAction?.invoke(machine)
            finalAction = null
            return
        }
        if (held.isNotEmpty()) {
            waitFrames = if (heldReturn) RETURN_FRAMES else RELEASE_FRAMES
            releaseHeld(machine)
            return
        }
        when (val item = pending.removeFirstOrNull() ?: return) {
            is Item.Text -> {
                // Decide Shift from the live CAPS LK LED, so either lock state works.
                val stroke = KeyMapping.forCharacter(item.char, machine.ula.capsLockLed) ?: return
                val modifiers = listOfNotNull(
                    ElectronKey.SHIFT.takeIf { stroke.shift },
                    ElectronKey.CONTROL.takeIf { stroke.control },
                )
                press(machine, modifiers + stroke.key, PRESS_FRAMES, null)
            }
            is Item.Chord -> press(machine, item.keys, item.holdFrames, item.onPress)
        }
    }

    private fun press(machine: ElectronMachine, keys: List<ElectronKey>, holdFrames: Int, onPress: ((ElectronMachine) -> Unit)?) {
        held = keys
        heldReturn = ElectronKey.RETURN in keys
        keys.dropLast(1).forEach(machine.keyboard::press)
        // The last key goes down on a later frame (immediately for a single key).
        finalKey = keys.last()
        finalHoldFrames = holdFrames
        finalAction = onPress
        waitFrames = if (keys.size > 1) MODIFIER_LEAD_FRAMES else 0
        if (keys.size == 1) onFrame(machine)
    }

    private fun releaseHeld(machine: ElectronMachine) {
        held.forEach(machine.keyboard::release)
        held = emptyList()
        heldReturn = false
    }

    private companion object {
        const val PRESS_FRAMES = 2
        const val RELEASE_FRAMES = 2
        const val RETURN_FRAMES = 12
        const val MODIFIER_LEAD_FRAMES = 1
    }
}
