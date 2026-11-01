// SPDX-FileCopyrightText: 2026 The Elkslop contributors
// SPDX-License-Identifier: BSD-3-Clause

package elkslop.desktop

import elkslop.electron.UlaVideo
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.awt.image.DataBufferInt
import javax.swing.JPanel

/**
 * Shows the latest frame. The ULA's 640 x 256 frame is drawn with each line
 * doubled (a 5:4 picture, as on a TV), scaled to fit and letterboxed in black.
 */
class ScreenPanel(private val emulator: Emulator) : JPanel() {
    private val image = BufferedImage(UlaVideo.WIDTH, UlaVideo.HEIGHT, BufferedImage.TYPE_INT_RGB)
    private val pixels = (image.raster.dataBuffer as DataBufferInt).data

    var scale: Int = 1
        set(value) {
            field = value
            preferredSize = Dimension(BASE_WIDTH * value, BASE_HEIGHT * value)
            revalidate()
        }

    init {
        background = Color.BLACK
        isFocusable = true
        focusTraversalKeysEnabled = false // Tab is the Electron's FUNC key.
        isDoubleBuffered = true
        scale = 1
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        emulator.copyFrame(pixels)
        val target = fitRectangle(width, height)
        (g as Graphics2D).setRenderingHint(
            RenderingHints.KEY_INTERPOLATION,
            RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR,
        )
        g.drawImage(image, target.x, target.y, target.width, target.height, null)
    }

    /** Renders the current frame exactly as on screen, for --screenshot. */
    fun snapshot(): BufferedImage {
        val result = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g = result.createGraphics()
        paint(g)
        g.dispose()
        return result
    }

    companion object {
        const val BASE_WIDTH = UlaVideo.WIDTH
        const val BASE_HEIGHT = UlaVideo.HEIGHT * 2

        /** Largest 5:4 rectangle centred in a width x height area. */
        fun fitRectangle(width: Int, height: Int): java.awt.Rectangle {
            val w = minOf(width, height * BASE_WIDTH / BASE_HEIGHT)
            val h = w * BASE_HEIGHT / BASE_WIDTH
            return java.awt.Rectangle((width - w) / 2, (height - h) / 2, w, h)
        }
    }
}
