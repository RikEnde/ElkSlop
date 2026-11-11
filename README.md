# Elkslop

An Acorn Electron emulator being built in Kotlin. It has three modules:

- `cpu`: a standalone NMOS 6502 CPU, all 256 opcodes.
- `electron`: the ULA, memory map, machine clock, and a headless `ElectronMachine`
  that boots the real OS and BASIC ROMs.
- `desktop`: a JVM window (Swing) with keyboard, sound, paste-as-typing, and a
  cassette recorder.

The OS and BASIC ROMs are included (see [ROMs](#roms)), so it runs straight
after cloning. So are some cassettes, including the Electron's Introductory
Cassette (see [Tapes](#tapes)).

## Run it

```sh
./run.sh
```

`run.sh` builds the emulator if needed and passes options straight through, for
example `./run.sh --scale 2` or `./run.sh --type "PRINT 6*7\n"`. It needs a JDK
17 or newer and uses `$JAVA_HOME`, else `.tools/jdk`, else `java` on the PATH.

Through Gradle instead:

```sh
./gradlew :desktop:run --args="--scale 2"
```

Or build a standalone jar and run it with any JDK 17+. The OS and BASIC ROMs
are built into the jar, so it runs from any directory:

```sh
JAVA_HOME="$PWD/.tools/jdk" ./gradlew :desktop:fatJar
java -jar desktop/build/libs/elkslop.jar
```

### Standalone app

To get an app that needs neither Gradle nor an installed Java, build it with
`jpackage`. It bundles a trimmed Java runtime (about 80 MB in all) and the
cassettes in `tapes/`, and Insert Tape opens on those:

```sh
JAVA_HOME="$PWD/.tools/jdk" ./gradlew :desktop:packageApp   # desktop/build/jpackage/app-image/Elkslop.app
JAVA_HOME="$PWD/.tools/jdk" ./gradlew :desktop:packageDmg   # desktop/build/jpackage/dmg/Elkslop-1.0.0.dmg
```

`jpackage` comes with JDK 17+, and the tasks use the JDK that runs Gradle. It
only builds for the OS it runs on. On Windows and Linux, `packageApp` makes a
folder holding the launcher. `packageDmg` is macOS only. There are no tasks yet for
`jpackage`'s `exe`/`msi` or `deb`/`rpm` installers. The app isn't signed with a Developer ID,
so macOS blocks it the first time: right-click it and choose Open. Options work
too, for example `Elkslop.app/Contents/MacOS/Elkslop --scale 2`.

Options:

```
--roms DIR          Directory with os.rom and basic.rom (default: built in)
--scale 1|2|3       Window size; 1 is 640 x 512 (default: 1)
--no-sound          Disable audio output
--tape FILE         Put a UEF cassette image in the recorder
--type TEXT         Type TEXT after boot; \n starts a new line
--screenshot FILE   Save a PNG of the window, then exit
--disassemble A-B   Run without a window, then print the machine code from hex address
                    A to B, as in --disassemble D940-D9FF; with --screenshot, the
                    picture comes from the same run
--origin ADDR       Number the listing from hex ADDR, for code that is copied there to run
--frames N          Frames to run before the screenshot or listing (default: 150)
```

`--disassemble` lists memory as the CPU sees it after the run (RAM, the paged
ROM or the OS), reading it without side effects. Run a program first with
`--type`, and raise `--frames` for one that loads from tape. Addresses take an
optional `$`, `&` or `0x`. The listing uses `$` hex, marks undocumented opcodes
with `*`, and follows the format of C64slop's disassembler:

```sh
./run.sh --disassemble FFE3-FFF9
FFE3  C9 0D     CMP #$0D
FFE5  D0 07     BNE $FFEE
...
FFEE  6C 0E 02  JMP ($020E)
```

The disassembler itself is `cpu/src/commonMain/kotlin/elkslop/cpu/Disassembler.kt`.
It knows no machine: it reads through a side-effect-free `peek` and can take a
function that names addresses, so a front end can show `JSR OSWRCH`.

### Keyboard

**Keys > Keyboard Layout** switches between two layouts:

- **Physical** is the default and suits games. Keys map by position, as in MAME,
  so shifted symbols are the Electron's own: Shift+2 is `"`, Shift+' is `*`,
  Shift+; is `+`, Shift+- is `=`, Shift+0 is `@`. On macOS, Java takes letters and
  digits from your host layout and punctuation from the physical key.
- **Natural** suits typing. Each key types the character your own keyboard
  produces, and the title bar shows `[NATURAL]`. Letters still follow CAPS LK. It
  handles one key at a time: the key you pressed last replaces any key still held,
  and games that read several keys at once won't work. A dead key types its symbol
  straight away, and characters the Electron doesn't have do nothing.

The other host keys are the same in both layouts, except that `=` `\` `[` `]` only
stand in for the cursor symbols in Physical:

| Electron | Host |
|---|---|
| CAPS LK / FUNC | Tab or Alt/Option; Shift+Tab toggles caps lock |
| COPY | End; Shift+End is `[`, Ctrl+End is `]` |
| DELETE | Backspace or Delete |
| ESCAPE | Esc |
| BREAK | F12 (Ctrl+F12 hard reset, Shift+F12 autoboot) |
| Cursor keys | Arrows, or `=` `\` `[` `]` for their symbols |

The host Caps Lock key can't be used because Java only reports its toggle state.
As on a real Electron, CAPS LK alone acts as FUNC; SHIFT + CAPS LK toggles caps
lock. The title bar shows `[CAPS]` while it's on, standing in for the LED.

The **Keys** menu has the keys a host keyboard lacks: BREAK, CTRL+BREAK (hard
reset), SHIFT+BREAK (autoboot), COPY, `[`, `]`, CAPS LK, ESCAPE, DELETE, and function keys
f0-f9 (FUNC + digit), plus the layout choice. **FUNC + Next Key** holds FUNC until you press another key,
for FUNC + letter BASIC keyword shortcuts. Menu keys are tapped through the same
typist as pasted text, with modifiers pressed a frame before the last key; the
OS ignores FUNC + digit if both go down in the same frame.

**Help > Keyboard** shows this mapping in the app. F9 pauses. **Edit > Paste as Typing** (Cmd+V on macOS, Ctrl+Shift+V elsewhere)
types the clipboard into the Electron, which is the easiest way to enter a BASIC
listing. It reads the CAPS LK LED to choose Shift for each letter. Characters the
Electron keyboard can't produce are skipped and listed.

## Platform independence

The emulator itself, `cpu` and `electron`, lives in `commonMain` source sets and
depends only on the common Kotlin standard library: no Java APIs, operating-system
APIs, threads, UI toolkit, or browser runtime. Both modules declare JVM and
Kotlin/Native macOS, Linux, and Windows targets. There are no JavaScript or Wasm
targets.

JVM is one way to run the same core, not a requirement of its design. The shared
unit tests also run as native executables. Loading test fixtures, ROM files and
tapes lives in `jvmTest`; its JVM file APIs are not production dependencies. Only the
`desktop` front end is JVM-only, because it uses Swing and `javax.sound`.

## Build and test

Use a JDK 17 or newer. The Gradle wrapper pins Gradle 9.7.0
and verifies its archive checksum; Kotlin is pinned to 2.4.21.

```sh
./gradlew :cpu:jvmTest :electron:jvmTest :desktop:jvmTest
```

On this development machine, a local JDK is available without a global install:

```sh
JAVA_HOME="$PWD/.tools/jdk" ./gradlew :cpu:jvmTest :cpu:macosArm64Test :electron:jvmTest :electron:macosArm64Test
```

The first build downloads Gradle/Kotlin dependencies. Native builds also download
the Kotlin/Native compiler and dependencies; macOS linking requires Xcode.
Tests use checked-in fixtures, ROMs and tapes; they download nothing.

Other native test tasks include `macosX64Test`, `linuxX64Test`, `linuxArm64Test`,
and `mingwX64Test`. Run them on a compatible host; declaring a target does not
mean its tests have been run. JVM and macOS ARM64 are the initial verification
hosts. Native Kotlin libraries can be built with `:cpu:assemble` and
`:electron:assemble`.

## CPU module

The production code is in `cpu/src/commonMain/kotlin/elkslop/cpu`.

- `MemoryBus.kt`: the boundary between the CPU and the machine.
- `CpuState.kt`: validated register values for inspection and initialization.
- `StatusFlag.kt`: named processor-status bits.
- `Instruction.kt`: instruction and addressing-mode names, and the
  `UndocumentedOpcodeException` thrown in strict mode.
- `InstructionSet.kt`: explicit lookup table for all 256 opcodes, with the 105
  undocumented ones in a separate, marked section.
- `Cpu6502.kt`: instruction execution, address calculation, arithmetic, stack,
  and interrupt handling. Each instruction emits its actual read/write sequence;
  cycle counts are derived from those accesses rather than a separate table.

### Connecting a machine

Implement `MemoryBus` to route reads and writes to RAM, ROM, or hardware:

```kotlin
import elkslop.cpu.Cpu6502
import elkslop.cpu.MemoryBus

val memory = ByteArray(65536)
val bus = object : MemoryBus {
    override fun read(address: Int): Int = memory[address].toInt() and 0xFF

    override fun write(address: Int, value: Int) {
        memory[address] = value.toByte()
    }
}

// A tiny program: LDA #$42; STA $80; JMP $0204.
memory[0x0200] = 0xA9.toByte()
memory[0x0201] = 0x42
memory[0x0202] = 0x85.toByte()
memory[0x0203] = 0x80.toByte()
memory[0x0204] = 0x4C
memory[0x0205] = 0x04
memory[0x0206] = 0x02
memory[0xFFFC] = 0x00
memory[0xFFFD] = 0x02

val cpu = Cpu6502(bus)
cpu.reset() // Seven bus cycles; loads PC from $FFFC/$FFFD.
cpu.step()  // LDA: two cycles.
cpu.step()  // STA: three cycles. memory[$80] is now $42.
```

`step()` completes one instruction, or services one interrupt, and returns its
CPU cycle count. `totalCycles` accumulates bus cycles, including reset. It is not
reset by `reset()` or `loadState()`.

Every bus call represents a CPU cycle, including dummy reads and the original-
value write of an NMOS read-modify-write instruction. The machine can advance
its hardware clock in those calls and account for contention separately. The
CPU itself performs no sleeping, pacing, allocation of machine memory, or
host I/O. Bus callbacks must not re-enter the CPU or throw.

Registers are readable but not directly mutable. `state()` returns their values;
`loadState()` validates and loads them without accessing memory. It clears pending
NMI and resamples I, preserves the IRQ input and cycle count, and is intended for
tests/debuggers. It is not an exact whole-machine save-state facility.

### Implemented behavior

- All 256 NMOS 6502 opcodes: the 151 documented ones and the 105 undocumented
  ones (see below), with their addressing modes and bus cycles.
- Binary and NMOS decimal ADC/SBC, including NMOS flag behavior.
- Zero-page, stack, and 16-bit address wrapping; indirect JMP page-wrap quirk.
- Page-crossing cycles, taken-branch cycles, dummy accesses, and RMW double writes.
- Reset, BRK, IRQ, NMI, RTI, and the status-byte conventions for stack operations.
- IRQ-mask delay after CLI, SEI, and PLP; immediate mask restoration after RTI.

IRQ is level-sensitive: set `irqAsserted` and leave it set until its source is
cleared. `requestNmi()` represents one active NMI edge and latches a pending
request. At a step boundary, pending NMI wins over eligible IRQ. Reset clears
pending NMI but leaves the externally driven IRQ level alone.

This is an instruction-stepped CPU with bus-access sequencing, **not a pin-level
6502 model**. IRQ/NMI are serviced at step boundaries. Exact intra-instruction
interrupt polling, branch interrupt-poll quirks, NMI hijacking of BRK/IRQ vectors,
RDY stalls, SO input, and asynchronous reset are not implemented. Full Electron
timing compatibility will require further work around these boundaries.

#### Undocumented opcodes

They run as on a real NMOS 6502, because some Electron software uses them:

- Stable combinations: SLO, RLA, SRE, RRA, DCP, ISC (read-modify-write, then an
  ALU operation), SAX, LAX, ANC, ALR, ARR (including its NMOS decimal-mode
  behavior), SBX, LAS, the `$EB` duplicate of SBC, and 27 NOPs that still perform
  their addressing mode's reads.
- Unstable on real chips: ANE (`$8B`) and LXA (`$AB`) use the magic constant
  `$EE`; SHA, SHX, SHY and TAS AND the value with the base address's high byte + 1,
  and on a page crossing the value also replaces the address's high byte. These
  follow the reference vectors; software relying on them is unreliable on real
  Electrons too.
- JAM (12 opcodes) halts the CPU: `halted` becomes true, IRQ and NMI are ignored,
  and each `step()` performs one idle read of `$FFFF` so machine time continues.
  `reset()` restarts it. The desktop app pauses and reports a JAM.

Set `rejectUndocumentedOpcodes = true` for a strict mode that throws
`UndocumentedOpcodeException` after the opcode fetch instead. This helps catch a
program, or an emulator bug, that has jumped into data.

Hardware power-on register values
are undefined; the core starts deterministically with zeroed A/X/Y/PC/SP and I
set. The initial reset takes SP from $00 to $FD. Subsequent resets decrement the
current SP by three and preserve A/X/Y and decimal mode.

### Verification

`commonTest` covers reset/interrupt sequences, stack behavior, bus accesses,
address boundaries, flags, and errors. Arithmetic tests exhaust all 131,072
input/carry combinations for each binary ADC and SBC, plus 40,000 valid BCD
addition/subtraction cases.

`jvmTest` additionally runs:

- 60,700 pinned [SingleStepTests](https://github.com/SingleStepTests/65x02)
  cases covering all 256 opcodes, comparing registers, memory, every bus
  address/value/direction, and cycle counts. This includes all 10,000 cases for
  immediate ADC, SBC and ARR, with valid and invalid decimal digits.
- [Klaus Dormann's functional test](https://github.com/Klaus2m5/6502_65C02_functional_tests),
  starting at $0400 and requiring the success loop at $3469, with an instruction
  limit to make failures terminate.

See `cpu/src/jvmTest/resources/reference/README.md` for provenance and licenses.
These tests establish the CPU behavior above; they do not establish
complete Electron compatibility or pin-level interrupt accuracy.

## Electron machine

The production code is in `electron/src/commonMain/kotlin/elkslop/electron`.
The ULA is a separate component from the CPU, but it lives in the same module as
the bus and clock: it decides ROM paging, interrupts, display fetches, and when
the CPU may access RAM, so those parts cannot be separated cleanly.

- `ElectronMachine.kt`: owns RAM, ULA, clock, bus, keyboard, and CPU; `step()`,
  `runForTicks()`, `reset()` (BREAK), and `requestNmi()`.
- `ElectronMemoryBus.kt`: address decoding. RAM at 0000-7FFF, paged ROM at
  8000-BFFF (keyboard in pages 8-9, BASIC in 10-11, optional sideways ROMs),
  FC00-FDFF FRED/JIM (unconnected), ULA at FE00-FEFF, OS ROM elsewhere.
- `ElectronClock.kt`: machine time in 2 MHz ticks. ROM runs at 2 MHz; RAM and
  I/O at 1 MHz, with the 1 MHz phase-alignment penalty. In modes 0-3 the CPU
  waits for RAM while the display is fetching.
- `ElectronUla.kt`: the register file (repeated every 16 bytes in page FE).
- `UlaInterrupts.kt`, `UlaVideo.kt`, `UlaPalette.kt`, `UlaSound.kt`,
  `UlaCassette.kt`, `KeyboardMatrix.kt`: one ULA function each.

Video renders the last completed frame as 640 x 256 0xRRGGBB pixels via
`ula.video.pixel()`, `copyFrame()` or `copyFrameInto()`. Sound exposes the
speaker's one-bit level, and `ula.sound.startSampling(rate)` resamples it into
audio samples for a host to play. The cassette works on decoded bits through
`CassettePort`, and `UefTape` plays a UEF image through it, moving only while the
motor runs. It takes plain UEF bytes, so hosts unpack gzipped images themselves.
It does not demodulate analog audio. Saving to tape isn't implemented: `SAVE`
runs, but nothing records the output.

Known simplifications: whole-line 312/313 frames rather than PAL half-line sync;
interrupts are sampled at instruction boundaries; display-end timing is at the
start of the line rather than 24-40 cycles after the last pixel.

`electron` tests cover interrupts, paging, palette wiring, keyboard decoding,
sound period and sampling, interrupt lines, rendering, contention, and each UEF
chunk type. `jvmTest` also boots the ROMs and checks the start-up banner and
prompt by matching screen RAM against the OS font. It types `PRINT 6*7` and
expects `42`, runs an undocumented opcode from BASIC, and draws MODE 2 colour
bars, checking each colour. It also loads the first program from both
Introductory Cassettes through the OS and compares RAM with the file on the tape.
Frames are written to `electron/build/test-frames/`. The boot tests skip with a
message if the ROM files are missing; the tape tests fail.

## Desktop front end

`desktop/src/jvmMain/kotlin/elkslop/desktop` is JVM-only and uses only the JDK
(Swing and `javax.sound`). It contains no emulation logic.

- `Emulator.kt`: runs the machine on its own thread, one frame at a time. Other
  threads send it commands through a queue and read finished frames. With sound,
  the blocking audio device paces the emulator, so the sound card's clock is the
  master clock (about 67 ms of buffer). Without sound, it sleeps to match
  emulated time to wall time. With Fast Loading on, it doesn't pace at all while
  the machine reads a tape. If the CPU executes a JAM opcode (which halts a
  real 6502) or the machine stops on an error, it pauses and a dialog explains
  how to recover.
- `ScreenPanel.kt`: draws the 640 x 256 frame doubled vertically (5:4),
  letterboxed, with nearest-neighbour scaling.
- `AudioOutput.kt`: 48 kHz 16-bit mono. The ULA resamples its one-bit speaker
  output by averaging over each sample period (`UlaSound.startSampling`).
- `NaturalKeyboard.kt`: the Natural layout's modifier overrides and event queue.
- `KeyMapping.kt`, `TextTyper.kt`: host key mappings, and the typist behind
  paste-as-typing and the Keys menu (each key held for 3 frames, released for 3,
  with a longer pause after RETURN; modifiers go down a frame before the last key).
- `TapeFile.kt`: reads UEF images, gzipped or plain.
- `EmulatorWindow.kt`, `Main.kt`: menus, options, and ROM loading.
- `AppIcon.kt`: the pixel-art acorn used as the window and Dock icon, drawn
  from a 32 x 32 grid in code.

`desktop` tests type every mappable symbol into BASIC on the real ROMs and check
the result on screen. They also check that SHIFT + CAPS LK toggles caps lock
(and CAPS LK alone doesn't), that FUNC + 1 runs a `*KEY1` definition, that
CTRL stays held through a CTRL + BREAK reset, that tape images load gzipped or
plain, that Fast Loading loads a whole program quickly and speeds up only while a
tape is playing, and that the icon renders.

## License

Elkslop is licensed under the [BSD 3-Clause License](LICENSE). Third-party files
keep their own licenses: the CPU test fixtures (MIT and GPL-3.0-or-later) and
the Gradle wrapper (Apache-2.0). The ULA follows MAME's BSD-3-Clause Electron
driver, credited in [NOTICE](NOTICE). Source files carry SPDX headers, and
[REUSE.toml](REUSE.toml) covers files that can't. The two ROM images in `roms/`
and the cassette images in `tapes/` are © Acorn and included without a licence
(see [LICENSES/LicenseRef-Acorn-ROM.txt](LICENSES/LicenseRef-Acorn-ROM.txt)); the
ROM source reconstructions are not distributed.

## Acknowledgements

See [ACKNOWLEDGEMENTS.md](ACKNOWLEDGEMENTS.md) for the people and projects
Elkslop relies on, including the Electron's own hidden ROM credits.

## ROM sources

Commented assembly for both ROMs is useful to read alongside the emulator:

- **OS 1.00:** [tom-seddon/electron_os_disassembly](https://github.com/tom-seddon/electron_os_disassembly),
  a disassembly that rebuilds the ROM byte for byte.
- **BBC BASIC II:** J.G. Harston's source reconstruction, `Basic2.zip` at
  [mdfs.net](https://mdfs.net/Software/BBCBasic/BBC/).

Neither has a licence, so they aren't in this repository. To download them into
`docs/rom-sources/` and check them against the SHA-256 of the versions we used, run:

```sh
docs/rom-sources/fetch.sh
```

[docs/rom-sources/README.md](docs/rom-sources/README.md) describes them and how
they relate to our ROMs.

## ROMs

`roms/os.rom` and `roms/basic.rom` are the Electron ROM images, each 16 KiB,
included in the repository so the emulator works straight after cloning. The
ROM tests use them, and the desktop build copies them into its jar and app. They
are not packaged into the `cpu` or `electron` libraries. They are © Acorn and
not covered by Elkslop's licence (see [License](#license)).

- `os.rom` is a direct dump of the OS 1.00 chip, from
  [tom-seddon/electron_os_disassembly](https://github.com/tom-seddon/electron_os_disassembly)
  (`orig/elk100.rom`, SHA-256 `efc902c0...8350`).
- `basic.rom` comes from the
  [Elkulator distribution](https://elkulator.acornelectron.co.uk/ElkulatorV1.0Win.zip).

Elkulator's OS ROM also works, but was dumped through a running Electron and
has I/O register values in 48 places in `&FC00-&FEFF`, which garble the hidden
credits text. With the ULA read-back the emulator uses by default, it never shows
those addresses, so it behaves the same with either. See [docs/rom-sources/README.md](docs/rom-sources/README.md).

## Tapes

[`tapes/`](tapes/README.md) holds the Introductory Cassette that came with the
Electron, in English and German, and Acornsoft's Peeko-Computer, as UEF images
from [The Stairway to Hell](https://www.stairwaytohell.com/electron/other.html).
They are © Acorn and Acornsoft and not covered by Elkslop's licence.
[tapes/README.md](tapes/README.md) lists what's on each one and where it came from.

To play one, choose **Tape > Insert Tape...** or start with a tape already in:

```sh
./run.sh --tape tapes/intro-english.uef --type 'CHAIN""\n'
```

Tapes load at the real 1200 baud, about 120 bytes a second, so the longer
programs take minutes. To skip the wait, turn on **Tape > Fast Loading**. It's
off each time you start the emulator. The whole machine then runs flat out while
it reads the tape (about 36x on the machine we tried), and the sound stops until
it returns to normal speed. Software still sees the real timing, so anything
that loads at normal speed should load the same way.

The title bar shows the tape's name, `[FAST]` when Fast Loading is on, and
`[MOTOR]` while the tape is moving. **Tape > Rewind** winds back to the start;
there is no fast forward.
Other `.uef` files work too, gzipped or plain, if they use the common chunks.
Tapes that rely on security cycles or other protection tricks may not load.
