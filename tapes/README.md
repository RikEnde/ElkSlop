# Tapes

Cassette images for the emulator, in [UEF](https://en.wikipedia.org/wiki/Unified_Emulator_Format)
format. They all come from the [Stairway to Hell](https://www.stairwaytohell.com/electron/other.html)
archive of Electron software. Each file here is the `.uef` from the archive's zip,
unchanged, so it's still gzipped.

| File | What it is | Files on tape | Source zip |
|---|---|---|---|
| `intro-english.uef` | The Introductory Cassette | 21 | [`Intro_E.zip`](https://www.stairwaytohell.com/electron/uefarchive/leisure/Acornsoft/Intro_E.zip) |
| `intro-german.uef` | The German *Einführungskassette* | 17 | [`IntroductoryCassette-German_E.zip`](https://www.stairwaytohell.com/electron/uefarchive/leisure/Acornsoft/IntroductoryCassette-German_E.zip) |
| `peeko-english.uef` | Acornsoft's Peeko-Computer | 8 | [`PeekoComputer_E.zip`](https://www.stairwaytohell.com/electron/uefarchive/educ/Acornsoft/PeekoComputer_E.zip) |

## Playing them

Choose **Tape > Insert Tape...**, or start with a tape in the recorder:

```sh
./run.sh --tape tapes/intro-english.uef --type 'CHAIN""\n'
```

`CHAIN""` loads and runs the next program on the tape. Tapes play at the real
1200 baud, about 120 bytes a second, so the larger programs take a few minutes.

## The Introductory Cassette

The free demonstration tape supplied with the Electron, described in
[chapter 4 of the User Guide](https://acornelectron.co.uk/ugs/electron/acorn_computers/ug-english/chapter004_eng.html).
Most of the programs offer to load the next one when they finish.

The German tape is a translation, not a relabelled copy: its programs have
German names and text. This table pairs them by tape order and name, without
having compared the programs, so a pair may not be exactly the same game.

| English | Bytes | German | Bytes |
|---|---:|---|---:|
| INTRO | 4437 | WILLKOMMEN | 4495 |
| KEYBOARD | 14045 | TASTATUR | 14008 |
| SKETCH | 3518 | MALER | 4200 |
| PIANO | 5996 | KLAVIER | 6086 |
| DODGEMS | 5376 | AUTODRON | 5888 |
| BIORHYTHMS | 4181 | BIORHYTHMS | 4239 |
| CLOCK | 2841 | UHRZEIT | 2951 |
| GOMOKU | 6559 | GOMOKU | 6896 |
| MESSAGE | 1592 | HINWEIS | 1670 |
| GREETER | 1101 | | |
| RIVERGAME | 11986 | | |
| TURTLE | 6034 | | |
| SOLVER | 9340 | | |
| PATTERNS | 2472 | MUSTER | 2518 |
| MARSLANDER | 5428 | LANDUNG | 4765 |
| BUGZAP | 4106 | GALAXIS | 4144 |
| ISLAND | 1792 | INSEL | 1920 |
| ISLESKR | 14276 | ISLESKR | 14276 |
| PLANETS | 1536 | PLANETEN | 4096 |
| PLASKR | 11158 | PLASKR | 11158 |
| BYE | 1472 | BISBALD | 1672 |

- **GREETER, RIVERGAME, TURTLE and SOLVER** go with the book *Start Programming
  With The Electron*, according to
  [this review](https://acornelectron.co.uk/eug/revs/addison/Start_Programming_With_The_Electron_000.html).
  The User Guide's chapter doesn't list them, and the German tape doesn't have them.
- **ISLESKR and PLASKR** are the same size on both tapes and probably belong to
  ISLAND and PLANETS, judging by their names. We haven't confirmed that.
- **Both sides are joined.** Each image is one continuous recording. MESSAGE still
  tells you to turn the tape over: there's nothing to turn, so just press SPACE.
  The next file after it is GREETER, not PATTERNS as its screen suggests.

## Peeko-Computer

An Acornsoft teaching program, written in BASIC, that simulates a simplified
microcomputer to teach the basics of machine code. It has
[ten instructions](https://everygamegoing.com/litem/Peeko-Computer-English-Version/105221),
with mnemonics borrowed from the 6502 such as LDA and STA, and each byte holds a
single decimal digit. The tape credits Acornsoft and no author; the loader says
1984, and the main program says 1983.

| File | Bytes | What it is |
|---|---:|---|
| PEEKO | 1192 | Loader |
| peeko | 5958 | The simulator |
| COUNT, FACTOR, COPY, ADD, PRIMES | 90 each | Example programs |
| V1 | 0 | Empty file at the end of the tape |

The manual, a separate booklet, isn't included.

Acornsoft also released a German version for the Electron, which is
[listed](https://www.stairwaytohell.com/electron/other.html) as
`PeekoComputer-German_E.zip`. We don't have it: the link returned 404 when we
tried on 2 November 2026, and it wasn't in the Internet Archive's
[Electron educational set](https://archive.org/download/acorn-electron-games-uef).

## Checks

All three use only the data, carrier and gap chunks that the emulator reads.
`TapeLoadTest` loads the first program from both Introductory Cassettes through
the real OS and compares RAM with the file on the tape. No test covers Peeko, or
the later programs on the Introductory Cassettes.

| File | SHA-256 of the `.uef` | SHA-256 of the source zip |
|---|---|---|
| `intro-english.uef` | `f935988b...ccf6` | `f5eece52...9dc6` |
| `intro-german.uef` | `3df12c18...9f8c` | `c59b9168...6a5c` |
| `peeko-english.uef` | `24a81110...c246` | `7f2cf496...620e` |

## Copyright

These images are © Acorn Computers Ltd and Acornsoft. They aren't covered by
Elkslop's BSD licence, and no licence for them has been granted to this project;
see [LicenseRef-Acorn-ROM.txt](../LICENSES/LicenseRef-Acorn-ROM.txt).
