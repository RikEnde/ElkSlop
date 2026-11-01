# Acknowledgements

Elkslop exists because many people spent years working out how the Acorn
Electron behaves, and wrote it down. This is a record of the work it relies on.

## The machine

The Acorn Electron was designed by Acorn Computers in 1983. Its OS ROM hides a
list of thanks at `&FC00-&FEFF`, where the I/O hardware means no program can
read it. We reproduce it here so it can finally be seen:

> Thanks are due to the following contributors to the development of the
> Electron (among others too numerous to mention):- Bob Austin, Astec, Harry
> Barman, Paul Bond, Allen Boothroyd, Ben Bridgewater, Cambridge, John Cox,
> Chris Curry, 6502 designers, Jeremy Dion, Tim Dobson, Joe Dunn, Ferranti,
> Steve Furber, David Gale, Andrew Gordon, Martyn Gilbert, Lawrence Hardwick,
> Hermann Hauser, John Herbert, Hitachi, Andy Hopper, Paul Jephcot, Brian Jones,
> Chris Jordan, Computer Laboratory, Tony Mann, Peter Miller, Trevor Morris,
> Steve Parsons, Robin Pain, Glyn Phillips, Brian Robertson, Peter Robinson,
> David Seal, Kim Spence-Jones, Graham Tebby, Jon Thackray, Topexpress, Chris
> Turner, Hugo Tyson, John Umney, Alex van Someren, Geoff Vincent, Adrian Warner,
> Robin Williamson, Sophie Wilson.

The quote is otherwise verbatim, apart from spacing after commas; the ROM credits
Sophie Wilson under her former name. BBC BASIC, which the Electron runs, was
written by Sophie Wilson.

## Emulators we learned from

- **[MAME](https://github.com/mamedev/mame)**: the Electron driver and ULA
  device (`src/mame/acorn/electron.cpp`, `electron_ula.cpp`), copyright Wilbert
  Pol and Nigel Barnes, BSD-3-Clause. Our ULA follows its register behavior,
  palette wiring, interrupt timing and keyboard layout, and we checked our work
  against it throughout.
- **[Elkulator](https://github.com/dmcoles/elkulator)** and its contributors:
  a second reference for the ULA, sound and cassette. Our BASIC ROM image comes
  from the [Elkulator distribution](https://elkulator.acornelectron.co.uk/ElkulatorV1.0Win.zip).

## ROM disassemblies

- **Tom Seddon**:
  [Electron OS disassembly](https://github.com/tom-seddon/electron_os_disassembly),
  which rebuilds OS 1.00 byte for byte, and the source of our OS ROM image.
- **J.G. Harston**: the commented MOS 1.00 disassembly behind most of its
  comments, and the BBC BASIC II source reconstruction at
  [mdfs.net](https://mdfs.net/Software/BBCBasic/BBC/).

They have no licence and aren't distributed with Elkslop;
[`docs/rom-sources/README.md`](docs/rom-sources/README.md) says where to find them.

## 6502 tests and references

- **Thomas Harte and contributors**:
  [SingleStepTests 65x02](https://github.com/SingleStepTests/65x02) (MIT), whose
  per-cycle vectors check every one of our 256 opcodes.
- **Klaus Dormann**:
  [6502 functional test](https://github.com/Klaus2m5/6502_65C02_functional_tests)
  (GPL-3.0), included as a test fixture.
- **Norbert Landsteiner**:
  [6502 instruction set reference](https://www.masswerk.at/6502/6502_instruction_set.html),
  including the undocumented opcodes and their names.
- **Neil Parker**: [6502 opcode encoding](https://llx.com/Neil/a2/opcodes.html).
- **Michael Steil**: [how the 6502 decodes instructions](https://www.pagetable.com/31).

Licenses and pinned versions for the test fixtures are in
[`cpu/src/jvmTest/resources/reference/README.md`](cpu/src/jvmTest/resources/reference/README.md).

## Documentation

- The *Acorn Electron Advanced User Guide*, for the ULA's registers, palette
  wiring and sound.
- The Acorn community, especially the [StarDot forums](https://stardot.org.uk),
  where much of this knowledge is kept alive.

## Tools

Built with [Kotlin](https://kotlinlang.org) and [Gradle](https://gradle.org).

## How this was written

Most of Elkslop's code was written by AI coding assistants (OpenAI Codex, then
Anthropic's Claude), directed by Rik. Everything that made that possible is
listed above.

If we've missed anyone, or credited something wrongly, please tell us.
