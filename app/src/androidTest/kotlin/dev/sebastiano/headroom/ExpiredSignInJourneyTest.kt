package dev.sebastiano.headroom

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sebastiano.headroom.model.DemoData
import dev.sebastiano.headroom.ui.detail.DETAIL_TAG
import dev.sebastiano.headroom.ui.overview.OVERVIEW_LIST_TAG
import dev.sebastiano.headroom.ui.overview.accountCardTag
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * An expired sign-in on a real device, in demo mode with a fixed clock: the card asks to sign in,
 * its button opens the provider's sign-in for that account, cancelling returns to the overview, and
 * the detail shows its banner.
 */
@RunWith(AndroidJUnit4::class)
class ExpiredSignInJourneyTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private val claudeCard = accountCardTag("demo-claude")

    /** The app outlives this test: put the demo accounts back for the next one. */
    @After
    fun restoreDemoAccounts() {
        val app = rule.activity.application as HeadroomApplication
        rule.runOnUiThread {
            app.graph.demoAccounts?.set(DemoData.accounts(E2eApplication.FIXED_NOW))
        }
    }

    @Test
    fun cardSignInCancelAndDetailBanner() {
        val app = rule.activity.application as HeadroomApplication
        rule.runOnUiThread {
            app.graph.demoAccounts?.set(
                DemoData.accountsWithExpiredSignIn(E2eApplication.FIXED_NOW)
            )
        }

        // The card says the sign-in expired and when its numbers are from.
        rule.waitUntil(TIMEOUT) {
            exists(hasContentDescription("Sign-in expired, data from 2 hours ago"))
        }

        // Its button opens the sign-in for Claude, straight away.
        rule.onNode(hasAnyAncestor(hasTestTag(claudeCard)) and hasText("Sign in")).performClick()
        rule.onNodeWithText("Sign in to Claude again").assertIsDisplayed()
        rule.onNodeWithText("Sign in as sam@example.com", substring = true).assertIsDisplayed()

        // Cancelling returns to the overview.
        rule.onNodeWithContentDescription("Cancel sign-in").performClick()
        rule.waitUntil(TIMEOUT) { exists(hasTestTag(OVERVIEW_LIST_TAG)) }

        // The detail starts with a banner that signs in again.
        rule.onNodeWithTag(claudeCard).performClick()
        rule.onNodeWithTag(DETAIL_TAG).assertIsDisplayed()
        rule.onNodeWithText("Your Claude sign-in expired").assertIsDisplayed()
        rule.onNodeWithText("Sign in again").performClick()
        rule.onNodeWithText("Sign in to Claude again").assertIsDisplayed()
    }

    private fun exists(matcher: androidx.compose.ui.test.SemanticsMatcher): Boolean =
        rule.onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private companion object {
        const val TIMEOUT = 5_000L
    }
}
