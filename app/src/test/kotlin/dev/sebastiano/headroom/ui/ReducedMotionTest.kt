package dev.sebastiano.headroom.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.ComposeUiTestConfig
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertRangeInfoEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.sebastiano.headroom.designsystem.HeadroomTheme
import dev.sebastiano.headroom.designsystem.IndicatorStyle
import dev.sebastiano.headroom.model.DemoData
import dev.sebastiano.headroom.ui.accounts.ACCOUNT_NAME_FIELD_TAG
import dev.sebastiano.headroom.ui.accounts.accountRowTag
import dev.sebastiano.headroom.ui.detail.DETAIL_TAG
import dev.sebastiano.headroom.ui.overview.accountCardTag
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * With animations off (animator duration scale 0), every screen must still be complete: bars at
 * their values, flat, and the pace chip still saying which accounts need attention.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class ReducedMotionTest {
    @get:Rule
    val rule =
        createAndroidComposeRule<ComponentActivity>(ComposeUiTestConfig(effectContext = ZeroMotion))

    @Test
    fun `with animations off the overview is static and still carries the attention signal`() {
        rule.setContent {
            HeadroomTheme(dynamicColor = false) { HeadroomApp(graph = testGraph(rule.activity)) }
        }
        rule
            .onAllNodes(indicatorsIn(accountCardTag("demo-claude")), useUnmergedTree = true)[0]
            .assert(hasIndicatorStyle(IndicatorStyle.Flat))
            .assertRangeInfoEquals(ProgressBarRangeInfo(0.71f, 0f..1f))
        rule.onNodeWithText("11 pts over pace", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `with animations off the detail ring is at its value at once`() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            HeadroomTheme(dynamicColor = false) { HeadroomApp(graph = testGraph(rule.activity)) }
        }
        rule.mainClock.advanceTimeByFrame()
        rule.onNodeWithTag(accountCardTag("demo-claude")).performClick()
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeByFrame()
        rule
            .onAllNodes(indicatorsIn(DETAIL_TAG), useUnmergedTree = true)[0]
            .assert(hasIndicatorStyle(IndicatorStyle.Flat))
            .assertRangeInfoEquals(ProgressBarRangeInfo(0.71f, 0f..1f))
    }

    @Test
    fun `with animations off an account opens for editing at once`() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            HeadroomTheme(dynamicColor = false) {
                HeadroomApp(
                    graph = testGraph(rule.activity, realAccounts = DemoData.accounts(FIXED_NOW))
                )
            }
        }
        rule.mainClock.advanceTimeByFrame()
        rule.openAccounts()
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeByFrame()
        rule.onNodeWithTag(accountRowTag("demo-codex")).performClick()
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeByFrame()
        rule.onNodeWithTag(ACCOUNT_NAME_FIELD_TAG).assertIsDisplayed()
        rule.onNodeWithText("Save").assertIsDisplayed()
        rule.onNodeWithTag(accountRowTag("demo-claude")).assertIsNotEnabled()
    }
}
