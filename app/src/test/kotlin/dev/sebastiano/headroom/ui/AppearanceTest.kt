package dev.sebastiano.headroom.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.core.view.WindowCompat
import dev.sebastiano.headroom.designsystem.IndicatorStyle
import dev.sebastiano.headroom.designsystem.headroomColorScheme
import dev.sebastiano.headroom.designsystem.seededColorScheme
import dev.sebastiano.headroom.model.InMemorySettingsRepository
import dev.sebastiano.headroom.model.MotionPreference
import dev.sebastiano.headroom.model.ThemeMode
import dev.sebastiano.headroom.model.ThemePalette
import dev.sebastiano.headroom.ui.overview.accountCardTag
import dev.sebastiano.headroom.ui.settings.REDUCE_MOTION_TAG
import dev.sebastiano.headroom.ui.settings.SETTINGS_LIST_TAG
import dev.sebastiano.headroom.ui.settings.SETTINGS_TAG
import dev.sebastiano.headroom.ui.settings.paletteTag
import dev.sebastiano.headroom.ui.settings.themeModeTag
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The appearance settings apply to the whole app as soon as they are chosen. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp")
class AppearanceTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val settings = InMemorySettingsRepository()

    private fun launch() {
        rule.setContent {
            val current by settings.settings.collectAsState()
            AppearanceTheme(current, dynamicColor = false) {
                HeadroomApp(graph = testGraph(rule.activity, settings = settings))
            }
        }
    }

    private fun openSettingsAt(tag: String) {
        rule.onNodeWithContentDescription("Settings").performClick()
        rule.onNodeWithTag(SETTINGS_LIST_TAG).performScrollToNode(hasTestTag(tag))
    }

    /** The colour of the settings page's own surface, at its left edge beside the cards. */
    private fun settingsSurface(): Color {
        val pixels = rule.onNodeWithTag(SETTINGS_TAG).captureToImage().toPixelMap()
        return pixels[EDGE_PX, pixels.height / 2]
    }

    @Test
    fun `choosing dark turns the app dark at once, status bar icons included`() {
        launch()
        openSettingsAt(themeModeTag(ThemeMode.Dark))
        rule.onNodeWithTag(themeModeTag(ThemeMode.System)).assertIsSelected()

        rule.onNodeWithTag(themeModeTag(ThemeMode.Dark)).performClick()

        rule.onNodeWithTag(themeModeTag(ThemeMode.Dark)).assertIsSelected()
        assertEquals(ThemeMode.Dark, settings.settings.value.theme)
        val dark = headroomColorScheme(rule.activity, ThemePalette.Wallpaper, dark = true, false)
        assertClose(dark.surface, settingsSurface())
        val window = rule.activity.window
        assertFalse(
            WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars
        )
    }

    @Test
    fun `choosing a fixed palette recolours the app`() {
        launch()
        openSettingsAt(paletteTag(ThemePalette.Lagoon))
        rule.onNodeWithTag(paletteTag(ThemePalette.Wallpaper)).assertIsSelected()

        rule.onNodeWithTag(paletteTag(ThemePalette.Lagoon)).performClick()

        rule.onNodeWithTag(paletteTag(ThemePalette.Lagoon)).assertIsSelected()
        assertEquals(ThemePalette.Lagoon, settings.settings.value.palette)
        assertClose(seededColorScheme(ThemePalette.Lagoon, dark = false).surface, settingsSurface())
    }

    @Test
    fun `reducing motion stops the wave on the bars`() {
        launch()
        val claudeBar = indicatorsIn(accountCardTag("demo-claude"))
        rule
            .onAllNodes(claudeBar, useUnmergedTree = true)[0]
            .assert(hasIndicatorStyle(IndicatorStyle.Wavy))

        openSettingsAt(REDUCE_MOTION_TAG)
        rule.onNodeWithTag(REDUCE_MOTION_TAG).performClick()
        assertEquals(MotionPreference.Reduced, settings.settings.value.motion)
        rule.onNodeWithContentDescription("Close settings").performClick()

        rule
            .onAllNodes(claudeBar, useUnmergedTree = true)[0]
            .assert(hasIndicatorStyle(IndicatorStyle.Flat))
    }

    private fun assertClose(expected: Color, actual: Color) {
        val channels =
            listOf(
                expected.red to actual.red,
                expected.green to actual.green,
                expected.blue to actual.blue,
            )
        assertTrue(
            channels.all { (e, a) -> abs(e - a) <= COLOUR_TOLERANCE },
            "expected $expected, got $actual",
        )
    }

    private companion object {
        const val EDGE_PX = 4
        /** About two steps of an 8-bit channel. */
        const val COLOUR_TOLERANCE = 2f / 255f
    }
}
