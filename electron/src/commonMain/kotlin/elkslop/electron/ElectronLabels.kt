// SPDX-FileCopyrightText: 2026 The Elkslop contributors
// SPDX-License-Identifier: BSD-3-Clause

package elkslop.electron

/**
 * Names for the addresses Electron software calls and pokes most: the OS
 * entry points at the top of memory (shared with the BBC Micro), the OS
 * vectors in page 2, and the ULA's registers. A debugger or disassembler can
 * show `JSR OSWRCH` instead of `JSR $FFEE`.
 *
 * The OS entry point and vector names are Acorn's, as given in the Advanced
 * User Guide (see ACKNOWLEDGEMENTS.md). The `ULA_` names are Elkslop's own.
 */
object ElectronLabels {
    private val names: Map<Int, String> = mapOf(
        // OS entry points.
        0xFFC2 to "GSINIT", 0xFFC5 to "GSREAD", 0xFFC8 to "NVRDCH", 0xFFCB to "NVWRCH",
        0xFFCE to "OSFIND", 0xFFD1 to "OSGBPB", 0xFFD4 to "OSBPUT", 0xFFD7 to "OSBGET",
        0xFFDA to "OSARGS", 0xFFDD to "OSFILE", 0xFFE0 to "OSRDCH", 0xFFE3 to "OSASCI",
        0xFFE7 to "OSNEWL", 0xFFEE to "OSWRCH", 0xFFF1 to "OSWORD", 0xFFF4 to "OSBYTE",
        0xFFF7 to "OSCLI",
        // OS vectors.
        0x0200 to "USERV", 0x0202 to "BRKV", 0x0204 to "IRQ1V", 0x0206 to "IRQ2V",
        0x0208 to "CLIV", 0x020A to "BYTEV", 0x020C to "WORDV", 0x020E to "WRCHV",
        0x0210 to "RDCHV", 0x0212 to "FILEV", 0x0214 to "ARGSV", 0x0216 to "BGETV",
        0x0218 to "BPUTV", 0x021A to "GBPBV", 0x021C to "FINDV", 0x021E to "FSCV",
        0x0220 to "EVNTV", 0x0222 to "UPTV", 0x0224 to "NETV", 0x0226 to "VDUV",
        0x0228 to "KEYV", 0x022A to "INSV", 0x022C to "REMV", 0x022E to "CNPV",
        // ULA registers.
        0xFE00 to "ULA_IRQ", 0xFE02 to "ULA_SCREEN_LO", 0xFE03 to "ULA_SCREEN_HI",
        0xFE04 to "ULA_CASSETTE", 0xFE05 to "ULA_PAGING", 0xFE06 to "ULA_SOUND",
        0xFE07 to "ULA_CONTROL",
    )

    /** The name for [address], or null. Fits the `label` parameter of `Disassembler`. */
    fun forAddress(address: Int): String? = names[address and 0xFFFF]
}
