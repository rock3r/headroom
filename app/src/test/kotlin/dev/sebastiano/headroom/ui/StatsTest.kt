package dev.sebastiano.headroom.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import dev.sebastiano.headroom.designsystem.HeadroomTheme
import dev.sebastiano.headroom.model.AccountState
import dev.sebastiano.headroom.model.DemoData
import dev.sebastiano.headroom.ui.stats.STATS_TAG
import dev.sebastiano.headroom.ui.stats.Stats
import dev.sebastiano.headroom.ui.stats.StatsScreen
import dev.sebastiano.headroom.ui.stats.StatsUiState
import java.time.ZoneOffset
import java.util.Locale
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class StatsTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val formatter = ResetFormatter(ZoneOffset.UTC, Locale.UK, is24Hour = true)

    private fun openStats(realAccounts: List<AccountState> = emptyList()) {
        rule.setContent {
            HeadroomTheme(dynamicColor = false) {
                HeadroomApp(graph = testGraph(rule.activity, realAccounts = realAccounts))
            }
        }
        rule.onNodeWithContentDescription("Stats").performClick()
        rule.onNodeWithTag(STATS_TAG).assertIsDisplayed()
    }

    private fun scrollTo(text: String) {
        rule.onNodeWithTag(STATS_TAG).performScrollToNode(hasText(text, substring = true))
        rule.onNodeWithText(text, substring = true).assertIsDisplayed()
    }

    private fun scrollToDescription(description: String) {
        rule
            .onNodeWithTag(STATS_TAG)
            .performScrollToNode(hasContentDescription(description, substring = true))
        rule.onNodeWithContentDescription(description, substring = true).assertIsDisplayed()
    }

    @Test
    fun `demo mode shows demo stats, labelled as demo data`() {
        openStats()
        rule.onNodeWithText("Example stats from demo data", substring = true).assertIsDisplayed()
        scrollToDescription("of 17 resets came without hitting the limit")
        scrollToDescription("Share of the quota burned: ")
        scrollToDescription("Quota use by day and hour")
        scrollTo("Grok reached 99%")
        scrollToDescription("was left unused at reset")
        scrollToDescription("Claude over the last 7 days")
    }

    @Test
    fun `with only this window's history, the reset stats wait for a reset`() {
        openStats(realAccounts = DemoData.accounts(FIXED_NOW))
        scrollTo("No resets recorded yet")
        // The shares only need a few syncs, and this week has them.
        scrollToDescription("Share of the quota burned")
        scrollTo("No resets yet, so nothing was left on the table")
    }

    @Test
    fun `every stat has an honest empty state`() {
        rule.setContent {
            HeadroomTheme(dynamicColor = false) {
                StatsScreen(StatsUiState(loading = false, stats = Stats()), formatter)
            }
        }
        rule.onNodeWithText("No history yet", substring = true).assertIsDisplayed()
        scrollTo("No resets recorded yet")
        scrollTo("Nothing burned yet")
        scrollTo("Needs a week of history")
        scrollTo("Appears once a limit resets")
        scrollTo("No accounts to chart yet")
    }
}
