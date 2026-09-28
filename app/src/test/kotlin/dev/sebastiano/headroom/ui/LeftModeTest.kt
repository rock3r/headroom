package dev.sebastiano.headroom.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertRangeInfoEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import dev.sebastiano.headroom.designsystem.HeadroomTheme
import dev.sebastiano.headroom.designsystem.IndicatorStyle
import dev.sebastiano.headroom.model.AppSettings
import dev.sebastiano.headroom.model.InMemorySettingsRepository
import dev.sebastiano.headroom.model.QuotaDisplay
import dev.sebastiano.headroom.ui.detail.DETAIL_TAG
import dev.sebastiano.headroom.ui.overview.accountCardTag
import dev.sebastiano.headroom.ui.resets.RESETS_TAG
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** With "left" chosen in the settings, every screen shows how much of each limit is left. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class LeftModeTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun launch() {
        val settings = InMemorySettingsRepository(AppSettings(quotaDisplay = QuotaDisplay.Left))
        rule.setContent {
            HeadroomTheme(dynamicColor = false) {
                HeadroomApp(graph = testGraph(rule.activity, settings = settings))
            }
        }
    }

    private fun openClaude() {
        launch()
        rule.onNodeWithTag(accountCardTag("demo-claude")).performClick()
        rule.onNodeWithTag(DETAIL_TAG).assertIsDisplayed()
    }

    @Test
    fun `the overview card leads with what is left and fills its bar by it`() {
        launch()
        rule
            .onNodeWithTag(accountCardTag("demo-claude"))
            .assert(hasText("29%"))
            .assert(hasText("weekly left"))
        rule
            .onNode(
                indicatorsIn(accountCardTag("demo-claude")) and
                    hasIndicatorStyle(IndicatorStyle.Wavy),
                true,
            )
            .assertRangeInfoEquals(ProgressBarRangeInfo(0.29f, 0f..1f))
    }

    @Test
    fun `the detail ring, window list and chart text say what is left`() {
        openClaude()
        rule
            .onNode(indicatorsIn(DETAIL_TAG) and hasIndicatorStyle(IndicatorStyle.Wavy), true)
            .assertRangeInfoEquals(ProgressBarRangeInfo(0.29f, 0f..1f))
        rule
            .onNode(hasText("weekly left") and hasAnyAncestor(hasTestTag(DETAIL_TAG)))
            .assertIsDisplayed()
        rule.onNodeWithContentDescription("29% left").performScrollTo().assertIsDisplayed()
        rule
            .onNodeWithContentDescription("29% left, usage above the even-pace line.")
            .performScrollTo()
            .assertIsDisplayed()
        rule
            .onNodeWithText("At this rate you run out in about 1d 17h, 1d 1h before the reset.")
            .assertExists()
    }

    @Test
    fun `the resets history shows what was left at each reset`() {
        launch()
        rule.onNodeWithContentDescription("Resets").performClick()
        val description =
            "Claude · Weekly · all models: 18%, 5%, 0%, 12%, 0% left at past resets, 29% left now"
        rule.onNodeWithTag(RESETS_TAG).performScrollToNode(hasContentDescription(description))
        rule.onNodeWithContentDescription(description).assertIsDisplayed()
        rule.onNodeWithText("Left when each window reset").assertExists()
    }
}
