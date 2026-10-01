package dev.sebastiano.headroom.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.test.espresso.Espresso
import dev.sebastiano.headroom.designsystem.HeadroomTheme
import dev.sebastiano.headroom.model.Account
import dev.sebastiano.headroom.model.QuotaDisplay
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
import dev.sebastiano.headroom.ui.resets.REDEEM_CONFIRM_TAG
import dev.sebastiano.headroom.ui.resets.REDEEM_SHEET_TAG
import dev.sebastiano.headroom.ui.resets.RedeemSheet
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlinx.coroutines.CompletableDeferred
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * While a reset runs, and while the new usage comes in after it, nothing closes the redeem sheet:
 * not back, not a swipe down, not a tap on the scrim. Once the outcome shows, back closes it again.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class RedeemSheetDismissTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val answer = CompletableDeferred<RedeemOutcome>()
    private var dismissed = 0
    private var refreshing by mutableStateOf(false)
    private val state = ScenarioAccounts.claudeAccount(FIXED_NOW)
    private val availability = ResetScenarios.claudeGrants(FIXED_NOW)

    /** Answers a redeem only when the test lets it. */
    private val provider =
        object : ResetProvider {
            override suspend fun availability(account: Account): ResetAvailability = availability

            override suspend fun redeem(
                account: Account,
                poolId: String,
                attemptKey: ResetAttemptKey,
            ): RedeemOutcome = answer.await()
        }

    private fun startReset() {
        rule.setContent {
            HeadroomTheme(dynamicColor = false) {
                RedeemSheet(
                    account = state.account,
                    summary = state.toSummary(FIXED_NOW, emptyMap(), emptyMap()),
                    availability = availability,
                    provider = provider,
                    intent = RedeemIntent.Use,
                    display = QuotaDisplay.Used,
                    formatter = rememberResetFormatter(ZoneOffset.UTC),
                    memory = ResetAttemptMemory(),
                    onDismiss = { dismissed++ },
                    refreshing = refreshing,
                )
            }
        }
        rule.waitForIdle()
        rule.onNodeWithTag(REDEEM_CONFIRM_TAG).performClick()
        rule.waitForIdle()
    }

    /** Back, a swipe down on the sheet, and a tap on the scrim above it. */
    private fun tryEveryWayToDismiss() {
        Espresso.pressBackUnconditionally()
        rule.waitForIdle()
        rule.onNodeWithTag(REDEEM_SHEET_TAG).performTouchInput { swipeDown() }
        rule.waitForIdle()
        rule.onNode(isDialog()).performTouchInput { click(Offset(centerX, SCRIM_Y)) }
        rule.waitForIdle()
    }

    @Test
    fun `nothing closes the sheet while the reset runs`() {
        startReset()

        tryEveryWayToDismiss()

        assertEquals(0, dismissed)
        rule.onNodeWithTag(REDEEM_SHEET_TAG).assertExists()
    }

    @Test
    fun `nothing closes the sheet while the new usage comes in`() {
        refreshing = true
        startReset()
        answer.complete(RedeemOutcome.Success(resetsLeft = 3))
        rule.waitForIdle()

        tryEveryWayToDismiss()

        assertEquals(0, dismissed)
        rule.onNodeWithTag(REDEEM_SHEET_TAG).assertExists()
    }

    @Test
    fun `once the outcome shows, back closes the sheet`() {
        startReset()
        answer.complete(RedeemOutcome.Success(resetsLeft = 3))
        rule.waitForIdle()

        Espresso.pressBackUnconditionally()
        rule.waitForIdle()

        assertEquals(1, dismissed)
    }

    private companion object {
        /** Near the top of the window, well above the sheet: on the scrim. */
        const val SCRIM_Y = 40f
    }
}
