package dev.sebastiano.headroom.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import dev.sebastiano.headroom.designsystem.HeadroomTheme
import dev.sebastiano.headroom.model.InMemorySettingsRepository
import dev.sebastiano.headroom.ui.delights.CONFETTI_OVERLAY_TAG
import dev.sebastiano.headroom.ui.delights.DelightsHost
import dev.sebastiano.headroom.ui.delights.SHIMMER_OVERLAY_TAG
import dev.sebastiano.headroom.ui.settings.REFRESH_SHIMMER_TAG
import dev.sebastiano.headroom.ui.settings.RESET_CONFETTI_TAG
import dev.sebastiano.headroom.ui.settings.SETTINGS_LIST_TAG
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Each delight has a "Try it" button in Settings that plays it once, without changing anything. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class DelightsPreviewTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun openDelights(shimmer: Boolean = true, confetti: Boolean = true) {
        rule.setContent {
            HeadroomTheme(dynamicColor = false) {
                DelightsHost(refreshShimmer = shimmer, resetConfetti = confetti) {
                    HeadroomApp(
                        graph = testGraph(rule.activity, settings = InMemorySettingsRepository())
                    )
                }
            }
        }
        rule.onNodeWithContentDescription("Settings").performClick()
        rule.onNodeWithTag(SETTINGS_LIST_TAG).performScrollToNode(hasTestTag(RESET_CONFETTI_TAG))
    }

    @Test
    fun `trying the shimmer plays it and leaves its switch alone`() {
        openDelights()
        rule.mainClock.autoAdvance = false

        rule.onNodeWithContentDescription("Try the refresh shimmer").performClick()
        rule.mainClock.advanceTimeByFrame()

        rule.onNodeWithTag(SHIMMER_OVERLAY_TAG).assertExists()
        rule.onNodeWithTag(REFRESH_SHIMMER_TAG).assertIsOn()
    }

    @Test
    fun `trying the confetti bursts it and leaves its switch alone`() {
        openDelights()
        rule.mainClock.autoAdvance = false

        rule.onNodeWithContentDescription("Try the reset confetti").performClick()
        rule.mainClock.advanceTimeByFrame()

        rule.onNodeWithTag(CONFETTI_OVERLAY_TAG).assertExists()
        rule.onNodeWithTag(RESET_CONFETTI_TAG).assertIsOn()
    }

    @Test
    fun `a delight that is off cannot be tried`() {
        openDelights(shimmer = false, confetti = false)

        rule.onNodeWithContentDescription("Try the refresh shimmer").assertIsNotEnabled()
        rule.onNodeWithContentDescription("Try the reset confetti").assertIsNotEnabled()
    }
}
