package dev.sebastiano.headroom.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import dev.sebastiano.headroom.designsystem.HeadroomTheme
import dev.sebastiano.headroom.model.AccountState
import dev.sebastiano.headroom.model.QuotaDisplay
import dev.sebastiano.headroom.model.RedeemStep
import dev.sebastiano.headroom.model.ResetAvailability
import dev.sebastiano.headroom.prototype.ResetScenarios
import dev.sebastiano.headroom.prototype.ScenarioAccounts
import dev.sebastiano.headroom.ui.components.rememberResetFormatter
import dev.sebastiano.headroom.ui.home.toSummary
import dev.sebastiano.headroom.ui.resets.REDEEM_CONFIRM_TAG
import dev.sebastiano.headroom.ui.resets.RedeemActions
import dev.sebastiano.headroom.ui.resets.RedeemSheetContent
import java.time.ZoneOffset
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The confirmation of a Claude reset says that using it is experimental, warns that a reset that
 * works at any time cannot be undone, and explains why the button is off away from a limit.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class ClaudeRedeemConfirmTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun confirm(account: AccountState, availability: ResetAvailability) {
        val step = RedeemStep.Confirm(availability.pools.first(), canGoBack = false)
        rule.setContent {
            HeadroomTheme(dynamicColor = false) {
                RedeemSheetContent(
                    step = step,
                    summary = account.toSummary(FIXED_NOW, emptyMap(), emptyMap()),
                    display = QuotaDisplay.Used,
                    formatter = rememberResetFormatter(ZoneOffset.UTC),
                    actions = RedeemActions(),
                )
            }
        }
        rule.waitForIdle()
    }

    private val claude = ScenarioAccounts.claudeAccount(FIXED_NOW)

    @Test
    fun `a Claude confirmation is labelled experimental`() {
        confirm(claude, ResetScenarios.claudeGrants(FIXED_NOW))

        rule.onNodeWithText("Experimental").assertIsDisplayed()
        rule.onNodeWithTag(REDEEM_CONFIRM_TAG).assertIsEnabled()
    }

    @Test
    fun `other providers' confirmations are not`() {
        confirm(
            ScenarioAccounts.codexAccount(FIXED_NOW),
            ResetScenarios.codexPool(FIXED_NOW, available = 2),
        )

        rule.onAllNodes(hasText("Experimental")).assertCountEquals(0)
    }

    @Test
    fun `a grant that works at any time says it cannot be undone`() {
        confirm(claude, ResetScenarios.claudeAnyTime(FIXED_NOW))

        rule.onNodeWithText("cannot be undone", substring = true).assertIsDisplayed()
        rule.onNodeWithTag(REDEEM_CONFIRM_TAG).assertIsEnabled()
    }

    @Test
    fun `a grant Claude does not allow yet is off, and says why`() {
        confirm(claude, ResetScenarios.claudeNotUsableYet(FIXED_NOW))

        rule
            .onNodeWithText("Claude does not let you use this reset yet", substring = true)
            .assertIsDisplayed()
        rule.onNodeWithTag(REDEEM_CONFIRM_TAG).assertIsNotEnabled()
    }

    @Test
    fun `a grant that needs a limit is off away from one, and says why`() {
        confirm(claude, ResetScenarios.claudeWaiting(FIXED_NOW))

        rule.onNodeWithText("You are not at a limit now", substring = true).assertIsDisplayed()
        rule.onNodeWithTag(REDEEM_CONFIRM_TAG).assertIsNotEnabled()
    }
}
