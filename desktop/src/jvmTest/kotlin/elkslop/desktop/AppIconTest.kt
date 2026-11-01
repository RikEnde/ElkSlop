// SPDX-FileCopyrightText: 2026 The Elkslop contributors
// SPDX-License-Identifier: BSD-3-Clause

package elkslop.desktop

import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals

class AppIconTest {
    @Test
    fun rendersCrispAcornAtAnySize() {
        val icon = AppIcon.render(512)
        assertEquals(512, icon.width)
        assertEquals(0, icon.getRGB(0, 0) ushr 24, "corners are transparent")
        // Grid cell (16, 9) is cap; (16, 20) is nut. Each cell is 16 x 16 pixels here.
        assertEquals(0xFF8C5C30.toInt(), icon.getRGB(16 * 16 + 8, 9 * 16 + 8), "brown cap")
        assertEquals(0xFF4CA840.toInt(), icon.getRGB(16 * 16 + 8, 20 * 16 + 8), "green nut")
        File("build/icon").mkdirs()
        ImageIO.write(icon, "png", File("build/icon/elkslop-512.png"))
    }
}
