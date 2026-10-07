package dev.sebastiano.headroom.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.espresso.Espresso
import dev.sebastiano.headroom.designsystem.HeadroomTheme
import dev.sebastiano.headroom.model.Account
import dev.sebastiano.headroom.model.DemoData
import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.RedeemOutcome
import dev.sebastiano.headroom.model.ResetAttemptKey
import dev.sebastiano.headroom.model.ResetAvailability
import dev.sebastiano.headroom.model.ResetProvider
import dev.sebastiano.headroom.prototype.ResetScenarios
import dev.sebastiano.headroom.ui.detail.DETAIL_TAG
import dev.sebastiano.headroom.ui.overview.OVERVIEW_LIST_TAG
import dev.sebastiano.headroom.ui.resets.REDEEM_SHEET_TAG
import dev.sebastiano.headroom.ui.resets.RESETS_TAG
import dev.sebastiano.headroom.ui.resets.availableResetTag
import dev.sebastiano.headroom.ui.resets.resetRowTag
import dev.sebastiano.headroom.ui.resets.useResetTag
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Details opened from the Resets tab belong to the Resets tab: back returns there, whichever row
 * opened them. The available-reset rows open details, and their Use button still opens the sheet.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class ResetsNavigationTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val provider =
        object : ResetProvider {
            override suspend fun availability(account: Account): ResetAvailability? =
                if (account.provider == Provider.Codex) {
                    ResetScenarios.codexPool(FIXED_NOW, available = 2)
                } else {
                    null
                }

            override suspend fun redeem(
                account: Account,
                poolId: String,
                attemptKey: ResetAttemptKey,
            ): RedeemOutcome = RedeemOutcome.Unsupported
        }

    private fun launchOnResets() {
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
        rule.onNodeWithContentDescription("Resets").performClick()
        rule.waitForIdle()
    }

    private fun assertBackOnResets() {
        rule.onNodeWithTag(RESETS_TAG).assertIsDisplayed()
        rule.onNodeWithTag(OVERVIEW_LIST_TAG).assertDoesNotExist()
        rule.onNodeWithTag(DETAIL_TAG).assertDoesNotExist()
    }

    @Test
    fun `an available-reset row opens the account, and back returns to Resets`() {
        launchOnResets()

        rule
            .onNodeWithTag(availableResetTag("demo-codex"))
            .assert(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick))
            .performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(DETAIL_TAG).assertIsDisplayed()

        Espresso.pressBack()
        rule.waitForIdle()
        assertBackOnResets()
    }

    @Test
    fun `an upcoming reset row opens the account, and back returns to Resets`() {
        launchOnResets()

        rule
            .onNodeWithTag(RESETS_TAG)
            .performScrollToNode(hasTestTag(resetRowTag("demo-grok", "weekly")))
        rule.onNodeWithTag(resetRowTag("demo-grok", "weekly")).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(DETAIL_TAG).assertIsDisplayed()

        Espresso.pressBack()
        rule.waitForIdle()
        assertBackOnResets()
    }

    @Test
    fun `the detail's back arrow also returns to Resets`() {
        launchOnResets()
        rule.onNodeWithTag(availableResetTag("demo-codex")).performClick()
        rule.waitForIdle()

        rule.onNodeWithContentDescription("Back").performClick()
        rule.waitForIdle()
        assertBackOnResets()
    }

    @Test
    fun `the Use button opens the sheet, not the details`() {
        launchOnResets()

        rule.onNodeWithTag(useResetTag("demo-codex")).performClick()
        rule.waitForIdle()

        rule.onNodeWithTag(REDEEM_SHEET_TAG).assertIsDisplayed()
        rule.onNodeWithTag(DETAIL_TAG).assertDoesNotExist()
    }
}
