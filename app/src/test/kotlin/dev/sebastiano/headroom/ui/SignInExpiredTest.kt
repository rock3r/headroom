package dev.sebastiano.headroom.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.sebastiano.headroom.designsystem.HeadroomTheme
import dev.sebastiano.headroom.designsystem.IndicatorStyle
import dev.sebastiano.headroom.designsystem.PaceChipState
import dev.sebastiano.headroom.designsystem.StaleKey
import dev.sebastiano.headroom.model.DemoData
import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.signin.FakeSignInController
import dev.sebastiano.headroom.signin.SignInState
import dev.sebastiano.headroom.ui.accounts.ACCOUNTS_TAG
import dev.sebastiano.headroom.ui.components.SignInExpiredRow
import dev.sebastiano.headroom.ui.detail.DETAIL_TAG
import dev.sebastiano.headroom.ui.home.toSummary
import dev.sebastiano.headroom.ui.overview.OVERVIEW_LIST_TAG
import dev.sebastiano.headroom.ui.overview.accountCardTag
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class SignInExpiredTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val accounts = DemoData.accountsWithExpiredSignIn(FIXED_NOW)
    private val claudeCard = accountCardTag("demo-claude")
    private val signIn = FakeSignInController()

    private fun launch() {
        rule.setContent {
            HeadroomTheme(dynamicColor = false) {
                HeadroomApp(
                    graph =
                        testGraph(rule.activity, realAccounts = accounts, signInController = signIn)
                )
            }
        }
    }

    private fun inCard(tag: String) = hasAnyAncestor(hasTestTag(tag))

    @Test
    fun `an expired account never needs attention, and its pace is from its last good sync`() {
        val stale = accounts.first()
        val summary = stale.toSummary(FIXED_NOW, emptyMap(), emptyMap())
        val syncedAt = FIXED_NOW.minus(Duration.ofHours(2))

        assertTrue(summary.signInExpired)
        assertEquals(syncedAt, summary.dataFrom)
        assertFalse(summary.needsAttention)
        assertEquals(PaceChipState.from(stale.primaryWindow!!, syncedAt), summary.pace)
    }

    @Test
    fun `the expired card says so, fades its data and keeps its bars flat`() {
        launch()
        // The card reads as one item, and the row's description is part of it.
        rule
            .onNodeWithTag(claudeCard)
            .assert(hasContentDescription("Sign-in expired, data from 2 hours ago"))
            // The stale session's reset time has passed: its numbers are from before the reset.
            .assert(hasText("Has reset"))
        rule
            .onAllNodes(indicatorsIn(claudeCard) and hasIndicatorStyle(IndicatorStyle.Wavy))
            .assertCountEquals(0)
        assertTrue(
            rule
                .onAllNodes(
                    inCard(claudeCard) and SemanticsMatcher.expectValue(StaleKey, true),
                    useUnmergedTree = true,
                )
                .fetchSemanticsNodes()
                .isNotEmpty()
        )
        // Grok is over its limit's pace and fresh, so it stays wavy and unfaded.
        rule
            .onAllNodes(
                inCard(accountCardTag("demo-grok")) and SemanticsMatcher.keyIsDefined(StaleKey),
                useUnmergedTree = true,
            )
            .assertCountEquals(0)
    }

    @Test
    fun `the card's sign in button is a button, and opens the sign-in for that account`() {
        launch()
        rule
            .onNode(inCard(claudeCard) and hasText("Sign in"))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .performClick()

        rule.onNodeWithTag(ACCOUNTS_TAG).assertIsDisplayed()
        rule.onNodeWithText("Sign in to Claude again").assertIsDisplayed()
        assertEquals(Provider.Claude, (signIn.state.value as? SignInState.Browser)?.provider)
    }

    @Test
    fun `leaving the sign-in goes back to the overview`() {
        launch()
        rule.onNode(inCard(claudeCard) and hasText("Sign in")).performClick()
        rule.onNodeWithContentDescription("Cancel sign-in").performClick()

        rule.onNodeWithTag(OVERVIEW_LIST_TAG).assertIsDisplayed()
    }

    @Test
    fun `last updated rounds to the nearest hour`() {
        rule.setContent {
            HeadroomTheme(dynamicColor = false) {
                SignInExpiredRow(
                    accountId = "a",
                    dataFrom = FIXED_NOW.minus(Duration.ofMinutes(118)),
                    now = FIXED_NOW,
                    onSignIn = {},
                )
            }
        }
        rule
            .onNode(hasContentDescription("Sign-in expired, data from 2 hours ago"))
            .assertIsDisplayed()
    }

    @Test
    fun `the detail shows a banner to sign in again, and when the data is from`() {
        launch()
        rule.onNodeWithTag(claudeCard).performClick()

        rule.onNodeWithTag(DETAIL_TAG).assertIsDisplayed()
        rule.onNodeWithText("Last updated 2 hours ago", substring = true).assertIsDisplayed()
        rule
            .onNodeWithText("Sign in again")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .performClick()
        rule.onNodeWithText("Sign in to Claude again").assertIsDisplayed()
    }

    @Test
    fun `an expired account's resets are not listed as upcoming`() {
        launch()
        rule.onNode(hasContentDescription("Resets")).performClick()
        rule.onAllNodesWithText("Claude · Weekly · all models").assertCountEquals(0)
    }
}
