package dev.sebastiano.headroom.signin

import dev.sebastiano.headroom.auth.AuthException
import dev.sebastiano.headroom.auth.CredentialKind
import dev.sebastiano.headroom.auth.TokenSet
import dev.sebastiano.headroom.model.Provider
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ZCodeSignInTest {
    private val tokens = TokenSet(Provider.ZAi, CredentialKind.OAuth, "business", "bundle", null)

    private class FakeSession(override val verificationUrl: String = "https://chat.z.ai/a") :
        DeviceSession {
        override val userCode: String = ""
        val result = CompletableDeferred<TokenSet>()

        override suspend fun awaitTokens(): TokenSet = result.await()
    }

    @Test
    fun `opens the sign-in page, then returns to the app and saves the tokens`() =
        runTest(UnconfinedTestDispatcher()) {
            val session = FakeSession()
            val events = mutableListOf<String>()
            val signIn =
                ZCodeSignIn(
                    start = { session },
                    save = { id, saved ->
                        assertEquals(tokens, saved)
                        events += "save $id"
                    },
                    scope = backgroundScope,
                    returnToApp = { events += "return" },
                )

            signIn.start("z1")
            assertEquals(ZCodeSignInState.Waiting("z1", "https://chat.z.ai/a"), signIn.state.value)
            assertTrue(events.isEmpty())

            session.result.complete(tokens)
            assertEquals(ZCodeSignInState.Done("z1"), signIn.state.value)
            // The browser tab gives way to the app as soon as the sign-in is through.
            assertEquals(listOf("return", "save z1"), events)
        }

    @Test
    fun `a failure says why`() =
        runTest(UnconfinedTestDispatcher()) {
            val cases =
                listOf<Pair<Throwable, SignInError>>(
                    AuthException.TimedOut("late") to SignInError.Expired,
                    AuthException.Network("offline") to SignInError.Network,
                    AuthException.SignInFailed("refused") to SignInError.Denied,
                    AuthException.InvalidResponse("odd") to SignInError.Unknown,
                    IOException("disk") to SignInError.Unknown,
                )
            cases.forEach { (failure, error) ->
                val session = FakeSession()
                val signIn =
                    ZCodeSignIn(
                        start = { session },
                        save = { _, _ -> },
                        scope = backgroundScope,
                    )
                signIn.start("z1")
                session.result.completeExceptionally(failure)
                assertEquals(ZCodeSignInState.Failed("z1", error), signIn.state.value)
            }
        }

    @Test
    fun `a new start replaces the one before, and cancel forgets it`() =
        runTest(UnconfinedTestDispatcher()) {
            val first = FakeSession("https://chat.z.ai/1")
            val second = FakeSession("https://chat.z.ai/2")
            val sessions = ArrayDeque(listOf(first, second))
            val saved = mutableListOf<String>()
            val signIn =
                ZCodeSignIn(
                    start = { sessions.removeFirst() },
                    save = { id, _ -> saved += id },
                    scope = backgroundScope,
                )

            signIn.start("z1")
            signIn.start("z2")
            first.result.complete(tokens)
            assertEquals(ZCodeSignInState.Waiting("z2", "https://chat.z.ai/2"), signIn.state.value)

            signIn.cancel()
            second.result.complete(tokens)
            assertEquals(ZCodeSignInState.Idle, signIn.state.value)
            assertTrue(saved.isEmpty())
        }

    @Test
    fun `starting again while waiting reopens the same page instead of a new flow`() =
        runTest(UnconfinedTestDispatcher()) {
            val session = FakeSession()
            var starts = 0
            val signIn =
                ZCodeSignIn(
                    start = {
                        starts++
                        session
                    },
                    save = { _, _ -> },
                    scope = backgroundScope,
                )

            signIn.start("z1")
            val first = signIn.state.value
            signIn.start("z1")

            assertEquals(1, starts)
            val again = signIn.state.value as ZCodeSignInState.Waiting
            assertEquals("https://chat.z.ai/a", again.url)
            // A new value, so the page opens again.
            assertTrue(again != first)

            session.result.complete(tokens)
            assertEquals(ZCodeSignInState.Done("z1"), signIn.state.value)
        }

    @Test
    fun `logs each step and why it failed, without tokens`() =
        runTest(UnconfinedTestDispatcher()) {
            val lines = mutableListOf<String>()
            val session = FakeSession()
            val signIn =
                ZCodeSignIn(
                    start = { session },
                    save = { _, _ -> },
                    scope = backgroundScope,
                    log = { lines += it },
                )

            signIn.start("z1")
            session.result.completeExceptionally(AuthException.SignInFailed("refused (HTTP 403)"))

            assertTrue(lines.toString(), lines.any { "waiting" in it })
            assertTrue(lines.toString(), lines.any { "SignInFailed" in it && "HTTP 403" in it })
            assertTrue(lines.none { "business" in it || "bundle" in it })
        }
}
