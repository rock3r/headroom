package dev.sebastiano.headroom.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import dev.sebastiano.headroom.auth.AuthException
import dev.sebastiano.headroom.auth.CredentialKind
import dev.sebastiano.headroom.auth.TokenSet
import dev.sebastiano.headroom.data.account.DifferentAccountException
import dev.sebastiano.headroom.designsystem.HeadroomTheme
import dev.sebastiano.headroom.model.AccountState
import dev.sebastiano.headroom.model.CursorPrompt
import dev.sebastiano.headroom.model.DemoData
import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.QuotaErrorKind
import dev.sebastiano.headroom.signin.DeviceSession
import dev.sebastiano.headroom.signin.ServiceSignIn
import dev.sebastiano.headroom.signin.ServiceSignInState
import dev.sebastiano.headroom.ui.components.CursorConnect
import dev.sebastiano.headroom.ui.components.CursorPromptBanner
import dev.sebastiano.headroom.ui.components.CursorPromptRow
import dev.sebastiano.headroom.ui.home.AccountSummary
import dev.sebastiano.headroom.ui.home.homeUiState
import dev.sebastiano.headroom.ui.overview.OVERVIEW_LIST_TAG
import dev.sebastiano.headroom.ui.overview.accountCardTag
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

/** What each Cursor prompt says and does: connect, try the read again, or say a sign-in failed. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class CursorPromptActionTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val now = Instant.parse("2026-10-18T12:00:00Z")
    private var retries = 0
    private var saveFailure: Exception? = null
    private val sessions = mutableListOf<FakeSession>()
    private val signIn = signIn()

    private fun signIn(scope: CoroutineScope = CoroutineScope(Dispatchers.Unconfined)) =
        ServiceSignIn(
            start = { FakeSession().also { sessions += it } },
            save = { _, _ -> saveFailure?.let { throw it } },
            scope = scope,
            label = "Cursor",
        )

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

        assertEquals(1, sessions.size)
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
        assertEquals(0, sessions.size)
        assertIs<ServiceSignInState.Idle>(signIn.state.value)
    }

    @Test
    fun `a sign-in that failed says why, and its button starts it again`() {
        val summary = grokSummary(CursorPrompt.NotLinked)
        rule.setContent {
            HeadroomTheme(dynamicColor = false) {
                CursorPromptRow(summary = summary, connect = connect)
            }
        }
        signIn.start(summary.id)
        sessions.single().result.completeExceptionally(AuthException.Network("offline"))
        rule.waitForIdle()

        // Without this the prompt fell back to its ordinary body, so a refused or offline sign-in
        // looked exactly like one the user had never tried.
        rule.onNodeWithText("There was no connection. Check it and try again.").assertIsDisplayed()

        rule.onNodeWithText("Connect").performClick()
        rule.waitForIdle()
        assertEquals(2, sessions.size)
    }

    @Test
    fun `a sign-in for another account names the account that already has it or the expected one`() {
        val summary = grokSummary(CursorPrompt.NotLinked)
        rule.setContent {
            HeadroomTheme(dynamicColor = false) {
                CursorPromptRow(summary = summary, connect = connect)
            }
        }
        val tokens = TokenSet(Provider.Grok, CredentialKind.OAuth, "access", "refresh", null)

        saveFailure = DifferentAccountException(summary.id, otherAccount = "Work")
        signIn.start(summary.id)
        sessions.last().result.complete(tokens)
        rule.waitForIdle()
        rule
            .onNodeWithText(
                "That Cursor account is already connected to Work. Sign in to the Cursor account for this one."
            )
            .assertIsDisplayed()

        saveFailure = DifferentAccountException(summary.id, expectedAccount = "ada@cursor.com")
        signIn.start(summary.id)
        sessions.last().result.complete(tokens)
        rule.waitForIdle()
        rule
            .onNodeWithText(
                "You signed in to a different account. Sign in as ada@cursor.com to update this one."
            )
            .assertIsDisplayed()
    }

    @Test
    fun `the account's own prompt is for connecting, not for waving away`() {
        val summary = grokSummary(CursorPrompt.NotLinked)
        rule.setContent {
            HeadroomTheme(dynamicColor = false) {
                CursorPromptBanner(summary = summary, connect = connect)
            }
        }
        rule.waitForIdle()

        // The card's nudge is the user's to wave away; the account's prompt is where a waved-away
        // card leaves them, so a close button there could only ever do nothing.
        rule.onNodeWithContentDescription("Not now").assertDoesNotExist()
        rule.onNodeWithText("Connect").assertIsDisplayed()
    }

    @Test
    fun `the card's nudge can be waved away`() {
        val summary = grokSummary(CursorPrompt.NotLinked)
        var dismissed: String? = null
        rule.setContent {
            HeadroomTheme(dynamicColor = false) {
                CursorPromptRow(
                    summary = summary,
                    connect = CursorConnect(signIn, onDismiss = { dismissed = it }),
                )
            }
        }
        rule.waitForIdle()

        rule.onNodeWithContentDescription("Not now").performClick()

        assertEquals(summary.id, dismissed)
    }

    @Test
    fun `a card whose sign-in expired does not offer the Cursor connection`() {
        val states =
            DemoData.accounts(now).map { state ->
                if (state.account.provider != Provider.Grok) state
                else
                    state.copy(
                        lastError = QuotaErrorKind.Auth,
                        snapshot = state.snapshot!!.copy(cursorLinked = false),
                    )
            }
        val grok = states.first { it.account.provider == Provider.Grok }
        rule.setContent {
            HeadroomTheme(dynamicColor = false) {
                HeadroomApp(graph = testGraph(rule.activity, realAccounts = states))
            }
        }
        rule.waitForIdle()

        // Connecting first could not save anything: a refresh with the expired sign-in fails, so
        // the account would keep asking to connect. The detail screen already waited its turn.
        rule
            .onNodeWithTag(OVERVIEW_LIST_TAG)
            .performScrollToNode(hasTestTag(accountCardTag(grok.account.id)))
        rule
            .onNodeWithTag(accountCardTag(grok.account.id))
            .assert(hasContentDescription("Sign-in expired", substring = true))
        rule.onNodeWithText("Connect").assertDoesNotExist()
    }

    /** A sign-in whose browser page the user has not finished, or whose flow the test ends. */
    private class FakeSession : DeviceSession {
        override val userCode: String = ""
        override val verificationUrl: String = "https://cursor.com/loginDeepControl"
        val result = CompletableDeferred<TokenSet>()

        override suspend fun awaitTokens(): TokenSet = result.await()
    }
}
