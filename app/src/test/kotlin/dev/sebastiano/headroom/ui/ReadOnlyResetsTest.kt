package dev.sebastiano.headroom.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import dev.sebastiano.headroom.designsystem.HeadroomTheme
import dev.sebastiano.headroom.model.Account
import dev.sebastiano.headroom.model.DemoData
import dev.sebastiano.headroom.model.InMemorySettingsRepository
import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.RedeemOutcome
import dev.sebastiano.headroom.model.ResetAttemptKey
import dev.sebastiano.headroom.model.ResetAvailability
import dev.sebastiano.headroom.model.ResetProvider
import dev.sebastiano.headroom.prototype.ResetScenarios
import dev.sebastiano.headroom.ui.overview.accountCardTag
import dev.sebastiano.headroom.ui.resets.RESETS_CARD_TAG
import dev.sebastiano.headroom.ui.resets.useResetTag
import dev.sebastiano.headroom.ui.settings.REDEEM_CLAUDE_RESETS_TAG
import dev.sebastiano.headroom.ui.settings.SETTINGS_LIST_TAG
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Claude's resets show for information only until the user turns on "Redeem Claude resets
 * (experimental)" in Settings: the count and the grants, with no button to use one. Codex and Grok
 * resets keep their button.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class ReadOnlyResetsTest {
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

    private val settings = InMemorySettingsRepository()

    private fun launch() {
        rule.setContent {
            HeadroomTheme(dynamicColor = false) {
                HeadroomApp(
                    graph =
                        testGraph(
                            rule.activity,
                            realAccounts = DemoData.accounts(FIXED_NOW),
                            settings = settings,
                            resetProvider = provider,
                        )
                )
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun `Claude's resets show with no action`() {
        launch()
        rule.onNodeWithTag(accountCardTag("demo-claude")).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(RESETS_CARD_TAG).performScrollTo().assertIsDisplayed()

        val inCard = hasAnyAncestor(hasTestTag(RESETS_CARD_TAG))
        rule.onAllNodes(inCard and hasText("Launch week")).assertCountEquals(1)
        rule.onAllNodes(inCard and hasText("Use a reset")).assertCountEquals(0)
        rule
            .onAllNodes(inCard and hasText("experimental", substring = true, ignoreCase = true))
            .assertCountEquals(0)
    }

    @Test
    fun `the Resets tab lists Claude's resets without a button, and Codex's with one`() {
        launch()
        rule.onNodeWithContentDescription("Resets").performClick()
        rule.waitForIdle()

        rule.onAllNodes(hasTestTag(useResetTag("demo-claude"))).assertCountEquals(0)
        rule.onAllNodes(hasTestTag(useResetTag("demo-codex"))).assertCountEquals(1)
        rule
            .onAllNodes(hasText("experimental", substring = true, ignoreCase = true))
            .assertCountEquals(0)
    }

    @Test
    fun `Settings has the switch to redeem Claude resets, off, with a note`() {
        launch()
        rule.onNodeWithContentDescription("Settings").performClick()
        rule.waitForIdle()

        rule
            .onNodeWithTag(SETTINGS_LIST_TAG)
            .performScrollToNode(hasTestTag(REDEEM_CLAUDE_RESETS_TAG))
        rule.onNodeWithTag(REDEEM_CLAUDE_RESETS_TAG).assertIsOff()
        rule.onNodeWithText("Redeem Claude resets (experimental)").assertIsDisplayed()
        rule.onNodeWithText("Claude has not published", substring = true).assertIsDisplayed()
    }

    @Test
    fun `the switch turns it on`() {
        launch()
        rule.onNodeWithContentDescription("Settings").performClick()
        rule.waitForIdle()

        rule
            .onNodeWithTag(SETTINGS_LIST_TAG)
            .performScrollToNode(hasTestTag(REDEEM_CLAUDE_RESETS_TAG))
        rule.onNodeWithTag(REDEEM_CLAUDE_RESETS_TAG).performClick()
        rule.waitForIdle()

        rule.onNodeWithTag(REDEEM_CLAUDE_RESETS_TAG).assertIsOn()
        assertTrue(settings.settings.value.redeemClaudeResets)
    }

    @Test
    fun `once turned on, Claude's resets have a button`() {
        runBlocking { settings.setRedeemClaudeResets(true) }
        launch()
        rule.onNodeWithTag(accountCardTag("demo-claude")).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(RESETS_CARD_TAG).performScrollTo().assertIsDisplayed()

        val inCard = hasAnyAncestor(hasTestTag(RESETS_CARD_TAG))
        rule.onAllNodes(inCard and hasText("Use a reset")).assertCountEquals(1)
    }
}
