package dev.sebastiano.headroom.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.sebastiano.headroom.designsystem.HeadroomTheme
import dev.sebastiano.headroom.model.Account
import dev.sebastiano.headroom.model.DemoData
import dev.sebastiano.headroom.model.RedeemOutcome
import dev.sebastiano.headroom.model.ResetAttemptKey
import dev.sebastiano.headroom.model.ResetAvailability
import dev.sebastiano.headroom.model.ResetProvider
import dev.sebastiano.headroom.prototype.ResetScenarios
import dev.sebastiano.headroom.ui.overview.accountCardTag
import dev.sebastiano.headroom.ui.resets.RESETS_CARD_TAG
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The detail leads with the usage: the windows, then the chart, then the resets, then alerts. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h2400dp")
class DetailOrderTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `the chart sits between the windows and the Resets card, and alerts come last`() {
        val provider =
            object : ResetProvider {
                override suspend fun availability(account: Account): ResetAvailability =
                    ResetScenarios.claudeGrants(FIXED_NOW)

                override suspend fun redeem(
                    account: Account,
                    poolId: String,
                    attemptKey: ResetAttemptKey,
                ): RedeemOutcome = RedeemOutcome.Unsupported
            }
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
        rule.onNodeWithTag(accountCardTag("demo-claude")).performClick()
        rule.waitForIdle()

        val chart = rule.onNodeWithText("This week").getBoundsInRoot().top
        val resets = rule.onNodeWithTag(RESETS_CARD_TAG).getBoundsInRoot().top
        val alerts = rule.onNodeWithText("Reset alerts").getBoundsInRoot().top
        assertTrue(chart < resets, "chart $chart, resets $resets")
        assertTrue(resets < alerts, "resets $resets, alerts $alerts")
    }
}
