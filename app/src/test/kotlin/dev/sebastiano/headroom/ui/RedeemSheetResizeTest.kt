package dev.sebastiano.headroom.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.sebastiano.headroom.designsystem.HeadroomTheme
import dev.sebastiano.headroom.model.Account
import dev.sebastiano.headroom.model.AccountState
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
import dev.sebastiano.headroom.ui.resets.REDEEM_CHECK_AGAIN_TAG
import dev.sebastiano.headroom.ui.resets.REDEEM_CONFIRM_TAG
import dev.sebastiano.headroom.ui.resets.REDEEM_SHEET_TAG
import dev.sebastiano.headroom.ui.resets.REDEEM_TRY_AGAIN_TAG
import dev.sebastiano.headroom.ui.resets.RedeemSheet
import dev.sebastiano.headroom.ui.resets.poolChoiceTag
import java.time.ZoneOffset
import kotlin.math.abs
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The redeem sheet resizes smoothly between its steps: its top edge moves a little in each frame,
 * never in one jump. The test follows the top of the sheet's content frame by frame through every
 * change of step.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class RedeemSheetResizeTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val answer = CompletableDeferred<RedeemOutcome>()
    private val checked = CompletableDeferred<RedeemOutcome>()
    private val asked = CompletableDeferred<AskOutcome>()

    private fun open(state: AccountState, availability: ResetAvailability) {
        val provider =
            object : ResetProvider {
                override suspend fun availability(account: Account): ResetAvailability =
                    availability

                override suspend fun redeem(
                    account: Account,
                    poolId: String,
                    attemptKey: ResetAttemptKey,
                ): RedeemOutcome = answer.await()

                override suspend fun check(
                    account: Account,
                    poolId: String,
                    attemptKey: ResetAttemptKey,
                ): RedeemOutcome = checked.await()

                override suspend fun askForMore(account: Account): AskOutcome = asked.await()
            }
        rule.mainClock.autoAdvance = false
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
                    onDismiss = {},
                )
            }
        }
        rule.mainClock.advanceTimeBy(SETTLE_MILLIS)
    }

    private fun top(): Float {
        val node = rule.onNodeWithTag(REDEEM_SHEET_TAG).fetchSemanticsNode()
        return node.boundsInRoot.top / node.layoutInfo.density.density
    }

    /**
     * Runs [change], then follows the sheet's top for [FRAMES] frames, and checks that no frame
     * moves it by more than a quarter of the whole way it travels.
     */
    private fun assertResizesSmoothly(name: String, change: () -> Unit) {
        val tops = mutableListOf(top())
        change()
        repeat(FRAMES) {
            rule.mainClock.advanceTimeBy(FRAME_MILLIS)
            tops += top()
        }
        val steps = tops.zipWithNext { a, b -> abs(b - a) }
        val travel = steps.sum()
        val biggest = steps.max()
        assertTrue(
            travel < MIN_TRAVEL_DP || biggest <= travel * MAX_SHARE,
            "$name: the sheet's top jumped $biggest dp in one frame, of $travel dp in all: $tops",
        )
    }

    @Test
    fun `the sheet resizes smoothly through the Z_AI steps`() {
        open(ScenarioAccounts.zaiAccount(FIXED_NOW), ResetScenarios.zaiPools(FIXED_NOW, 2, 1))

        assertResizesSmoothly("choose to confirm") {
            rule.onNodeWithTag(poolChoiceTag("week")).performClick()
        }
        assertResizesSmoothly("back to choose") { rule.onNodeWithText("Back").performClick() }
        assertResizesSmoothly("choose to confirm, again") {
            rule.onNodeWithTag(poolChoiceTag("five_hour")).performClick()
        }
        assertResizesSmoothly("confirm to resetting") {
            rule.onNodeWithTag(REDEEM_CONFIRM_TAG).performClick()
        }
        assertResizesSmoothly("resetting to the outcome") {
            answer.complete(RedeemOutcome.Success(resetsLeft = 1))
        }
    }

    @Test
    fun `the sheet resizes smoothly through a Claude failure`() {
        open(ScenarioAccounts.claudeAccount(FIXED_NOW), ResetScenarios.claudeGrants(FIXED_NOW))

        assertResizesSmoothly("confirm to resetting") {
            rule.onNodeWithTag(REDEEM_CONFIRM_TAG).performClick()
        }
        assertResizesSmoothly("resetting to the failure") {
            answer.complete(RedeemOutcome.Failed(QuotaErrorKind.Network))
        }
    }

    @Test
    fun `the sheet resizes smoothly through a Codex success`() {
        open(ScenarioAccounts.codexAccount(FIXED_NOW), ResetScenarios.codexPool(FIXED_NOW, 2))

        assertResizesSmoothly("confirm to resetting") {
            rule.onNodeWithTag(REDEEM_CONFIRM_TAG).performClick()
        }
        assertResizesSmoothly("resetting to the outcome") {
            answer.complete(RedeemOutcome.Success(resetsLeft = 1))
        }
    }

    @Test
    fun `the sheet resizes smoothly through Try again`() {
        open(ScenarioAccounts.grokAccount(FIXED_NOW), ResetScenarios.grokPool(FIXED_NOW))
        rule.onNodeWithTag(REDEEM_CONFIRM_TAG).performClick()
        rule.mainClock.advanceTimeBy(SETTLE_MILLIS)
        answer.complete(RedeemOutcome.Failed(QuotaErrorKind.Network))
        rule.mainClock.advanceTimeBy(SETTLE_MILLIS)

        assertResizesSmoothly("the failure to resetting") {
            rule.onNodeWithTag(REDEEM_TRY_AGAIN_TAG).performClick()
        }
    }

    @Test
    fun `the sheet resizes smoothly through Check again`() {
        open(ScenarioAccounts.claudeAccount(FIXED_NOW), ResetScenarios.claudeGrants(FIXED_NOW))
        rule.onNodeWithTag(REDEEM_CONFIRM_TAG).performClick()
        rule.mainClock.advanceTimeBy(SETTLE_MILLIS)

        assertResizesSmoothly("resetting to unconfirmed") {
            answer.complete(RedeemOutcome.Unconfirmed)
        }
        assertResizesSmoothly("unconfirmed to checking") {
            rule.onNodeWithTag(REDEEM_CHECK_AGAIN_TAG).performClick()
        }
        assertResizesSmoothly("checking to the outcome") {
            checked.complete(RedeemOutcome.Success(resetsLeft = 1))
        }
    }

    @Test
    fun `the sheet resizes smoothly through Ask for a reset card`() {
        open(ScenarioAccounts.zaiAccount(FIXED_NOW), ResetScenarios.zaiPools(FIXED_NOW, 0, 0))

        assertResizesSmoothly("no credit to asking") {
            rule.onNodeWithText("Ask for a reset card").performClick()
        }
        assertResizesSmoothly("asking to granted") { asked.complete(AskOutcome.Granted("week")) }
    }

    @Test
    fun `the sheet resizes smoothly when the sign-in note shows`() {
        open(
            ScenarioAccounts.zaiAccount(FIXED_NOW),
            ResetAvailability(emptyList(), requiresSignIn = true),
        )

        assertResizesSmoothly("the note") {
            rule.onNode(hasText("Sign in to ZCode") and hasClickAction()).performClick()
        }
    }

    private companion object {
        const val SETTLE_MILLIS = 2_000L
        const val FRAME_MILLIS = 16L
        const val FRAMES = 60
        /** Below this the sheet barely moves, and a single step is not a jump anyone sees. */
        const val MIN_TRAVEL_DP = 4f
        /** A spring moves at most about a sixth of the way in its fastest frame. */
        const val MAX_SHARE = 0.25f
    }
}
