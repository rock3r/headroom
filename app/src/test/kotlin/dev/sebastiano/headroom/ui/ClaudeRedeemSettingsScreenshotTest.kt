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
import dev.sebastiano.headroom.ui.settings.REDEEM_CLAUDE_RESETS_TAG
import dev.sebastiano.headroom.ui.settings.SETTINGS_LIST_TAG
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The "Redeem Claude resets (experimental)" switch in Settings, off and on, recorded with
 * `./gradlew :app:recordRoborazziDebug` into `app/build/outputs/roborazzi/prototypes`. Recordings
 * for review, not assertions.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PROTOTYPE_PHONE)
class ClaudeRedeemSettingsScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun row(on: Boolean, name: String) {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            HeadroomTheme(dynamicColor = false) {
                HeadroomApp(
                    graph =
                        testGraph(
                            rule.activity,
                            settings =
                                InMemorySettingsRepository(AppSettings(redeemClaudeResets = on)),
                        )
                )
            }
        }
        rule.mainClock.autoAdvance = true
        rule.onNodeWithContentDescription("Settings").performClick()
        rule
            .onNodeWithTag(SETTINGS_LIST_TAG)
            .performScrollToNode(hasTestTag(REDEEM_CLAUDE_RESETS_TAG))
        rule.mainClock.autoAdvance = false
        repeat(PROTOTYPE_SETTLE_STEPS) {
            rule.mainClock.advanceTimeBy(PROTOTYPE_STEP_MILLIS)
            rule.onRoot().drawFrame()
        }
        rule.onRoot().captureRoboImage(prototypeScreenshot(name))
    }

    @Test fun off() = row(on = false, name = "settings-redeem-claude-off")

    @Test fun on() = row(on = true, name = "settings-redeem-claude-on")
}
