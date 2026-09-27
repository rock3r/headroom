package dev.sebastiano.headroom.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertRangeInfoEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import dev.sebastiano.headroom.designsystem.HeadroomTheme
import dev.sebastiano.headroom.designsystem.IndicatorStyle
import dev.sebastiano.headroom.ui.detail.DETAIL_TAG
import dev.sebastiano.headroom.ui.detail.alertSwitchTag
import dev.sebastiano.headroom.ui.overview.accountCardTag
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class DetailTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun openClaude() {
        rule.setContent {
            HeadroomTheme(dynamicColor = false) { HeadroomApp(graph = testGraph(rule.activity)) }
        }
        rule.onNodeWithTag(accountCardTag("demo-claude")).performClick()
        rule.onNodeWithTag(DETAIL_TAG).assertIsDisplayed()
    }

    @Test
    fun `the hero ring shows the weekly window and is wavy because Claude is over pace`() {
        openClaude()
        rule
            .onNode(indicatorsIn(DETAIL_TAG) and hasIndicatorStyle(IndicatorStyle.Wavy), true)
            .assertRangeInfoEquals(ProgressBarRangeInfo(0.71f, 0f..1f))
    }

    @Test
    fun `the chart describes usage against even pace and projects the limit`() {
        openClaude()
        rule
            .onNodeWithContentDescription("above the even-pace line", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
        rule
            .onNodeWithText("At this rate you reach 100% in about 1d 17h, 1d 1h before the reset.")
            .assertExists()
    }

    @Test
    fun `weekly alerts are on by default and can be switched off`() {
        openClaude()
        val weekly = rule.onNodeWithTag(alertSwitchTag("demo-claude", "seven_day"))
        weekly.performScrollTo().assertIsOn().performClick()
        rule.onNodeWithTag(alertSwitchTag("demo-claude", "seven_day")).assertIsOff()
        rule.onNodeWithTag(alertSwitchTag("demo-claude", "seven_day_opus")).assertIsOn()
    }

    @Test
    fun `session windows say they never alert`() {
        openClaude()
        rule
            .onNodeWithText("Session limits never send alerts.")
            .performScrollTo()
            .assertIsDisplayed()
        rule.onNodeWithTag(alertSwitchTag("demo-claude", "five_hour")).assertDoesNotExist()
    }

    @Test
    fun `back returns to the overview`() {
        openClaude()
        rule.onNodeWithContentDescription("Back").performClick()
        rule.onNodeWithTag(DETAIL_TAG).assertDoesNotExist()
        rule.onNodeWithTag(accountCardTag("demo-claude")).assertIsDisplayed()
    }

    @Test
    fun `a monthly window is off by default`() {
        rule.setContent {
            HeadroomTheme(dynamicColor = false) { HeadroomApp(graph = testGraph(rule.activity)) }
        }
        rule
            .onNodeWithTag(dev.sebastiano.headroom.ui.overview.OVERVIEW_LIST_TAG)
            .performScrollToNode(hasTestTag(accountCardTag("demo-copilot")))
        rule.onNodeWithTag(accountCardTag("demo-copilot")).performClick()
        rule
            .onNodeWithTag(alertSwitchTag("demo-copilot", "premium_interactions"))
            .performScrollTo()
            .assertIsOff()
        rule.onNodeWithText("Monthly windows are off by default").assertExists()
    }
}
