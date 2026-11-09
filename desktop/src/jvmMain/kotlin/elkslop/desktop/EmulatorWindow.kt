// SPDX-FileCopyrightText: 2026 The Elkslop contributors
// SPDX-License-Identifier: BSD-3-Clause

package elkslop.desktop

import elkslop.electron.ElectronKey
import elkslop.electron.ElectronMachine
import java.awt.BorderLayout
import java.awt.FileDialog
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.event.ActionEvent
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.InputEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import javax.imageio.ImageIO
import javax.swing.AbstractAction
import javax.swing.ButtonGroup
import javax.swing.JCheckBoxMenuItem
import javax.swing.JFrame
import javax.swing.JMenu
import javax.swing.JMenuBar
import javax.swing.JMenuItem
import javax.swing.JOptionPane
import javax.swing.JRadioButtonMenuItem
import javax.swing.KeyStroke
import javax.swing.SwingUtilities
import javax.swing.WindowConstants
import kotlin.system.exitProcess

class EmulatorWindow(private val roms: Roms, private val options: Options) : Emulator.Listener {
    private val emulator = Emulator(
        createMachine = { ElectronMachine(roms.os, roms.basic) },
        soundEnabled = options.sound,
        listener = this,
    )
    private val frame = JFrame()
    private val screen = ScreenPanel(emulator)
    private val screenshotTaken = AtomicBoolean(false)
    private val isMac = System.getProperty("os.name").startsWith("Mac")
    private lateinit var pauseItem: JCheckBoxMenuItem
    private lateinit var funcLatchItem: JCheckBoxMenuItem
    private var naturalLayout = false
    @Volatile private var capsLock = false
    @Volatile private var motorRunning = false
    private var tapeName: String? = null

    fun show() {
        frame.defaultCloseOperation = WindowConstants.DISPOSE_ON_CLOSE
        frame.addWindowListener(object : WindowAdapter() {
            override fun windowClosed(e: WindowEvent) {
                emulator.stop()
                exitProcess(0)
            }
        })
        AppIcon.install(frame)
        frame.jMenuBar = createMenuBar()
        frame.contentPane.add(screen, BorderLayout.CENTER)
        screen.scale = options.scale
        screen.addKeyListener(keyListener)
        screen.addFocusListener(object : FocusAdapter() {
            // Keys released while another window has focus never reach us.
            override fun focusLost(e: FocusEvent) {
                emulator.releaseAll()
                funcLatchItem.isSelected = false
            }
        })
        updateTitle()
        frame.pack()
        frame.setLocationRelativeTo(null)
        frame.isVisible = true
        screen.requestFocusInWindow()

        options.tape?.let { insertTape(it) }
        options.typeText?.let { emulator.type(it, delayFrames = BOOT_FRAMES) }
        emulator.start()
    }

    override fun frameReady(frameCount: Long) {
        screen.repaint()
        val path = options.screenshot ?: return
        if (frameCount >= options.screenshotFrames && !emulator.typing && screenshotTaken.compareAndSet(false, true)) {
            SwingUtilities.invokeLater { saveScreenshotAndExit(path) }
        }
    }

    override fun stopped(error: Throwable) {
        SwingUtilities.invokeLater {
            updateTitle()
            pauseItem.isSelected = true
            JOptionPane.showMessageDialog(
                frame,
                "The emulated machine stopped:\n${error.message ?: error}\n\n" +
                    "Use Machine > Power Cycle to restart, or BREAK (F12) to reset.",
                "Elkslop",
                JOptionPane.ERROR_MESSAGE,
            )
        }
    }

    override fun capsLockChanged(on: Boolean) {
        capsLock = on
        SwingUtilities.invokeLater { updateTitle() }
    }

    override fun motorChanged(on: Boolean) {
        motorRunning = on
        SwingUtilities.invokeLater { updateTitle() }
    }

    private val keyListener = object : KeyAdapter() {
        override fun keyPressed(e: KeyEvent) {
            // Leave Command shortcuts to the menu bar.
            if (e.isMetaDown) return
            // Handled here, not as a menu accelerator, so Ctrl/Shift+BREAK work:
            // the OS reads those keys from the matrix while it restarts.
            if (e.keyCode == KeyMapping.BREAK_KEY) {
                resume()
                emulator.pressBreak()
                e.consume()
                return
            }
            if (naturalLayout) {
                val modifier = KeyMapping.naturalModifier(e.keyCode)
                if (modifier != null) {
                    emulator.naturalModifier(modifier, down = true)
                } else {
                    val stroke = KeyMapping.naturalStroke(e.keyCode, e.keyChar, e.isControlDown, e.isAltDown || e.isAltGraphDown)
                    if (stroke != null) emulator.naturalKeyDown(e.keyCode, stroke)
                }
                e.consume()
                return
            }
            KeyMapping.forKeyCode(e.keyCode)?.let {
                emulator.press(it)
                e.consume()
            }
        }

        override fun keyReleased(e: KeyEvent) {
            val key = KeyMapping.forKeyCode(e.keyCode)
            if (naturalLayout) {
                val modifier = KeyMapping.naturalModifier(e.keyCode)
                if (modifier != null) emulator.naturalModifier(modifier, down = false) else emulator.naturalKeyUp(e.keyCode)
                e.consume()
            } else if (key != null) {
                emulator.release(key)
                e.consume()
            }
            // A latched FUNC lasts until the next ordinary key is released.
            if (key != null && funcLatchItem.isSelected && key !in MODIFIERS) {
                funcLatchItem.isSelected = false
                setFuncLatch(false)
            }
        }
    }

    private fun setFuncLatch(on: Boolean) {
        when {
            naturalLayout -> emulator.naturalModifier(ElectronKey.FUNC, on)
            on -> emulator.press(ElectronKey.FUNC)
            else -> emulator.release(ElectronKey.FUNC)
        }
    }

    private fun setNaturalLayout(natural: Boolean) {
        naturalLayout = natural
        // Keys held now would never be released by the other layout's handling.
        emulator.releaseAll()
        funcLatchItem.isSelected = false
        updateTitle()
        screen.requestFocusInWindow()
    }

    private fun createMenuBar() = JMenuBar().apply {
        add(JMenu("Machine").apply {
            pauseItem = JCheckBoxMenuItem("Pause").apply {
                accelerator = KeyStroke.getKeyStroke(KeyEvent.VK_F9, 0)
                addActionListener {
                    emulator.paused = isSelected
                    updateTitle()
                }
            }
            add(pauseItem)
            add(item("Power Cycle", null) {
                resume()
                emulator.powerCycle()
            })
            addSeparator()
            add(JCheckBoxMenuItem("Sound", options.sound).apply {
                isEnabled = options.sound
                addActionListener { emulator.volume = if (isSelected) VOLUME else 0f }
            })
            if (!isMac) {
                addSeparator()
                add(item("Quit", null) { frame.dispose() })
            }
        })
        add(JMenu("Edit").apply {
            add(item("Paste as Typing", shortcut(KeyEvent.VK_V)) { pasteClipboard() })
            add(item("Stop Typing", null) { emulator.cancelTyping() })
        })
        add(createKeysMenu())
        add(JMenu("Tape").apply {
            add(item("Insert Tape...", shortcut(KeyEvent.VK_O)) { chooseTape() })
            add(item("Rewind", null) { emulator.rewindTape() })
            add(item("Eject", null) {
                emulator.ejectTape()
                tapeName = null
                updateTitle()
            })
            addSeparator()
            add(JCheckBoxMenuItem("Fast Loading").apply {
                toolTipText = "Run flat out while the machine reads a tape, instead of at 1200 baud"
                addActionListener {
                    emulator.fastTape = isSelected
                    updateTitle()
                }
            })
        })
        add(JMenu("View").apply {
            val group = ButtonGroup()
            for (scale in 1..3) {
                val choice = JRadioButtonMenuItem("${scale}x", scale == options.scale)
                choice.accelerator = shortcut(KeyEvent.VK_0 + scale)
                choice.addActionListener {
                    screen.scale = scale
                    frame.pack()
                }
                group.add(choice)
                add(choice)
            }
        })
        add(JMenu("Help").apply {
            add(item("Keyboard", null) { showKeyboardHelp() })
        })
    }

    /** Electron keys a host keyboard lacks, or that need a held modifier. */
    private fun createKeysMenu() = JMenu("Keys").apply {
        add(item("BREAK  (F12)", null) {
            resume()
            emulator.pressBreak()
        })
        add(item("CTRL + BREAK  (hard reset)", null) {
            resume()
            emulator.pressBreakWith(ElectronKey.CONTROL)
        })
        add(item("SHIFT + BREAK  (autoboot)", null) {
            resume()
            emulator.pressBreakWith(ElectronKey.SHIFT)
        })
        addSeparator()
        add(item("COPY  (End)", null) { emulator.tap(ElectronKey.COPY) })
        add(item("[  (Shift+End)", null) { emulator.tap(ElectronKey.SHIFT, ElectronKey.COPY) })
        add(item("]  (Ctrl+End)", null) { emulator.tap(ElectronKey.CONTROL, ElectronKey.COPY) })
        // The Electron toggles caps lock with SHIFT + CAPS LK; alone it is FUNC.
        add(item("CAPS LK  (Shift+Tab)", null) { emulator.tap(ElectronKey.SHIFT, ElectronKey.FUNC) })
        add(item("ESCAPE  (Esc)", null) { emulator.tap(ElectronKey.ESCAPE) })
        add(item("DELETE  (Backspace)", null) { emulator.tap(ElectronKey.DELETE) })
        addSeparator()
        add(JMenu("Function Keys").apply {
            for (n in 0..9) {
                add(item("f$n  (FUNC + $n)", null) {
                    emulator.tap(ElectronKey.FUNC, ElectronKey.valueOf("DIGIT_$n"))
                })
            }
        })
        funcLatchItem = JCheckBoxMenuItem("FUNC + Next Key").apply {
            toolTipText = "Hold FUNC until the next key: FUNC + a letter types a BASIC keyword"
            addActionListener {
                setFuncLatch(isSelected)
                screen.requestFocusInWindow()
            }
        }
        add(funcLatchItem)
        addSeparator()
        add(JMenu("Keyboard Layout").apply {
            val group = ButtonGroup()
            add(JRadioButtonMenuItem("Physical (for games)", true).also { choice ->
                choice.toolTipText = "Keys press the Electron key in the same place, so symbols follow the Electron"
                choice.addActionListener { setNaturalLayout(false) }
                group.add(choice)
            })
            add(JRadioButtonMenuItem("Natural (for typing)").also { choice ->
                choice.toolTipText = "Keys type the character your own keyboard layout produces"
                choice.addActionListener { setNaturalLayout(true) }
                group.add(choice)
            })
        })
    }

    private fun item(title: String, key: KeyStroke?, action: () -> Unit) =
        JMenuItem(object : AbstractAction(title) {
            override fun actionPerformed(e: ActionEvent) = action()
        }).apply { accelerator = key }

    /** Command on macOS. Ctrl+Shift elsewhere, so plain Ctrl stays the Electron's. */
    private fun shortcut(keyCode: Int): KeyStroke = KeyStroke.getKeyStroke(
        keyCode,
        if (isMac) InputEvent.META_DOWN_MASK else InputEvent.CTRL_DOWN_MASK or InputEvent.SHIFT_DOWN_MASK,
    )

    private fun resume() {
        emulator.paused = false
        pauseItem.isSelected = false
        updateTitle()
    }

    private fun updateTitle() {
        frame.title = "Elkslop - Acorn Electron" +
            (if (capsLock) "  [CAPS]" else "") +
            (if (naturalLayout) "  [NATURAL]" else "") +
            (tapeName?.let { "  [$it]" } ?: "") +
            (if (emulator.fastTape) "  [FAST]" else "") +
            (if (motorRunning) "  [MOTOR]" else "") +
            (if (emulator.paused) "  (paused)" else "")
    }

    private fun chooseTape() {
        val dialog = FileDialog(frame, "Insert Tape", FileDialog.LOAD)
        dialog.directory = defaultTapeDirectory().path
        dialog.isVisible = true
        val name = dialog.file
        if (name != null) insertTape(File(dialog.directory, name))
        screen.requestFocusInWindow()
    }

    private fun insertTape(file: File) {
        try {
            emulator.insertTape(TapeFile.load(file))
            tapeName = file.name
            updateTitle()
        } catch (e: IOException) {
            JOptionPane.showMessageDialog(
                frame,
                "Couldn't load ${file.name}:\n${e.message ?: e}",
                "Insert Tape",
                JOptionPane.ERROR_MESSAGE,
            )
        }
    }

    private fun pasteClipboard() {
        val text = try {
            Toolkit.getDefaultToolkit().systemClipboard.getData(DataFlavor.stringFlavor) as String
        } catch (e: Exception) {
            return
        }
        val skipped = emulator.type(text)
        if (skipped.isNotEmpty()) {
            JOptionPane.showMessageDialog(
                frame,
                "The Electron keyboard can't type these characters, so they were skipped:\n" +
                    skipped.joinToString(" ") { if (it.isWhitespace()) "U+%04X".format(it.code) else it.toString() },
                "Paste as Typing",
                JOptionPane.WARNING_MESSAGE,
            )
        }
    }

    private fun showKeyboardHelp() {
        JOptionPane.showMessageDialog(
            frame,
            """
            Keys > Keyboard Layout chooses how keys map. [NATURAL] in the title bar shows it.
            Natural types the character your own keyboard produces. Letters still follow
            CAPS LK, and the key you pressed last replaces any key still held down.
            Physical, the default, is for games and keeps the Electron's symbols:
              Shift+2 = "    Shift+' = *    Shift+; = +    Shift+- = =    Shift+0 = @

            Electron key        Host key
            CAPS LK / FUNC      Tab or Alt/Option  (Shift+Tab toggles caps lock)
            COPY                End   (Shift+End = [   Ctrl+End = ])
            DELETE              Backspace or Delete
            ESCAPE              Esc
            BREAK               F12   (Ctrl+F12 = hard reset; Shift+F12 = autoboot)
            Cursor keys         Arrows
            ^ ~ / | \ / £ { / _ }    = / \ / [ / ]   (Physical; the Electron's cursor keys)

            The Keys menu taps COPY, [ and ], CAPS LK, ESCAPE, BREAK variants and f0-f9.
            FUNC + Next Key holds FUNC for the next key you press.
            The title bar shows [CAPS] while CAPS LK is on.

            Pause: F9.   Paste as Typing types text from the clipboard.
            Tapes: insert one from the Tape menu, then type CHAIN "" and RETURN.
            Tape > Fast Loading skips the wait; [FAST] in the title bar shows it's on.
            """.trimIndent(),
            "Keyboard",
            JOptionPane.PLAIN_MESSAGE,
        )
    }

    private fun saveScreenshotAndExit(path: File) {
        path.absoluteFile.parentFile?.mkdirs()
        ImageIO.write(screen.snapshot(), "png", path)
        println("Saved ${path.absolutePath}")
        frame.dispose()
    }

    private companion object {
        const val VOLUME = 0.25f
        val MODIFIERS = setOf(ElectronKey.SHIFT, ElectronKey.CONTROL, ElectronKey.FUNC)
    }
}
