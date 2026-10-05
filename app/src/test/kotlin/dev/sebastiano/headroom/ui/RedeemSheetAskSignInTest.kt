package dev.sebastiano.headroom.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import dev.sebastiano.headroom.designsystem.HeadroomTheme
import dev.sebastiano.headroom.model.Account
import dev.sebastiano.headroom.model.AskOutcome
import dev.sebastiano.headroom.model.QuotaDisplay
import dev.sebastiano.headroom.model.QuotaErrorKind
import dev.sebastiano.headroom.model.RedeemIntent
import dev.sebastiano.headroom.model.RedeemOutcome
import dev.sebastiano.headroom.model.ResetAttemptKey
import dev.sebastiano.headroom.model.ResetAttemptMemory
import dev.sebastiano.headroom.model.ResetAvailability
import dev.sebastiano.headroom.model.ResetProvider
import dev.sebastiano.headroom.prototype.ResetScenarios
import dev.sebastiano.headroom.prototype.ScenarioAccounts
import dev.sebastiano.headroom.ui.components.rememberResetFormatter
import dev.sebastiano.headroom.ui.home.toSummary
import dev.sebastiano.headroom.ui.resets.RedeemSheet
import java.time.ZoneOffset
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * An ask that Z.AI refuses because the ZCode sign-in no longer works offers that sign-in again, as
 * a refused reset does. Other failures do not.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class RedeemSheetAskSignInTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val state = ScenarioAccounts.zaiAccount(FIXED_NOW)
    private val availability = ResetScenarios.zaiPools(FIXED_NOW, fiveHour = 0, week = 0)
    private var signIns = 0

    private fun ask(answer: AskOutcome) {
        val provider =
            object : ResetProvider {
                override suspend fun availability(account: Account): ResetAvailability =
                    availability

                override suspend fun redeem(
                    account: Account,
                    poolId: String,
                    attemptKey: ResetAttemptKey,
                ): RedeemOutcome = RedeemOutcome.Unsupported

                override suspend fun askForMore(account: Account): AskOutcome = answer
            }
        rule.setContent {
            HeadroomTheme(dynamicColor = false) {
                RedeemSheet(
                    account = state.account,
                    summary = state.toSummary(FIXED_NOW, emptyMap(), emptyMap()),
                    availability = availability,
                    provider = provider,
                    intent = RedeemIntent.AskForMore,
                    display = QuotaDisplay.Used,
                    formatter = rememberResetFormatter(ZoneOffset.UTC),
                    memory = ResetAttemptMemory(),
                    onDismiss = {},
                    onSignIn = { signIns++ },
                )
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun `a refused ask offers the ZCode sign-in again`() {
        ask(AskOutcome.Failed(QuotaErrorKind.Auth))

        rule.onNode(hasText(SIGN_IN_AGAIN) and hasClickAction()).performClick()
        rule.waitForIdle()

        assertEquals(1, signIns)
    }

    @Test
    fun `an ask that could not reach Z_AI offers no sign-in`() {
        ask(AskOutcome.Failed(QuotaErrorKind.Network))

        rule.onAllNodes(hasText(SIGN_IN_AGAIN)).assertCountEquals(0)
    }

    private companion object {
        const val SIGN_IN_AGAIN = "Sign in again"
    }
}
