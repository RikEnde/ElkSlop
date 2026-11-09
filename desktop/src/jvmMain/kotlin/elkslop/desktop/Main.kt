// SPDX-FileCopyrightText: 2026 The Elkslop contributors
// SPDX-License-Identifier: BSD-3-Clause

package elkslop.desktop

import elkslop.cpu.Disassembler
import elkslop.electron.ElectronMachine
import elkslop.electron.UlaVideo
import java.awt.GraphicsEnvironment
import java.awt.image.BufferedImage
import java.io.File
import java.io.IOException
import javax.imageio.ImageIO
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
  --disassemble A-B   Run without a window, then print the machine code from hex address
                      A to B, as in --disassemble D940-D9FF; with --screenshot, the
                      picture comes from the same run
  --origin ADDR       Number the listing from hex ADDR, for code that is copied there to run
  --frames N          Frames to run before the screenshot or listing (default: 150)"""

data class Options(
    /** Null means the ROMs built into the jar. */
    val romDirectory: File? = null,
    val scale: Int = 1,
    val sound: Boolean = true,
    val tape: File? = null,
    val typeText: String? = null,
    val screenshot: File? = null,
    /** Frames to run before the screenshot or the listing. */
    val screenshotFrames: Int = 150,
    /** Addresses to disassemble after running headless, or null. */
    val disassemble: IntRange? = null,
    /** Where the code in [disassemble] runs from, if it is copied there first; null for where it is. */
    val origin: Int? = null,
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
                    "--disassemble" -> options.copy(disassemble = parseRange(value()))
                    "--origin" -> options.copy(origin = address(value())
                        ?: throw IllegalArgumentException("--origin needs a hex address, as in 0E00"))
                    "--help", "-h" -> {
                        println(USAGE)
                        exitProcess(0)
                    }
                    else -> throw IllegalArgumentException("Unknown option: $arg")
                }
                i++
            }
            if (options.origin != null && options.disassemble == null) {
                throw IllegalArgumentException("--origin only goes with --disassemble")
            }
            return options
        }

        /** A hex address such as `D940`, `$D940`, `&D940` or `0xD940`, or null. */
        private fun address(text: String): Int? =
            text.trim().removePrefix("$").removePrefix("&").removePrefix("0x")
                .toIntOrNull(16)?.takeIf { it in 0..0xFFFF }

        /** `D940-D9FF`, or `$D940-$D9FF`, in hex; a single address gives one byte's worth. */
        private fun parseRange(text: String): IntRange {
            val parts = text.split("-")
            val start = address(parts[0])
            val end = if (parts.size == 2) address(parts[1]) else start.takeIf { parts.size == 1 }
            if (start == null || end == null || end < start) {
                throw IllegalArgumentException("--disassemble needs hex addresses START-END, as in D940-D9FF")
            }
            return start..end
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

/**
 * Builds a machine and runs [Options.screenshotFrames] frames (50ths of a
 * second) as fast as possible, with the tape and text from [options], and on
 * until the text is typed. The tape plays only if the text starts it.
 */
fun runHeadless(roms: Roms, options: Options): ElectronMachine {
    val machine = ElectronMachine(roms.os, roms.basic).also { it.reset() }
    options.tape?.let { machine.ula.cassette.port = TapeFile.load(it) }
    val typer = TextTyper()
    options.typeText?.let { typer.enqueue(it, delayFrames = BOOT_FRAMES) }
    var frames = 0
    while (frames < options.screenshotFrames || typer.busy) {
        typer.onFrame(machine)
        val video = machine.ula.video
        val frame = video.frameNumber
        while (video.frameNumber == frame) machine.step()
        frames++
    }
    return machine
}

/**
 * A listing of [range] as the CPU sees memory after [runHeadless]: RAM, the
 * paged ROM or the OS, as the paging is then. Reading for it changes nothing.
 * The listing numbers the code from [origin], where it will run once copied.
 */
fun disassemble(machine: ElectronMachine, range: IntRange, origin: Int = range.first): String =
    Disassembler(machine.bus::peek).listing(range.first, range.last, origin)

/**
 * Writes [machine]'s last frame to [file] as a PNG, 640 x 512 with each line
 * doubled, as the window shows it at scale 1.
 */
fun saveScreenshot(machine: ElectronMachine, file: File) {
    val width = UlaVideo.WIDTH
    val frame = IntArray(width * UlaVideo.HEIGHT)
    machine.ula.video.copyFrameInto(frame)
    val image = BufferedImage(width, UlaVideo.HEIGHT * 2, BufferedImage.TYPE_INT_RGB)
    for (y in 0 until UlaVideo.HEIGHT) {
        image.setRGB(0, 2 * y, width, 1, frame, y * width, width)
        image.setRGB(0, 2 * y + 1, width, 1, frame, y * width, width)
    }
    file.absoluteFile.parentFile?.mkdirs()
    ImageIO.write(image, "png", file)
}

/** Frames the OS needs to start before typing; the window waits as long. */
const val BOOT_FRAMES = 100

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
    options.disassemble?.let {
        val machine = try {
            runHeadless(roms, options)
        } catch (e: IOException) {
            System.err.println("Couldn't load the tape: ${e.message}")
            exitProcess(1)
        }
        println(disassemble(machine, it, options.origin ?: it.first))
        options.screenshot?.let { file ->
            saveScreenshot(machine, file)
            println("Saved ${file.absolutePath}")
        }
        return
    }
    SwingUtilities.invokeLater { EmulatorWindow(roms, options).show() }
}
