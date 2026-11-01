// SPDX-FileCopyrightText: 2026 The Elkslop contributors
// SPDX-FileCopyrightText: Wilbert Pol, Nigel Barnes (portions following MAME's Electron ULA; see NOTICE)
// SPDX-License-Identifier: BSD-3-Clause

package elkslop.electron

/**
 * Display fetches and pixel expansion, independent of a windowing system.
 * Frame pixels are packed 0xRRGGBB, always 640 x 256; lower-resolution pixels
 * are repeated horizontally. Text modes include their two blank raster lines.
 *
 * We alternate 312/313 whole lines for a 50 Hz average. This does not reproduce
 * PAL half-line/interlace sync. See the hardware notes for the chosen phase.
 */
class UlaVideo internal constructor(
    private val ram: ByteArray,
    val palette: UlaPalette,
    private val interrupts: UlaInterrupts,
) {
    var mode: Int = 0
        private set
    var screenStart: Int = 0x3000
        private set
    var scanline: Int = 0
        private set
    var tickInLine: Int = 0
        private set
    var frameNumber: Long = 0
        private set

    private var raster = 0
    private var screenAddress = screenStart
    private var drawing = IntArray(WIDTH * HEIGHT)
    private var completed = IntArray(WIDTH * HEIGHT)

    val displayEndLine: Int get() = if (mode == 3 || mode >= 6) 250 else 256
    val wideMemoryFetch: Boolean get() = mode < 4
    val displayFetching: Boolean
        get() = scanline < displayEndLine && raster < 8 && tickInLine < DISPLAY_TICKS
    val ramContended: Boolean get() = wideMemoryFetch && displayFetching

    /** Copy the last completed frame. The caller owns the returned pixels. */
    fun copyFrame(): IntArray = completed.copyOf()

    /** Copy the last completed frame into [destination] (WIDTH * HEIGHT pixels). */
    fun copyFrameInto(destination: IntArray) {
        require(destination.size >= WIDTH * HEIGHT)
        completed.copyInto(destination)
    }

    /** Read the last completed frame without allocating a copy. */
    fun pixel(x: Int, y: Int): Int {
        require(x in 0 until WIDTH && y in 0 until HEIGHT)
        return completed[y * WIDTH + x]
    }

    internal fun setMode(value: Int) { mode = value and 7 }
    internal fun setStartLow(value: Int) { screenStart = (screenStart and 0x7E00) or ((value and 0xE0) shl 1) }
    internal fun setStartHigh(value: Int) { screenStart = (screenStart and 0x01FF) or ((value and 0x3F) shl 9) }

    internal fun tick() {
        if (scanline < HEIGHT && tickInLine < DISPLAY_TICKS) drawFetch()
        tickInLine++
        if (tickInLine == TICKS_PER_LINE) endLine()
    }

    private fun drawFetch() {
        val fetchTicks = if (wideMemoryFetch) 1 else 2
        if (tickInLine % fetchTicks != 0) return
        val x = tickInLine * 8
        val width = fetchTicks * 8
        if (!displayFetching) {
            drawing.fill(0, scanline * WIDTH + x, scanline * WIDTH + x + width)
            return
        }

        val column = tickInLine / fetchTicks
        val address = wrapScreenAddress(screenAddress + column * 8)
        var data = ram[address].toInt() and 0xFF
        val bitsPerPixel = when (mode) { 1, 5 -> 2; 2 -> 4; else -> 1 }
        val pixelsPerByte = 8 / bitsPerPixel
        val repeat = width / pixelsPerByte
        val penMask = when (bitsPerPixel) { 1 -> 0x08; 2 -> 0x0A; else -> 0x0F }
        var destination = scanline * WIDTH + x
        repeat(pixelsPerByte) {
            // Pixel bits are interleaved: bits 7,5,3,1 form the first 4-bit pen.
            val pen = ((data ushr 4) and 8) or ((data ushr 3) and 4) or
                ((data ushr 2) and 2) or ((data ushr 1) and 1)
            val colour = palette.rgb(pen and penMask)
            repeat(repeat) { drawing[destination++] = colour }
            data = data shl 1
        }
    }

    private fun endLine() {
        tickInLine = 0
        if (scanline < displayEndLine) {
            if (raster < 8) screenAddress++
            raster++
            val characterHeight = if (mode == 3 || mode >= 6) 10 else 8
            if (raster == characterHeight) {
                raster = 0
                screenAddress += if (wideMemoryFetch) 0x278 else 0x138
            }
            screenAddress = wrapScreenAddress(screenAddress)
        }
        scanline++
        if (scanline == 100) interrupts.raise(UlaInterrupt.REAL_TIME_CLOCK)
        if (scanline == displayEndLine) interrupts.raise(UlaInterrupt.DISPLAY_END)
        val linesInFrame = if (frameNumber and 1L == 0L) 312 else 313
        if (scanline == linesInFrame) {
            val oldCompleted = completed
            completed = drawing
            drawing = oldCompleted
            drawing.fill(0)
            frameNumber++
            scanline = 0
            raster = 0
            screenAddress = screenStart
        }
    }

    private fun wrapScreenAddress(address: Int): Int {
        val size = when (mode) {
            0, 1, 2 -> 0x5000
            3 -> 0x4000
            4, 5 -> 0x2800
            else -> 0x2000
        }
        return if (address >= 0x8000) address - size else address
    }

    companion object {
        const val WIDTH = 640
        const val HEIGHT = 256
        const val TICKS_PER_LINE = 128
        const val DISPLAY_TICKS = 80
    }
}
