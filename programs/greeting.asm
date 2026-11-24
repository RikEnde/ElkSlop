\ SPDX-FileCopyrightText: 2026 The Elkslop contributors
\ SPDX-License-Identifier: BSD-3-Clause

\ Prints a line through the OS, then returns to BASIC. The syntax is BBC
\ BASIC's assembler: labels start with a dot, & is hex, and P% is where
\ the code goes.

oswrch = &FFEE              \ Write the character in A
osnewl = &FFE7              \ Write a new line

P% = &2000
.start
    LDX #0
.loop
    LDA message,X
    BEQ done                \ A zero byte ends the message
    JSR oswrch
    INX
    BNE loop
.done
    JMP osnewl              \ Returns to BASIC when it returns

.message
    EQUS "Hello from 6502 code" : EQUB 0
