REM Hello, from a source file built outside the Electron.
REM No line numbers: the devkit numbers the lines 10, 20, 30...
MODE 6
FOR I% = 1 TO 5
  PRINT TAB(I% * 2) "Hello, Electron"
NEXT
PROCbye
END
DEF PROCbye
PRINT '"Built with the Elkslop devkit"
ENDPROC
