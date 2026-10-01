package dev.sebastiano.headroom.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import dev.sebastiano.headroom.designsystem.HeadroomTheme
import dev.sebastiano.headroom.designsystem.StaleKey
import dev.sebastiano.headroom.model.Account
import dev.sebastiano.headroom.model.DemoData
import dev.sebastiano.headroom.model.QuotaErrorKind
import dev.sebastiano.headroom.model.RedeemOutcome
import dev.sebastiano.headroom.model.ResetAttemptKey
import dev.sebastiano.headroom.model.ResetAvailability
import dev.sebastiano.headroom.model.ResetProvider
import dev.sebastiano.headroom.prototype.ResetScenarios
import dev.sebastiano.headroom.ui.overview.OVERVIEW_LIST_TAG
import dev.sebastiano.headroom.ui.overview.accountCardTag
import dev.sebastiano.headroom.ui.resets.RESETS_CARD_TAG
import dev.sebastiano.headroom.ui.resets.useResetTag
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * An account whose sign-in expired shows old data. Its resets are as old as its usage, so the app
 * never offers to use one until the user signs in again: its Resets card is faded with the rest of
 * its data and has no actions, and the Resets tab does not list it among the resets you can use.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class ExpiredAccountResetsTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    /** Every account holds one reset it can use now. */
    private val provider =
        object : ResetProvider {
            override suspend fun availability(account: Account): ResetAvailability =
                ResetScenarios.grokPool(FIXED_NOW)

            override suspend fun redeem(
                account: Account,
                poolId: String,
                attemptKey: ResetAttemptKey,
            ): RedeemOutcome = RedeemOutcome.Success(resetsLeft = 0)
        }

    /** The demo accounts, with Grok's sign-in expired: its data is from two hours ago. */
    private val accounts =
        DemoData.accounts(FIXED_NOW).map {
            if (it.account.id == "demo-grok") it.copy(lastError = QuotaErrorKind.Auth) else it
        }

    private fun launch() {
        rule.setContent {
            HeadroomTheme(dynamicColor = false) {
                HeadroomApp(
                    graph =
                        testGraph(
                            rule.activity,
                            realAccounts = accounts,
                            resetProvider = provider,
                        )
                )
            }
        }
        rule.waitForIdle()
    }

    private fun inResetsCard() = hasAnyAncestor(hasTestTag(RESETS_CARD_TAG))

    @Test
    fun `the Resets tab lists only the resets of accounts with a working sign-in`() {
        launch()
        rule.onNodeWithContentDescription("Resets").performClick()
        rule.waitForIdle()

        rule.onAllNodes(hasTestTag(useResetTag("demo-grok"))).assertCountEquals(0)
        rule.onAllNodes(hasTestTag(useResetTag("demo-codex"))).assertCountEquals(1)
    }

    @Test
    fun `an expired account's Resets card is faded and offers no action`() {
        launch()
        rule
            .onNodeWithTag(OVERVIEW_LIST_TAG)
            .performScrollToNode(hasTestTag(accountCardTag("demo-grok")))
        rule.onNodeWithTag(accountCardTag("demo-grok")).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(RESETS_CARD_TAG).performScrollTo()

        rule.onAllNodes(inResetsCard() and hasText("Use a reset")).assertCountEquals(0)
        rule
            .onAllNodes(
                hasTestTag(RESETS_CARD_TAG) and SemanticsMatcher.expectValue(StaleKey, true),
                useUnmergedTree = true,
            )
            .assertCountEquals(1)
    }

    @Test
    fun `a working account's Resets card still offers to use a reset`() {
        launch()
        rule
            .onNodeWithTag(OVERVIEW_LIST_TAG)
            .performScrollToNode(hasTestTag(accountCardTag("demo-codex")))
        rule.onNodeWithTag(accountCardTag("demo-codex")).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(RESETS_CARD_TAG).performScrollTo()

        rule.onAllNodes(inResetsCard() and hasText("Use a reset")).assertCountEquals(1)
    }
}
