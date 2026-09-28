package dev.sebastiano.headroom.widget.render

import dev.sebastiano.headroom.designsystem.seededColorScheme
import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.ThemePalette
import dev.sebastiano.headroom.widget.ColourMode
import kotlin.test.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Widgets take their colours from the palette the user picked in the app. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WidgetPaletteTest {
    private val context = RuntimeEnvironment.getApplication()

    @Test
    fun `a fixed palette gives the widgets its colours`() {
        val colors =
            WidgetColors.forPalette(context, ThemePalette.Lagoon, ColourMode.Wallpaper, false)
        val scheme = seededColorScheme(ThemePalette.Lagoon, dark = false)

        assertEquals(scheme.primary, colors.accent(Provider.Claude))
        assertEquals(scheme.onSurface, colors.onSurface)
    }

    @Test
    fun `lock screen widgets use the dark version of the palette`() {
        val colors =
            WidgetColors.forPalette(context, ThemePalette.Coral, ColourMode.Wallpaper, true)

        assertEquals(
            seededColorScheme(ThemePalette.Coral, dark = true).primary,
            colors.accent(Provider.Claude),
        )
    }
}
