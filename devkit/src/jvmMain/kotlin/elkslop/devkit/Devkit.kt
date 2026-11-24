// SPDX-FileCopyrightText: 2026 The Elkslop contributors
// SPDX-License-Identifier: BSD-3-Clause

package elkslop.devkit

import java.io.File
import kotlin.system.exitProcess

/** A source file that can't be built. The message names the file and line: `hello.asm:12: No such label: loop`. */
class BuildError(message: String) : Exception(message)

/** A built source: the file to put on tape, and the labels, for machine code. */
class Program(val file: CassetteFile, val symbols: Map<String, Int> = emptyMap())

/** Builds source files from disk: `.bas` for BBC BASIC, `.asm` for the assembler. */
object Devkit {
    val SOURCE_EXTENSIONS = setOf("bas", "asm")

    /**
     * Builds [source]. [name] is its name on tape, by default the file's name
     * cut to 10 characters; [exec] is where machine code starts, a label or a
     * hex address, by default its first byte.
     *
     * @throws BuildError if it can't be built.
     */
    fun build(source: File, name: String = tapeName(source), exec: String? = null): Program {
        val text = try {
            source.readText()
        } catch (e: java.io.IOException) {
            throw BuildError("Can't read ${source.path}: ${e.message}")
        }
        try {
            return when (source.extension.lowercase()) {
                "bas" -> {
                    if (exec != null) throw BuildError("${source.name}: a BASIC program has no exec address")
                    Program(CassetteFile.basic(name, BasicTokeniser.tokenise(text)))
                }
                "asm" -> {
                    val assembly = Assembler.assemble(text)
                    val start = if (exec == null) assembly.start else assembly.symbols[exec] ?: hexAddress(exec)
                        ?: throw BuildError("--exec $exec: no such label or address in ${source.name}")
                    Program(CassetteFile.machineCode(name, assembly, start), assembly.symbols)
                }
                else -> throw BuildError("${source.name}: sources end in .bas (BASIC) or .asm (assembler)")
            }
        } catch (e: SourceError) {
            throw BuildError("${source.path}:${e.line}: ${e.reason}")
        } catch (e: IllegalArgumentException) {
            throw BuildError("${source.path}: ${e.message}")
        }
    }

    /** The name [source] gets on tape: its file name, without the extension, cut to 10 characters. */
    fun tapeName(source: File): String = source.nameWithoutExtension.take(CassetteFile.MAX_NAME_LENGTH)

    /** A UEF tape image holding [programs], in order. */
    fun tape(programs: List<Program>): ByteArray = UefWriter.write(programs.map { it.file })

    /** `2000`, `&2000`, `$2000` or `0x2000`, or null. */
    fun hexAddress(text: String): Int? =
        text.removePrefix("&").removePrefix("$").removePrefix("0x").toIntOrNull(16)?.takeIf { it in 0..0xFFFF }
}

const val DEVKIT_USAGE = """Usage: devkit [options] SOURCE...
Builds BBC BASIC (.bas) and 6502 assembler (.asm) sources into a UEF tape,
one file on tape for each source, in order. Each is named on tape after its
source file, cut to 10 characters: hello.bas is hello.
  -o FILE         The tape to write (default: the first source's name, .uef)
  --name NAME     The name on tape, up to 10 characters (one source only)
  --exec ADDR     Where machine code starts: a label or hex address (one source
                  only; default: its first byte)
  --symbols       Print the labels and constants of each assembled source
  --all DIR       Build each source in DIR into its own tape, in the -o directory"""

fun main(args: Array<String>) {
    try {
        runDevkit(args.toList())
    } catch (e: BuildError) {
        System.err.println(e.message)
        exitProcess(1)
    } catch (e: IllegalArgumentException) {
        System.err.println(e.message)
        System.err.println(DEVKIT_USAGE)
        exitProcess(2)
    }
}

/** The command line, without exiting; for tests. */
fun runDevkit(args: List<String>, out: (String) -> Unit = ::println) {
    val sources = mutableListOf<File>()
    var output: File? = null
    var name: String? = null
    var exec: String? = null
    var symbols = false
    var all: File? = null
    var i = 0
    fun value(): String = args.getOrNull(++i) ?: throw IllegalArgumentException("${args[i - 1]} needs a value")
    while (i < args.size) {
        when (val arg = args[i]) {
            "-o" -> output = File(value())
            "--name" -> name = value()
            "--exec" -> exec = value()
            "--symbols" -> symbols = true
            "--all" -> all = File(value())
            "--help", "-h" -> { out(DEVKIT_USAGE); return }
            else -> if (arg.startsWith("-")) throw IllegalArgumentException("Unknown option: $arg") else sources += File(arg)
        }
        i++
    }

    fun report(source: File, program: Program) {
        if (!symbols || program.symbols.isEmpty()) return
        out("${source.name}:")
        for ((label, value) in program.symbols.entries.sortedBy { it.value }) {
            out("  %-20s &%04X".format(label, value and 0xFFFF))
        }
    }

    all?.let { directory ->
        if (sources.isNotEmpty() || name != null || exec != null) {
            throw IllegalArgumentException("--all builds a whole directory: no sources, --name or --exec with it")
        }
        val files = directory.listFiles { file -> file.isFile && file.extension.lowercase() in Devkit.SOURCE_EXTENSIONS }
            ?.sortedBy { it.name }
            ?: throw BuildError("No directory ${directory.path}")
        files.groupBy { it.nameWithoutExtension }.values.firstOrNull { it.size > 1 }?.let { same ->
            throw BuildError("${same.joinToString(" and ") { it.name }} would both be ${same[0].nameWithoutExtension}.uef: rename one")
        }
        val target = output ?: File(directory, "build")
        target.mkdirs()
        for (source in files) {
            val program = Devkit.build(source)
            val tape = File(target, source.nameWithoutExtension + ".uef")
            tape.writeBytes(Devkit.tape(listOf(program)))
            out("${source.path} -> ${tape.path}")
            report(source, program)
        }
        return
    }

    if (sources.isEmpty()) throw IllegalArgumentException("No source files")
    if (sources.size > 1 && (name != null || exec != null)) {
        throw IllegalArgumentException("--name and --exec go with a single source")
    }
    val programs = sources.map { source ->
        Devkit.build(source, name ?: Devkit.tapeName(source), exec).also { report(source, it) }
    }
    val tape = output ?: File(sources[0].parentFile, sources[0].nameWithoutExtension + ".uef")
    tape.absoluteFile.parentFile?.mkdirs()
    tape.writeBytes(Devkit.tape(programs))
    out("Wrote ${tape.path}: " + programs.joinToString(", ") { describe(it.file) })
}

private fun describe(file: CassetteFile): String =
    if (file.isBasic) "${file.name} (BASIC, ${file.data.size} bytes)"
    else "${file.name} (&%04X-&%04X, exec &%04X)".format(file.load, file.load + file.data.size - 1, file.exec)
