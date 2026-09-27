package dev.sebastiano.headroom.widget.render

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.widget.ColourMode
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.junit.Test

class WidgetColorsTest {
    private val light = lightColorScheme()
    private val dark = darkColorScheme()

    @Test
    fun `oklch converts to srgb`() {
        assertClose(Color.White, oklch(1f, 0f, 0f))
        assertClose(Color.Black, oklch(0f, 0f, 0f))
        assertClose(Color.Red, oklch(0.62796f, 0.25768f, 29.2339f))
        assertClose(Color(0xFF0000FF), oklch(0.45201f, 0.31321f, 264.052f))
    }

    @Test
    fun `wallpaper mode draws with the dynamic palette`() {
        val colors = WidgetColors.from(light, ColourMode.Wallpaper, isDark = false)

        assertEquals(light.primary, colors.accent(Provider.Claude))
        assertEquals(light.tertiary, colors.secondaryAccent(Provider.Claude))
        assertEquals(light.primaryContainer, colors.shapeFill(Provider.Codex))
        assertEquals(light.onPrimaryContainer, colors.onShapeFill)
        assertEquals(light.surfaceContainerHighest, colors.track)
    }

    @Test
    fun `per account mode gives each provider its own hue`() {
        val colors = WidgetColors.from(light, ColourMode.PerAccount, isDark = false)

        assertNotEquals(colors.accent(Provider.Claude), colors.accent(Provider.Codex))
        assertEquals(colors.accent(Provider.Claude), colors.secondaryAccent(Provider.Claude))
        assertClose(oklch(0.6f, 0.14f, 45f), colors.accent(Provider.Claude))
        assertClose(oklch(0.86f, 0.07f, 155f), colors.shapeFill(Provider.Codex))
    }

    @Test
    fun `per account hues get lighter in the dark theme`() {
        val colors = WidgetColors.from(dark, ColourMode.PerAccount, isDark = true)

        assertClose(oklch(0.8f, 0.1f, 45f), colors.accent(Provider.Claude))
        assertClose(oklch(0.42f, 0.07f, 45f), colors.shapeFill(Provider.Claude))
    }

    @Test
    fun `mono mode uses only the on surface colour`() {
        val colors = WidgetColors.from(light, ColourMode.Mono, isDark = false)

        assertEquals(light.onSurface, colors.accent(Provider.Grok))
        assertEquals(light.onSurface.copy(alpha = 0.55f), colors.secondaryAccent(Provider.Grok))
        assertEquals(light.surfaceContainerHighest, colors.shapeFill(Provider.Grok))
    }

    @Test
    fun `avatars always use the provider hue`() {
        val mono = WidgetColors.from(light, ColourMode.Mono, isDark = false)

        assertClose(oklch(0.6f, 0.14f, 230f), mono.avatar(Provider.Grok))
    }

    private fun assertClose(expected: Color, actual: Color) {
        val tolerance = 0.01f
        assertTrue(
            abs(expected.red - actual.red) < tolerance &&
                abs(expected.green - actual.green) < tolerance &&
                abs(expected.blue - actual.blue) < tolerance,
            "expected $expected but was $actual",
        )
    }
}
