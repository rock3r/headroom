package dev.sebastiano.headroom.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.sebastiano.headroom.auth.TokenSet
import dev.sebastiano.headroom.designsystem.HeadroomTheme
import dev.sebastiano.headroom.model.AccountState
import dev.sebastiano.headroom.model.CursorPrompt
import dev.sebastiano.headroom.model.DemoData
import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.signin.DeviceSession
import dev.sebastiano.headroom.signin.ServiceSignIn
import dev.sebastiano.headroom.signin.ServiceSignInState
import dev.sebastiano.headroom.ui.components.CursorConnect
import dev.sebastiano.headroom.ui.components.CursorPromptRow
import dev.sebastiano.headroom.ui.home.AccountSummary
import dev.sebastiano.headroom.ui.home.homeUiState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** What each Cursor prompt's button does: connect, or read the account again. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class CursorPromptActionTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val now = Instant.parse("2026-10-18T12:00:00Z")
    private var retries = 0
    private val signIn = signIn()

    private fun signIn(scope: CoroutineScope = CoroutineScope(Dispatchers.Unconfined)) =
        ServiceSignIn(start = { Session }, save = { _, _ -> }, scope = scope, label = "Cursor")

    private val connect = CursorConnect(signIn, onDismiss = {}, onRetry = { retries++ })

    private fun grokSummary(prompt: CursorPrompt): AccountSummary {
        val states: List<AccountState> =
            DemoData.accounts(now).map { state ->
                if (state.account.provider != Provider.Grok) state
                else
                    state.copy(
                        snapshot =
                            state.snapshot?.copy(
                                cursorLinked = prompt != CursorPrompt.NotLinked,
                                cursorReadFailed = prompt == CursorPrompt.ReadFailed,
                            )
                    )
            }
        val summaries =
            homeUiState(
                    accounts = states,
                    now = now,
                    alerts = emptyMap(),
                    pastResets = emptyMap(),
                    isDemo = false,
                    isRefreshing = false,
                )
                .accounts
        val grok = summaries.first { it.provider == Provider.Grok }
        assertEquals(prompt, grok.cursorPrompt)
        return grok
    }

    private fun show(prompt: CursorPrompt) {
        val summary = grokSummary(prompt)
        rule.setContent {
            HeadroomTheme(dynamicColor = false) {
                CursorPromptRow(summary = summary, connect = connect)
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun `a missing sign-in asks for one, and the button starts it`() {
        show(CursorPrompt.NotLinked)

        rule.onNodeWithText("Connect").performClick()
        rule.waitForIdle()

        assertIs<ServiceSignInState.Waiting>(signIn.state.value)
        assertEquals(0, retries)
    }

    @Test
    fun `a read that failed is tried again, without asking for a new sign-in`() {
        show(CursorPrompt.ReadFailed)

        rule.onNodeWithText("Retry").performClick()
        rule.waitForIdle()

        assertEquals(1, retries)
        // The credential is still there and still good: a new sign-in would replace it.
        assertIs<ServiceSignInState.Idle>(signIn.state.value)
    }

    /** A sign-in whose browser page the user has not finished yet. */
    private object Session : DeviceSession {
        override val userCode: String = ""
        override val verificationUrl: String = "https://cursor.com/loginDeepControl"

        override suspend fun awaitTokens(): TokenSet = CompletableDeferred<TokenSet>().await()
    }
}
