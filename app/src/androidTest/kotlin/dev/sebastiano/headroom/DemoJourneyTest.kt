package dev.sebastiano.headroom

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sebastiano.headroom.ui.REFRESH_TAG
import dev.sebastiano.headroom.ui.detail.DETAIL_TAG
import dev.sebastiano.headroom.ui.detail.alertSwitchTag
import dev.sebastiano.headroom.ui.overview.DEMO_BANNER_TAG
import dev.sebastiano.headroom.ui.overview.OVERVIEW_LIST_TAG
import dev.sebastiano.headroom.ui.overview.accountCardTag
import dev.sebastiano.headroom.ui.resets.RESETS_TAG
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The main journey on a real device, in demo mode with a fixed clock: the overview, an account's
 * detail and its alert switch, back, the resets tab, and refreshing from the overview.
 */
@RunWith(AndroidJUnit4::class)
class DemoJourneyTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private val accounts = listOf("demo-claude", "demo-codex", "demo-grok", "demo-copilot")

    @Test
    fun overviewDetailAlertResetsAndRefresh() {
        // The overview shows the four demo accounts, labelled as demo data.
        rule.onNodeWithTag(DEMO_BANNER_TAG).assertIsDisplayed()
        accounts.forEach { id ->
            rule
                .onNodeWithTag(OVERVIEW_LIST_TAG)
                .performScrollToNode(hasTestTag(accountCardTag(id)))
            rule.onNodeWithTag(accountCardTag(id)).assertIsDisplayed()
        }

        // Open Claude and switch its weekly alert off.
        rule.onNodeWithTag(accountCardTag("demo-claude")).performClick()
        rule.onNodeWithTag(DETAIL_TAG).assertIsDisplayed()
        val weekly = alertSwitchTag("demo-claude", "seven_day")
        rule.onNodeWithTag(weekly).performScrollTo().assertIsOn().performClick()
        rule.onNodeWithTag(weekly).assertIsOff()

        // Back to the overview.
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitUntil(TIMEOUT) { detailGone() }
        rule.onNodeWithTag(OVERVIEW_LIST_TAG).assertIsDisplayed()

        // The resets tab reflects the switch: three of the five windows alert now.
        rule.onNodeWithContentDescription("Resets").performClick().assertIsSelected()
        rule.onNodeWithTag(RESETS_TAG).assertIsDisplayed()
        rule.onNodeWithText("Alerts are on for 3 of 5 windows").assertIsDisplayed()

        // Back on the overview, pull to refresh: the numbers move on.
        rule.onNodeWithContentDescription("Overview").performClick()
        rule.onNodeWithTag(OVERVIEW_LIST_TAG).performScrollToIndex(0)
        rule.onNodeWithTag(accountCardTag("demo-claude")).assertIsDisplayed()
        waitForClaude("71%")
        rule.onNodeWithTag(OVERVIEW_LIST_TAG).performTouchInput { swipeDown() }
        waitForClaude("72%")

        // And the refresh button does the same.
        rule.onNodeWithTag(REFRESH_TAG).performClick()
        waitForClaude("73%")
    }

    private fun detailGone() =
        rule.onAllNodes(hasTestTag(DETAIL_TAG)).fetchSemanticsNodes().isEmpty()

    private fun claudeShows(text: String) =
        rule
            .onAllNodes(
                hasText(text) and
                    androidx.compose.ui.test.hasAnyAncestor(
                        hasTestTag(accountCardTag("demo-claude"))
                    ),
                useUnmergedTree = true,
            )
            .fetchSemanticsNodes()
            .isNotEmpty()

    private fun waitForClaude(text: String) {
        rule.waitUntil(TIMEOUT) { claudeShows(text) }
    }

    private companion object {
        const val TIMEOUT = 10_000L
    }
}
