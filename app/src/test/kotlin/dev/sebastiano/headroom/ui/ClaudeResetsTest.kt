package dev.sebastiano.headroom.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.sebastiano.headroom.designsystem.HeadroomTheme
import dev.sebastiano.headroom.model.Account
import dev.sebastiano.headroom.model.DemoData
import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.RedeemOutcome
import dev.sebastiano.headroom.model.ResetAttemptKey
import dev.sebastiano.headroom.model.ResetAvailability
import dev.sebastiano.headroom.model.ResetProvider
import dev.sebastiano.headroom.prototype.ResetScenarios
import dev.sebastiano.headroom.ui.overview.accountCardTag
import dev.sebastiano.headroom.ui.resets.RESETS_CARD_TAG
import dev.sebastiano.headroom.ui.resets.useResetTag
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Claude's resets can be used out of the box: the count and the grants, with a button to use one,
 * like Codex's and Grok's.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class ClaudeResetsTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val provider =
        object : ResetProvider {
            override suspend fun availability(account: Account): ResetAvailability? =
                when (account.provider) {
                    Provider.Claude -> ResetScenarios.claudeGrants(FIXED_NOW)
                    Provider.Codex -> ResetScenarios.codexPool(FIXED_NOW, available = 2)
                    else -> null
                }

            override suspend fun redeem(
                account: Account,
                poolId: String,
                attemptKey: ResetAttemptKey,
            ): RedeemOutcome = RedeemOutcome.Unsupported
        }

    private fun launch() {
        rule.setContent {
            HeadroomTheme(dynamicColor = false) {
                HeadroomApp(
                    graph =
                        testGraph(
                            rule.activity,
                            realAccounts = DemoData.accounts(FIXED_NOW),
                            resetProvider = provider,
                        )
                )
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun `Claude's resets card has a use action`() {
        launch()
        rule.onNodeWithTag(accountCardTag("demo-claude")).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(RESETS_CARD_TAG).performScrollTo().assertIsDisplayed()

        val inCard = hasAnyAncestor(hasTestTag(RESETS_CARD_TAG))
        rule.onAllNodes(inCard and hasText("Launch week")).assertCountEquals(1)
        rule.onAllNodes(inCard and hasText("Use a reset")).assertCountEquals(1)
    }

    @Test
    fun `the Resets tab lists Claude's resets with a button, like Codex's`() {
        launch()
        rule.onNodeWithContentDescription("Resets").performClick()
        rule.waitForIdle()

        rule.onAllNodes(hasTestTag(useResetTag("demo-claude"))).assertCountEquals(1)
        rule.onAllNodes(hasTestTag(useResetTag("demo-codex"))).assertCountEquals(1)
    }
}
