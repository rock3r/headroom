package dev.sebastiano.headroom.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import dev.sebastiano.headroom.designsystem.HeadroomTheme
import dev.sebastiano.headroom.designsystem.IndicatorStyle
import dev.sebastiano.headroom.ui.detail.DETAIL_TAG
import dev.sebastiano.headroom.ui.overview.DEMO_BANNER_TAG
import dev.sebastiano.headroom.ui.overview.NEXT_RESET_ALERT_TAG
import dev.sebastiano.headroom.ui.overview.OVERVIEW_LIST_TAG
import dev.sebastiano.headroom.ui.overview.accountCardTag
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class OverviewTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val ids = listOf("demo-claude", "demo-codex", "demo-grok", "demo-copilot")

    private fun launch() {
        rule.setContent {
            HeadroomTheme(dynamicColor = false) { HeadroomApp(graph = testGraph(rule.activity)) }
        }
    }

    private fun scrollTo(tag: String) {
        rule.onNodeWithTag(OVERVIEW_LIST_TAG).performScrollToNode(hasTestTag(tag))
    }

    @Test
    fun `demo mode shows the four demo accounts and says they are demo data`() {
        launch()
        rule.onNodeWithTag(DEMO_BANNER_TAG).assertIsDisplayed()
        rule.onNodeWithText("Demo data").assertIsDisplayed()
        rule.onNodeWithText("4 accounts · synced just now").assertIsDisplayed()
        ids.forEach { id ->
            scrollTo(accountCardTag(id))
            rule.onNodeWithTag(accountCardTag(id)).assertIsDisplayed()
        }
    }

    @Test
    fun `real accounts replace the demo banner`() {
        rule.setContent {
            HeadroomTheme(dynamicColor = false) {
                HeadroomApp(
                    graph =
                        testGraph(
                            rule.activity,
                            realAccounts =
                                dev.sebastiano.headroom.model.DemoData.accounts(FIXED_NOW).take(1),
                        )
                )
            }
        }
        rule.onNodeWithText("1 account · synced just now").assertIsDisplayed()
        rule.onAllNodesWithText("Demo data").assertCountEquals(0)
    }

    @Test
    fun `each card says how it compares with even pace`() {
        launch()
        listOf("11 pts over pace", "7 pts under pace", "On pace", "30 pts under pace")
            .forEachIndexed { index, text ->
                scrollTo(accountCardTag(ids[index]))
                rule.onNodeWithText(text, substring = true, useUnmergedTree = true).assertExists()
            }
    }

    @Test
    fun `only accounts that need attention get a wavy bar`() {
        launch()
        val expected =
            mapOf(
                "demo-claude" to IndicatorStyle.Wavy,
                "demo-codex" to IndicatorStyle.Flat,
                "demo-grok" to IndicatorStyle.Wavy,
                "demo-copilot" to IndicatorStyle.Flat,
            )
        expected.forEach { (id, style) ->
            scrollTo(accountCardTag(id))
            val bars = rule.onAllNodes(indicatorsIn(accountCardTag(id)), useUnmergedTree = true)
            bars[0].assert(hasIndicatorStyle(style))
        }
        // The session bar is always flat, even on an account that needs attention.
        rule
            .onAllNodes(indicatorsIn(accountCardTag("demo-claude")), useUnmergedTree = true)[1]
            .assert(hasIndicatorStyle(IndicatorStyle.Flat))
    }

    @Test
    fun `the hero alert switch toggles the next reset's alert`() {
        launch()
        rule.onNodeWithText("Grok · Mon 28 Sep", substring = true).assertIsDisplayed()
        rule.onNodeWithTag(NEXT_RESET_ALERT_TAG).assertIsOn().performClick()
        rule.onNodeWithTag(NEXT_RESET_ALERT_TAG).assertIsOff()
        rule.onNodeWithText("Alert off").assertIsDisplayed()
    }

    @Test
    fun `tapping a card opens its detail`() {
        launch()
        rule.onNodeWithTag(accountCardTag("demo-claude")).performClick()
        rule.onNodeWithTag(DETAIL_TAG).assertIsDisplayed()
        rule.onNodeWithText("Max 20x · sam@example.com").assertIsDisplayed()
    }
}
