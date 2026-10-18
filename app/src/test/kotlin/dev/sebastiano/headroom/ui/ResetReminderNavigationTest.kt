package dev.sebastiano.headroom.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
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
import dev.sebastiano.headroom.ui.resets.RESETS_CARD_TAG
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Tapping a reminder that a reset expires soon opens the account with its Resets card in view. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h640dp")
class ResetReminderNavigationTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val provider =
        object : ResetProvider {
            override suspend fun availability(account: Account): ResetAvailability? =
                when (account.provider) {
                    Provider.Codex -> ResetScenarios.codexPool(FIXED_NOW, available = 2)
                    else -> null
                }

            override suspend fun redeem(
                account: Account,
                poolId: String,
                attemptKey: ResetAttemptKey,
            ): RedeemOutcome = RedeemOutcome.Unsupported
        }

    private fun launch(request: OpenAccountRequest) {
        rule.setContent {
            HeadroomTheme(dynamicColor = false) {
                HeadroomApp(
                    graph =
                        testGraph(
                            rule.activity,
                            realAccounts = DemoData.accounts(FIXED_NOW),
                            resetProvider = provider,
                        ),
                    openAccountRequest = request,
                )
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun `a reminder opens the account with its Resets card in view`() {
        launch(OpenAccountRequest("demo-codex", serial = 1, showResets = true))

        rule.onNodeWithTag(DETAIL_TAG).assertIsDisplayed()
        rule.onNodeWithText("Pro · sam@example.com").assertExists()
        rule.onNodeWithTag(RESETS_CARD_TAG).assertIsDisplayed()
    }

    @Test
    fun `any other request opens the account at the top`() {
        launch(OpenAccountRequest("demo-codex", serial = 1))

        rule.onNodeWithTag(DETAIL_TAG).assertIsDisplayed()
        rule.onNodeWithTag(RESETS_CARD_TAG).assertIsNotDisplayed()
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp")
    fun `on a wide screen the detail pane scrolls to the Resets card`() {
        launch(OpenAccountRequest("demo-codex", serial = 1, showResets = true))

        rule.onNodeWithTag(RESETS_CARD_TAG).assertIsDisplayed()
    }
}
