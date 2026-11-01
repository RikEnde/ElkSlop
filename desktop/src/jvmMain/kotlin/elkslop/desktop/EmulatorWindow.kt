// SPDX-FileCopyrightText: 2026 The Elkslop contributors
// SPDX-License-Identifier: BSD-3-Clause

package elkslop.desktop

import elkslop.electron.ElectronKey
import elkslop.electron.ElectronMachine
import java.awt.BorderLayout
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
    @Volatile private var capsLock = false

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
            KeyMapping.forKeyCode(e.keyCode)?.let {
                emulator.press(it)
                e.consume()
            }
        }

        override fun keyReleased(e: KeyEvent) {
            KeyMapping.forKeyCode(e.keyCode)?.let {
                emulator.release(it)
                e.consume()
                // A latched FUNC lasts until the next ordinary key is released.
                if (funcLatchItem.isSelected && it !in MODIFIERS) {
                    funcLatchItem.isSelected = false
                    emulator.release(ElectronKey.FUNC)
                }
            }
        }
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
                if (isSelected) emulator.press(ElectronKey.FUNC) else emulator.release(ElectronKey.FUNC)
                screen.requestFocusInWindow()
            }
        }
        add(funcLatchItem)
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
            (if (emulator.paused) "  (paused)" else "")
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
            Keys map by position, so shifted symbols follow the Electron keyboard:
              Shift+2 = "    Shift+' = *    Shift+; = +    Shift+- = =    Shift+0 = @

            Electron key        Host key
            CAPS LK / FUNC      Tab or Alt/Option  (Shift+Tab toggles caps lock)
            COPY                End
            DELETE              Backspace or Delete
            ESCAPE              Esc
            BREAK               F12   (Ctrl+F12 = hard reset; Shift+F12 = autoboot)
            Cursor keys         Arrows
            ^ ~ / | \ / £ { / _ }    = / \ / [ / ]   (the Electron's cursor keys)

            The Keys menu taps COPY, CAPS LK, ESCAPE, BREAK variants and f0-f9.
            FUNC + Next Key holds FUNC for the next key you press.
            The title bar shows [CAPS] while CAPS LK is on.

            Pause: F9.   Paste as Typing types text from the clipboard.
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
        const val BOOT_FRAMES = 100
        val MODIFIERS = setOf(ElectronKey.SHIFT, ElectronKey.CONTROL, ElectronKey.FUNC)
    }
}
