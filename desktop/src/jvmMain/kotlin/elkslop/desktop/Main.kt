// SPDX-FileCopyrightText: 2026 The Elkslop contributors
// SPDX-License-Identifier: BSD-3-Clause

package elkslop.desktop

import java.awt.GraphicsEnvironment
import java.io.File
import java.io.IOException
import javax.swing.JOptionPane
import javax.swing.SwingUtilities
import kotlin.system.exitProcess

const val USAGE = """Usage: elkslop [options]
  --roms DIR          Directory with os.rom and basic.rom (default: roms)
  --scale 1|2|3       Window size; 1 is 640 x 512 (default: 1)
  --no-sound          Disable audio output
  --type TEXT         Type TEXT after boot; \n starts a new line
  --screenshot FILE   Save a PNG of the window, then exit
  --frames N          Frames to run before the screenshot (default: 150)"""

data class Options(
    val romDirectory: File = File("roms"),
    val scale: Int = 1,
    val sound: Boolean = true,
    val typeText: String? = null,
    val screenshot: File? = null,
    val screenshotFrames: Int = 150,
) {
    companion object {
        fun parse(args: Array<String>): Options {
            var options = Options()
            var i = 0
            fun value(): String = args.getOrNull(++i) ?: throw IllegalArgumentException("${args[i - 1]} needs a value")
            while (i < args.size) {
                options = when (val arg = args[i]) {
                    "--roms" -> options.copy(romDirectory = File(value()))
                    "--scale" -> options.copy(scale = value().toIntOrNull()?.takeIf { it in 1..3 }
                        ?: throw IllegalArgumentException("--scale must be 1, 2 or 3"))
                    "--no-sound" -> options.copy(sound = false)
                    "--type" -> options.copy(typeText = value().replace("\\n", "\n"))
                    "--screenshot" -> options.copy(screenshot = File(value()))
                    "--frames" -> options.copy(screenshotFrames = value().toIntOrNull()?.takeIf { it > 0 }
                        ?: throw IllegalArgumentException("--frames must be a positive number"))
                    "--help", "-h" -> {
                        println(USAGE)
                        exitProcess(0)
                    }
                    else -> throw IllegalArgumentException("Unknown option: $arg")
                }
                i++
            }
            return options
        }
    }
}

/** The two ROMs a stock Electron needs, from roms/ by default (© Acorn). */
class Roms(val os: ByteArray, val basic: ByteArray) {
    companion object {
        fun load(directory: File): Roms {
            fun read(name: String): ByteArray {
                val file = File(directory, name)
                if (!file.isFile) throw IOException("Missing ${file.absolutePath}")
                val bytes = file.readBytes()
                if (bytes.size != 0x4000) throw IOException("${file.absolutePath} must be 16 KiB, was ${bytes.size} bytes")
                return bytes
            }
            return Roms(read("os.rom"), read("basic.rom"))
        }
    }
}

fun main(args: Array<String>) {
    System.setProperty("apple.awt.application.name", "Elkslop")
    System.setProperty("apple.laf.useScreenMenuBar", "true")

    val options = try {
        Options.parse(args)
    } catch (e: IllegalArgumentException) {
        System.err.println(e.message)
        System.err.println(USAGE)
        exitProcess(2)
    }
    val roms = try {
        Roms.load(options.romDirectory)
    } catch (e: IOException) {
        val message = "${e.message}\n\nPut the Electron os.rom and basic.rom in that directory, or pass --roms DIR."
        System.err.println(message)
        if (!GraphicsEnvironment.isHeadless()) {
            JOptionPane.showMessageDialog(null, message, "Elkslop", JOptionPane.ERROR_MESSAGE)
        }
        exitProcess(1)
    }
    SwingUtilities.invokeLater { EmulatorWindow(roms, options).show() }
}
