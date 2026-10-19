package dev.sebastiano.headroom.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.espresso.Espresso
import dev.sebastiano.headroom.auth.TokenSet
import dev.sebastiano.headroom.designsystem.HeadroomTheme
import dev.sebastiano.headroom.model.Account
import dev.sebastiano.headroom.model.DemoData
import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.QuotaDisplay
import dev.sebastiano.headroom.model.RedeemIntent
import dev.sebastiano.headroom.model.RedeemOutcome
import dev.sebastiano.headroom.model.ResetAttemptKey
import dev.sebastiano.headroom.model.ResetAttemptMemory
import dev.sebastiano.headroom.model.ResetAvailability
import dev.sebastiano.headroom.model.ResetProvider
import dev.sebastiano.headroom.prototype.ResetScenarios
import dev.sebastiano.headroom.prototype.ScenarioAccounts
import dev.sebastiano.headroom.signin.DeviceSession
import dev.sebastiano.headroom.signin.ServiceSignIn
import dev.sebastiano.headroom.signin.ServiceSignInState
import dev.sebastiano.headroom.signin.SignInError
import dev.sebastiano.headroom.ui.components.rememberResetFormatter
import dev.sebastiano.headroom.ui.home.toSummary
import dev.sebastiano.headroom.ui.overview.OVERVIEW_LIST_TAG
import dev.sebastiano.headroom.ui.overview.accountCardTag
import dev.sebastiano.headroom.ui.resets.RESETS_CARD_TAG
import dev.sebastiano.headroom.ui.resets.RedeemSheet
import dev.sebastiano.headroom.ui.resets.poolChoiceTag
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * A Z.AI account whose resets need the ZCode sign-in: the sheet readies the sign-in page before the
 * tap, so the page opens at once, and a finished sign-in turns the sheet's sign-in step into the
 * account's resets instead of closing the sheet.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class RedeemSheetZCodeSignInTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val state = ScenarioAccounts.zaiAccount(FIXED_NOW)
    private val signedIn = ResetScenarios.zaiPools(FIXED_NOW, fiveHour = 2, week = 1)
    private val signedOut = signedIn.copy(pools = emptyList(), requiresSignIn = true)

    @Test
    fun `a finished sign-in shows the resets instead of closing the sheet`() {
        var dismissed = 0
        var availability by mutableStateOf(signedOut)
        var signIn by mutableStateOf<ServiceSignInState>(ServiceSignInState.Idle)
        val provider =
            object : ResetProvider {
                override suspend fun availability(account: Account): ResetAvailability =
                    availability

                override suspend fun redeem(
                    account: Account,
                    poolId: String,
                    attemptKey: ResetAttemptKey,
                ): RedeemOutcome = RedeemOutcome.Unsupported
            }
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
                    onSignIn = {},
                    signIn = signIn,
                )
            }
        }
        rule.waitForIdle()
        rule.onNode(SIGN_IN_BUTTON and hasAnyAncestor(isDialog())).assertExists()

        // The sign-in finishes, and the account's fresh resets flow in.
        signIn = ServiceSignInState.Done(state.account.id)
        availability = signedIn
        rule.waitForIdle()

        rule.onNodeWithTag(poolChoiceTag("five_hour")).assertExists()
        rule.onNodeWithTag(poolChoiceTag("week")).assertExists()
        assertEquals(0, dismissed)
    }

    @Test
    fun `a ZCode sign-in for another account names the account that has it or the expected one`() {
        var signIn by
            mutableStateOf<ServiceSignInState>(
                ServiceSignInState.Failed(
                    accountId = state.account.id,
                    error = SignInError.DifferentAccount,
                    otherAccount = "Work",
                )
            )
        val provider =
            object : ResetProvider {
                override suspend fun availability(account: Account): ResetAvailability = signedOut

                override suspend fun redeem(
                    account: Account,
                    poolId: String,
                    attemptKey: ResetAttemptKey,
                ): RedeemOutcome = RedeemOutcome.Unsupported
            }
        rule.setContent {
            HeadroomTheme(dynamicColor = false) {
                RedeemSheet(
                    account = state.account,
                    summary = state.toSummary(FIXED_NOW, emptyMap(), emptyMap()),
                    availability = signedOut,
                    provider = provider,
                    intent = RedeemIntent.Use,
                    display = QuotaDisplay.Used,
                    formatter = rememberResetFormatter(ZoneOffset.UTC),
                    memory = ResetAttemptMemory(),
                    onDismiss = {},
                    onSignIn = {},
                    signIn = signIn,
                )
            }
        }
        rule.waitForIdle()

        rule
            .onNode(
                hasText(
                    "That ZCode account is already connected to Work. Sign in to the ZCode account for this one."
                ) and hasAnyAncestor(isDialog())
            )
            .assertExists()

        signIn =
            ServiceSignInState.Failed(
                accountId = state.account.id,
                error = SignInError.DifferentAccount,
                expectedAccount = "ada@example.com",
            )
        rule.waitForIdle()

        rule
            .onNode(
                hasText(
                    "You signed in to a different account. Sign in as ada@example.com to update this one."
                ) and hasAnyAncestor(isDialog())
            )
            .assertExists()
    }

    @Test
    fun `the sheet readies the sign-in page before the tap`() {
        var sessions = 0
        launchApp(newZCodeSignIn { sessions++ })
        openZAiSheet()

        // The page is readied while the user reads the sheet: the flow started once.
        assertEquals(1, sessions)

        rule.onNode(SIGN_IN_BUTTON and hasAnyAncestor(isDialog())).performClick()
        rule.waitForIdle()

        // The tap opens the readied page at once: no second flow, and the tab is up.
        assertEquals(1, sessions)
        assertEquals(PAGE_URL, shadowOf(rule.activity).nextStartedActivity?.dataString)
    }

    @Test
    fun `closing the sheet without signing in forgets the readied page`() {
        var sessions = 0
        launchApp(newZCodeSignIn { sessions++ })
        openZAiSheet()
        assertEquals(1, sessions)

        Espresso.pressBackUnconditionally()
        rule.waitForIdle()
        rule.onNode(SIGN_IN_BUTTON).performClick()
        rule.waitForIdle()

        // A readied page expires a few minutes after it is made, so a reopened sheet readies a
        // new one instead of reusing a stale page.
        assertEquals(2, sessions)
    }

    private fun launchApp(zCode: ServiceSignIn) {
        val provider =
            object : ResetProvider {
                override suspend fun availability(account: Account): ResetAvailability? =
                    if (account.provider == Provider.ZAi) signedOut else null

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
                            realAccounts = DemoData.manyAccounts(FIXED_NOW),
                            resetProvider = provider,
                            zCodeSignIn = zCode,
                        )
                )
            }
        }
        rule.waitForIdle()
    }

    private fun openZAiSheet() {
        rule
            .onNodeWithTag(OVERVIEW_LIST_TAG)
            .performScrollToNode(hasTestTag(accountCardTag("demo-zai")))
        rule.onNodeWithTag(accountCardTag("demo-zai")).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(RESETS_CARD_TAG).performScrollTo()
        rule.onNode(SIGN_IN_BUTTON).performClick()
        rule.waitForIdle()
    }

    /** A ZCode session whose page is ready and whose poll never answers. */
    private object NeverEndingSession : DeviceSession {
        override val userCode: String = ""
        override val verificationUrl: String = PAGE_URL

        override suspend fun awaitTokens(): TokenSet = CompletableDeferred<TokenSet>().await()
    }

    private fun newZCodeSignIn(
        scope: CoroutineScope = CoroutineScope(Dispatchers.Unconfined),
        onStart: () -> Unit,
    ): ServiceSignIn =
        ServiceSignIn(
            start = {
                onStart()
                NeverEndingSession
            },
            save = { _, _ -> },
            scope = scope,
        )

    private companion object {
        const val PAGE_URL = "https://chat.z.ai/prepared"
        val SIGN_IN_BUTTON = hasText("Sign in to ZCode") and hasClickAction()
    }
}
