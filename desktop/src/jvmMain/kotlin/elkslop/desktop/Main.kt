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
  --roms DIR          Directory with os.rom and basic.rom (default: built in)
  --scale 1|2|3       Window size; 1 is 640 x 512 (default: 1)
  --no-sound          Disable audio output
  --tape FILE         Put a UEF cassette image in the recorder
  --type TEXT        Type TEXT after boot; \n starts a new line
  --screenshot FILE   Save a PNG of the window, then exit
  --frames N          Frames to run before the screenshot (default: 150)"""

data class Options(
    /** Null means the ROMs built into the jar. */
    val romDirectory: File? = null,
    val scale: Int = 1,
    val sound: Boolean = true,
    val tape: File? = null,
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
                    "--tape" -> options.copy(tape = File(value()))
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

/**
 * The two ROMs a stock Electron needs (© Acorn). The build copies roms/os.rom
 * and roms/basic.rom into the jar, so it runs from anywhere.
 */
class Roms(val os: ByteArray, val basic: ByteArray) {
    companion object {
        private const val RESOURCES = "/elkslop/roms/"

        /** From [directory], or the built-in ROMs if it is null. */
        fun load(directory: File?): Roms {
            fun read(name: String): ByteArray {
                val (where, bytes) = if (directory == null) {
                    val stream = Roms::class.java.getResourceAsStream(RESOURCES + name)
                        ?: throw IOException("The built-in $name is missing from the jar")
                    "Built-in $name" to stream.use { it.readBytes() }
                } else {
                    val file = File(directory, name)
                    if (!file.isFile) throw IOException("Missing ${file.absolutePath}")
                    file.absolutePath to file.readBytes()
                }
                if (bytes.size != 0x4000) throw IOException("$where must be 16 KiB, was ${bytes.size} bytes")
                return bytes
            }
            return Roms(read("os.rom"), read("basic.rom"))
        }
    }
}

/**
 * Where Insert Tape starts browsing: tapes/ in the current directory, else
 * tapes/ beside the jar, which is where the packaged app keeps its cassettes.
 */
fun defaultTapeDirectory(): File {
    val here = File("tapes")
    if (here.isDirectory) return here.absoluteFile
    val jar = runCatching { File(Roms::class.java.protectionDomain.codeSource.location.toURI()) }.getOrNull()
    val beside = jar?.parentFile?.let { File(it, "tapes") }
    return if (beside?.isDirectory == true) beside else here.absoluteFile
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
