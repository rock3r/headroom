package dev.sebastiano.headroom.designsystem

import androidx.compose.ui.graphics.Color
import dev.sebastiano.headroom.model.Provider
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProviderPaletteTest {
    private val cobalt = Color(0xFF305EB7)

    @Test
    fun `every provider has its own hue`() {
        val hues = Provider.entries.map { ProviderPalette.baseHue(it) }
        assertEquals(hues.size, hues.toSet().size)
    }

    @Test
    fun `accent hues move towards the primary colour by at most 15 degrees`() {
        val primaryHue = cobalt.toOklch().hue
        Provider.entries.forEach { provider ->
            val colors = ProviderPalette.colors(provider, primary = cobalt, dark = false)
            val base = ProviderPalette.baseHue(provider)
            val moved = colors.accent.toOklch().hue
            val shift = angleBetween(base, moved)
            // 8-bit channels shift the measured hue by up to about one degree.
            assertTrue(shift <= 16.5f, "$provider moved $shift degrees")
            assertTrue(
                angleBetween(moved, primaryHue) <= angleBetween(base, primaryHue) + 0.5f,
                "$provider moved away from the primary hue",
            )
        }
    }

    @Test
    fun `dark theme accents are lighter than light theme accents`() {
        val light = ProviderPalette.colors(Provider.Claude, primary = cobalt, dark = false)
        val dark = ProviderPalette.colors(Provider.Claude, primary = cobalt, dark = true)
        assertTrue(dark.accent.toOklch().lightness > light.accent.toOklch().lightness)
        assertTrue(dark.container.toOklch().lightness < light.container.toOklch().lightness)
    }

    private fun angleBetween(a: Float, b: Float): Float {
        val d = abs(a - b) % 360f
        return if (d > 180f) 360f - d else d
    }
}
