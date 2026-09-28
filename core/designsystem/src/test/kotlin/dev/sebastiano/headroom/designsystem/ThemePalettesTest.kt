package dev.sebastiano.headroom.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.ThemePalette
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ThemePalettesTest {
    private val fixed = ThemePalette.entries.filter { it != ThemePalette.Wallpaper }

    @Test
    fun `the wallpaper has no seed and every fixed palette has its own`() {
        assertNull(ThemePalette.Wallpaper.seed)
        val seeds = fixed.map { assertNotNull(it.seed) }
        assertEquals(seeds.size, seeds.toSet().size)
    }

    @Test
    fun `the seeds are spread round the colour wheel`() {
        val hues = fixed.map { assertNotNull(it.seed).toOklch().hue }.sorted()
        val gaps = hues.zipWithNext { a, b -> b - a } + (hues.first() + 360f - hues.last())
        assertTrue(gaps.all { it >= MIN_HUE_GAP }, "hue gaps: $gaps")
    }

    @Test
    fun `a fixed scheme keeps the hue of its seed`() {
        fixed.forEach { palette ->
            val seedHue = assertNotNull(palette.seed).toOklch().hue
            listOf(false, true).forEach { dark ->
                val primary = seededColorScheme(palette, dark).primary
                val shift = angleBetween(primary.toOklch().hue, seedHue)
                assertTrue(shift <= MAX_PRIMARY_HUE_SHIFT, "$palette dark=$dark shifted $shift")
            }
        }
    }

    @Test
    fun `light schemes are light and dark schemes are dark`() {
        fixed.forEach { palette ->
            assertTrue(seededColorScheme(palette, dark = false).surface.luminance() > LIGHT_SURFACE)
            assertTrue(seededColorScheme(palette, dark = true).surface.luminance() < DARK_SURFACE)
        }
    }

    @Test
    fun `text on the main roles is readable`() {
        fixed.forEach { palette ->
            listOf(false, true).forEach { dark ->
                val scheme = seededColorScheme(palette, dark)
                assertTrue(contrast(scheme.primary, scheme.onPrimary) >= MIN_CONTRAST)
                assertTrue(contrast(scheme.surface, scheme.onSurface) >= MIN_CONTRAST)
                assertTrue(
                    contrast(scheme.primaryContainer, scheme.onPrimaryContainer) >= MIN_CONTRAST
                )
            }
        }
    }

    @Test
    fun `provider colours are harmonised with a fixed scheme's primary`() {
        val lagoon = seededColorScheme(ThemePalette.Lagoon, dark = false).primary
        val coral = seededColorScheme(ThemePalette.Coral, dark = false).primary
        assertNotEquals(
            ProviderPalette.colors(Provider.Grok, lagoon, dark = false).accent,
            ProviderPalette.colors(Provider.Grok, coral, dark = false).accent,
        )
    }

    private fun contrast(a: Color, b: Color): Float {
        val la = a.luminance() + LUMINANCE_OFFSET
        val lb = b.luminance() + LUMINANCE_OFFSET
        return max(la, lb) / min(la, lb)
    }

    private fun angleBetween(a: Float, b: Float): Float {
        val d = abs(a - b) % FULL_TURN
        return if (d > HALF_TURN) FULL_TURN - d else d
    }

    private companion object {
        const val MIN_HUE_GAP = 20f
        const val MAX_PRIMARY_HUE_SHIFT = 25f
        const val LIGHT_SURFACE = 0.8f
        const val DARK_SURFACE = 0.05f
        const val MIN_CONTRAST = 4.5f
        const val LUMINANCE_OFFSET = 0.05f
        const val FULL_TURN = 360f
        const val HALF_TURN = 180f
    }
}
