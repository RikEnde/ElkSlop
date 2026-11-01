// SPDX-FileCopyrightText: 2026 The Elkslop contributors
// SPDX-License-Identifier: BSD-3-Clause

package elkslop.desktop

import java.awt.Image
import java.awt.RenderingHints
import java.awt.Taskbar
import java.awt.image.BufferedImage
import javax.swing.JFrame

/**
 * The application icon: a pixel-art acorn, brown cap and green nut, in the
 * spirit of Acorn Computers' logo. Drawn from a 32 x 32 grid and scaled with
 * nearest-neighbour filtering, so it stays crisp at every size.
 */
object AppIcon {
    private val ART = arrayOf(
        "................................",
        "................................",
        "................................",
        "...............ss...............",
        "..............ss................",
        "..............ss................",
        "..........cCCCCCCCCCCc..........",
        "........cCCCCCCCCCCCCCCc........",
        ".......cCCtCCtCCtCCtCCtCc.......",
        "......cCCCCCCCCCCCCCCCCCCc......",
        "......cCtCCtCCtCCtCCtCCtCc......",
        "......cCCCCCCCCCCCCCCCCCCc......",
        ".......cddddddddddddddddc.......",
        "........nnnnnnnnnnnnnnnN........",
        "........nnhhnnnnnnnnnnnN........",
        "........nhhnnnnnnnnnnnnN........",
        "........nhhnnnnnnnnnnnnN........",
        "........nhnnnnnnnnnnnnNN........",
        "........nhnnnnnnnnnnnnNN........",
        "........nnnnnnnnnnnnnnNN........",
        ".........nnnnnnnnnnnnNN.........",
        ".........nnnnnnnnnnnnNN.........",
        "..........nnnnnnnnnnNN..........",
        "...........nnnnnnnnNN...........",
        "............nnnnnnNN............",
        ".............nnnnNN.............",
        "..............nnNN..............",
        "...............NN...............",
        "................................",
        "................................",
        "................................",
        "................................",
    )

    private val PALETTE = mapOf(
        '.' to 0x00000000,
        's' to 0xFF4A301A.toInt(), // stem
        'C' to 0xFF8C5C30.toInt(), // cap
        'c' to 0xFF623E20.toInt(), // cap edge
        't' to 0xFF704826.toInt(), // cap scales
        'd' to 0xFF462C16.toInt(), // cap rim shadow
        'n' to 0xFF4CA840.toInt(), // nut
        'N' to 0xFF2C762C.toInt(), // nut shade
        'h' to 0xFFAAE28C.toInt(), // highlight
    )

    private val base: BufferedImage by lazy {
        BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB).apply {
            for ((y, row) in ART.withIndex()) {
                for ((x, char) in row.withIndex()) setRGB(x, y, PALETTE.getValue(char))
            }
        }
    }

    /** The icon at [size] x [size] pixels. */
    fun render(size: Int): BufferedImage {
        val image = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR)
        g.drawImage(base, 0, 0, size, size, null)
        g.dispose()
        return image
    }

    /** Window icons (title bar, Windows taskbar, Linux docks) and the macOS Dock icon. */
    fun install(frame: JFrame) {
        frame.iconImages = listOf(16, 32, 64, 128, 256).map(::render)
        try {
            val taskbar = Taskbar.getTaskbar()
            if (taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) taskbar.iconImage = render(512) as Image
        } catch (_: UnsupportedOperationException) {
            // No taskbar on this platform; window icons are enough.
        } catch (_: SecurityException) {
        }
    }
}
