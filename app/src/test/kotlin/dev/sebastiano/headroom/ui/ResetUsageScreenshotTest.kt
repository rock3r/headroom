package dev.sebastiano.headroom.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.github.takahirom.roborazzi.captureRoboImage
import dev.sebastiano.headroom.designsystem.HeadroomTheme
import dev.sebastiano.headroom.ui.stats.STATS_TAG
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The "Usage limit resets" card of the Stats tab, with the demo history, for the last 4 weeks and
 * the last 12 months. Recorded with `./gradlew :app:recordRoborazziDebug` into
 * `app/build/outputs/roborazzi/prototypes`. Recordings for review, not assertions.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PROTOTYPE_PHONE)
class ResetUsageScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun card(period: String?, name: String) {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            HeadroomTheme(dynamicColor = false) { HeadroomApp(graph = testGraph(rule.activity)) }
        }
        rule.mainClock.autoAdvance = true
        rule.onNodeWithContentDescription("Stats").performClick()
        rule.onNodeWithTag(STATS_TAG).performScrollToNode(hasText("Usage limit resets"))
        period?.let { rule.onNodeWithText(it).performClick() }
        rule.onNodeWithTag(STATS_TAG).performScrollToNode(hasText("A reset counts as used", true))
        rule.mainClock.autoAdvance = false
        repeat(PROTOTYPE_SETTLE_STEPS) {
            rule.mainClock.advanceTimeBy(PROTOTYPE_STEP_MILLIS)
            rule.onRoot().drawFrame()
        }
        rule.onRoot().captureRoboImage(prototypeScreenshot(name))
    }

    @Test fun fourWeeks() = card(period = null, name = "stats-reset-usage-4-weeks")

    @Test fun twelveMonths() = card(period = "12 months", name = "stats-reset-usage-12-months")
}
