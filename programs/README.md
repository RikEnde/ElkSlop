# Programs

Sources for the Electron, built on the host by the `devkit` module. Each one
becomes a cassette file named after it (up to 10 characters), on a UEF tape.

| File | What it is |
|------|------------|
| `hello.bas` | BBC BASIC: a loop, `TAB`, and a procedure |
| `greeting.asm` | 6502 assembler: prints a message through `OSWRCH` |

Git tracks only these examples and this README. Programs you add here, and
the tapes built from them, stay local. To share one, remove its exception
from `.gitignore` or use `git add -f`.

Run one on the emulator, which builds it, loads it and runs it:

```sh
./run.sh --run programs/hello.bas
```

Or build every source here into `programs/build/NAME.uef`:

```sh
./gradlew :devkit:buildPrograms
```

## BASIC: `.bas`

BBC BASIC II, as typed on the Electron. Keywords are upper case; `print` is a
variable. Abbreviations work as on the machine (`P.` is `PRINT`).

Line numbers are optional. A line without one is numbered 10 more than the line
before, starting at 10, so a program that uses `PROC`s and no `GOTO` needs
none. Numbers must go up. Blank lines are skipped, and indentation is kept, as
BASIC keeps it when you type it.

The text is the Electron's character set: printable ASCII, and `£`, which BASIC
stores as `` ` ``. Tabs become spaces.

## Assembler: `.asm`

The syntax of BBC BASIC's inline assembler, so code moves between a BASIC
program's `[ ... ]` and a source file unchanged:

```
oswrch = &FFEE              \ a constant
P% = &2000                  \ where the code goes
.start  LDX #0              \ a label, then an instruction
.loop   LDA text,X : BEQ done
        JSR oswrch : INX : BNE loop
.done   RTS
.text   EQUS "Hello" : EQUB 13 : EQUB 0
```

- `.name` is a label. `name = value` sets a constant, and `P% = value` sets
  where code goes next; set it before the first instruction. Each name is set
  once, except P%.
- `:` separates statements. `\` or `;` starts a comment to the end of the line.
- Numbers are decimal, or hex after `&`. Operands are BASIC expressions: `+`
  `-` `*` `/` `DIV` `MOD` `AND` `OR` `EOR` `NOT`, comparisons, brackets,
  `ASC"A"`, `TRUE` and `FALSE`. Strings for `EQUS` join with `+` and can use
  `CHR$13`. As in BASIC, `DIV256` is `DIV 256`.
- `EQUB`, `EQUW` and `EQUD` store 1, 2 and 4 bytes, and `EQUS` a string; unlike
  BASIC's, each takes a list, as in `EQUB 1, 2, 3`.
- Mnemonics may be in any case; labels and constants are case sensitive. Only
  the documented opcodes, as in BASIC.
- Labels can be used before they are defined. Each instruction's size is fixed
  in the first pass: an address known by then that fits in a byte gets zero
  page addressing, one that isn't gets absolute.

The code loads where it was assembled. It is run with `*LOAD name` and then
`CALL` at its first byte, so put the entry point first, or give another with
`--exec`. It may end with `RTS` to go back to BASIC. Not `*RUN`: in the Electron's
OS 1.00, `*RUN` from tape overwrites BASIC's zero page, and code that returns
gets "Syntax error".

## The `devkit` command

`:devkit:buildPrograms` runs the `devkit` command line on this directory. To
run it yourself, for example to put several programs on one tape, such as a
BASIC loader and the machine code it loads:

```sh
./gradlew :devkit:run --args="-o build/game.uef programs/loader.bas programs/game.asm"
```

```
Usage: devkit [options] SOURCE...
  -o FILE         The tape to write (default: the first source's name, .uef)
  --name NAME     The name on tape, up to 10 characters (one source only)
  --exec ADDR     Where machine code starts: a label or hex address (one source
                  only; default: its first byte)
  --symbols       Print the labels and constants of each assembled source
  --all DIR       Build each source in DIR into its own tape, in the -o directory
```

`--symbols` lists each label's address, ready for the emulator's `--break`.
