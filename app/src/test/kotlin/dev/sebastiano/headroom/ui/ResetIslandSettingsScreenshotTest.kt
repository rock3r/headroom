package dev.sebastiano.headroom.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.github.takahirom.roborazzi.captureRoboImage
import dev.sebastiano.headroom.designsystem.HeadroomTheme
import dev.sebastiano.headroom.model.AppSettings
import dev.sebastiano.headroom.model.InMemorySettingsRepository
import dev.sebastiano.headroom.ui.settings.RESET_ISLAND_TAG
import dev.sebastiano.headroom.ui.settings.SETTINGS_LIST_TAG
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshots of the reset island's settings row, recorded with `./gradlew
 * :app:recordRoborazziDebug` into `docs/screenshots`. They are recordings, not assertions.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class ResetIslandSettingsScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun row(on: Boolean, ready: Boolean, name: String) {
        val island = FakeResetIslandAccess(ready = ready)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            HeadroomTheme(dynamicColor = false) {
                HeadroomApp(
                    graph =
                        testGraph(
                            rule.activity,
                            settings = InMemorySettingsRepository(AppSettings(resetIsland = on)),
                            resetIsland = island,
                        )
                )
            }
        }
        rule.mainClock.autoAdvance = true
        rule.onNodeWithContentDescription("Settings").performClick()
        rule.onNodeWithTag(SETTINGS_LIST_TAG).performScrollToNode(hasTestTag(RESET_ISLAND_TAG))
        rule.mainClock.autoAdvance = false
        repeat(SETTLE_STEPS) {
            rule.mainClock.advanceTimeBy(STEP_MILLIS)
            rule.onRoot().drawFrame()
        }
        rule.onRoot().captureRoboImage(screenshot(name))
    }

    @Test fun rowReady() = row(on = true, ready = true, name = "settings-reset-island-ready")

    @Test
    fun rowNeedsAccess() =
        row(on = true, ready = false, name = "settings-reset-island-needs-access")

    private companion object {
        const val SETTLE_STEPS = 12
        const val STEP_MILLIS = 100L
    }
}

private fun screenshot(name: String): String {
    val dir = System.getProperty("roborazzi.output.dir") ?: "build/outputs/roborazzi"
    return "$dir/$name.png"
}
