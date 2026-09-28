package dev.sebastiano.headroom.ui

import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import dev.sebastiano.headroom.designsystem.HeadroomTheme
import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.ui.accounts.ACCOUNTS_TAG
import dev.sebastiano.headroom.ui.accounts.providerOptionTag
import dev.sebastiano.headroom.ui.components.STATUS_BAR_BLUR_TAG
import dev.sebastiano.headroom.ui.detail.DETAIL_TAG
import dev.sebastiano.headroom.ui.overview.OVERVIEW_LIST_TAG
import dev.sebastiano.headroom.ui.overview.accountCardTag
import dev.sebastiano.headroom.ui.resets.RESETS_TAG
import dev.sebastiano.headroom.ui.settings.SETTINGS_TAG
import dev.sebastiano.headroom.ui.stats.STATS_TAG
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The frosted blur behind the status bar shows only while content scrolls under the status bar, and
 * never where no status bar covers the content.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h600dp")
class StatusBarBlurTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val blur
        get() = rule.onAllNodesWithTag(STATUS_BAR_BLUR_TAG)

    private val accountsList
        get() = rule.onNode(hasScrollToIndexAction() and hasAnyAncestor(hasTestTag(ACCOUNTS_TAG)))

    private fun launch() {
        rule.setContent {
            HeadroomTheme(dynamicColor = false) { HeadroomApp(graph = testGraph(rule.activity)) }
        }
        rule.runOnUiThread { rule.activity.giveStatusBar(STATUS_BAR_PX) }
    }

    @Test
    fun `the overview blurs the status bar only once scrolled`() {
        launch()
        blur.assertCountEquals(0)
        rule
            .onNodeWithTag(OVERVIEW_LIST_TAG)
            .performScrollToNode(hasTestTag(accountCardTag("demo-grok")))
        blur.assertCountEquals(1)
        rule.onNodeWithTag(OVERVIEW_LIST_TAG).performScrollToIndex(0)
        blur.assertCountEquals(0)
    }

    @Test
    fun `an account opens from a scrolled overview and back returns to it`() {
        launch()
        rule
            .onNodeWithTag(OVERVIEW_LIST_TAG)
            .performScrollToNode(hasTestTag(accountCardTag("demo-grok")))
        blur.assertCountEquals(1)
        rule.onNodeWithTag(accountCardTag("demo-grok")).performClick()
        rule.onNodeWithTag(DETAIL_TAG).assertIsDisplayed()
        blur.assertCountEquals(0)

        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.onNodeWithTag(OVERVIEW_LIST_TAG).assertIsDisplayed()
        rule.onNodeWithTag(DETAIL_TAG).assertDoesNotExist()
    }

    @Test
    fun `the detail blurs the status bar only once scrolled`() {
        launch()
        rule.onNodeWithTag(accountCardTag("demo-claude")).performClick()
        rule.onNodeWithTag(DETAIL_TAG).assertExists()
        blur.assertCountEquals(0)
        rule.onNodeWithText("Session limits never send alerts.").performScrollTo()
        blur.assertCountEquals(1)
    }

    @Test
    fun `the resets and stats tabs blur the status bar once scrolled`() {
        launch()
        rule.onNodeWithContentDescription("Resets").performClick()
        blur.assertCountEquals(0)
        rule.onNodeWithTag(RESETS_TAG).performScrollToIndex(LAST_RESETS_INDEX)
        blur.assertCountEquals(1)

        rule.onNodeWithContentDescription("Stats").performClick()
        rule.waitForIdle()
        blur.assertCountEquals(0)
        rule.onNodeWithTag(STATS_TAG).performScrollToIndex(LAST_STATS_INDEX)
        blur.assertCountEquals(1)
    }

    @Test
    fun `the accounts list and the provider picker blur the status bar once scrolled`() {
        launch()
        rule.openAccounts()
        blur.assertCountEquals(0)
        accountsList.performScrollToNode(hasText("Add account"))
        blur.assertCountEquals(1)

        rule.onNodeWithText("Add account").performClick()
        rule.waitForIdle()
        blur.assertCountEquals(0)
        accountsList.performScrollToNode(hasTestTag(providerOptionTag(Provider.entries.last())))
        blur.assertCountEquals(1)
    }

    @Test
    fun `the predictive back gesture works over a scrolled accounts list`() {
        launch()
        rule.openAccounts()
        accountsList.performScrollToNode(hasText("Add account"))
        blur.assertCountEquals(1)

        val dispatcher = rule.activity.onBackPressedDispatcher
        rule.runOnUiThread {
            dispatcher.dispatchOnBackStarted(backEvent(0f))
            dispatcher.dispatchOnBackProgressed(backEvent(0.5f))
        }
        rule.waitForIdle()
        rule.onNodeWithTag(SETTINGS_TAG).assertExists()
        rule.runOnUiThread { dispatcher.onBackPressed() }
        rule.onNodeWithTag(ACCOUNTS_TAG).assertDoesNotExist()
        rule.onNodeWithTag(SETTINGS_TAG).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w1280dp-h500dp")
    fun `the expanded detail pane sits below the status bar and never blurs it`() {
        launch()
        rule.onNodeWithTag(accountCardTag("demo-claude")).performClick()
        rule.onNodeWithText("Session limits never send alerts.").performScrollTo()
        blur.assertCountEquals(0)
    }
}

private const val STATUS_BAR_PX = 72

private fun backEvent(progress: Float) =
    BackEventCompat(0f, 0f, progress, BackEventCompat.EDGE_LEFT)

/** Header, upcoming label, upcoming card, history label, history card. */
private const val LAST_RESETS_INDEX = 4
/** The header and six stat cards. */
private const val LAST_STATS_INDEX = 6
