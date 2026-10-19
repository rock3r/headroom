package dev.sebastiano.headroom.signin

import dev.sebastiano.headroom.auth.AuthException
import dev.sebastiano.headroom.auth.CredentialKind
import dev.sebastiano.headroom.auth.TokenSet
import dev.sebastiano.headroom.data.account.DifferentAccountException
import dev.sebastiano.headroom.model.Provider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class ServiceSignInTest {
    private val tokens = TokenSet(Provider.ZAi, CredentialKind.OAuth, "business", "bundle", null)

    private class FakeSession(override val verificationUrl: String = "https://chat.z.ai/a") :
        DeviceSession {
        override val userCode: String = ""
        val result = CompletableDeferred<TokenSet>()

        override suspend fun awaitTokens(): TokenSet = result.await()
    }

    @Test
    fun `opens the sign-in page then returns to the app and saves the tokens`() =
        runTest(UnconfinedTestDispatcher()) {
            val session = FakeSession()
            val events = mutableListOf<String>()
            val signIn =
                ServiceSignIn(
                    start = { session },
                    save = { id, saved ->
                        assertEquals(tokens, saved)
                        events += "save $id"
                    },
                    scope = backgroundScope,
                    returnToApp = { events += "return" },
                )

            signIn.start("z1")
            assertEquals(
                ServiceSignInState.Waiting("z1", "https://chat.z.ai/a"),
                signIn.state.value,
            )
            assertTrue(events.isEmpty())

            session.result.complete(tokens)
            assertEquals(ServiceSignInState.Done("z1"), signIn.state.value)
            // The browser tab gives way to the app as soon as the sign-in is through.
            assertEquals(listOf("return", "save z1"), events)
        }

    @Test
    fun `asking again for the page the sign-in waits on opens it again`() =
        runTest(UnconfinedTestDispatcher()) {
            val session = FakeSession()
            val signIn =
                ServiceSignIn(
                    start = { session },
                    save = { _, _ -> },
                    scope = backgroundScope,
                )

            signIn.start("z1")
            assertEquals(
                ServiceSignInState.Waiting("z1", "https://chat.z.ai/a"),
                signIn.state.value,
            )

            // Asking again opens the same page, which the user may have closed.
            signIn.start("z1")
            assertEquals(
                ServiceSignInState.Waiting("z1", "https://chat.z.ai/a", opened = 2),
                signIn.state.value,
            )
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
                    ServiceSignIn(
                        start = { session },
                        save = { _, _ -> },
                        scope = backgroundScope,
                    )
                signIn.start("z1")
                session.result.completeExceptionally(failure)
                assertEquals(ServiceSignInState.Failed("z1", error), signIn.state.value)
            }

            val session = FakeSession()
            val signIn =
                ServiceSignIn(
                    start = { session },
                    save = { id, _ ->
                        throw DifferentAccountException(
                            accountId = id,
                            otherAccount = "Work",
                            expectedAccount = "ada@example.com",
                        )
                    },
                    scope = backgroundScope,
                )
            signIn.start("z1")
            session.result.complete(tokens)
            assertEquals(
                ServiceSignInState.Failed(
                    accountId = "z1",
                    error = SignInError.DifferentAccount,
                    otherAccount = "Work",
                    expectedAccount = "ada@example.com",
                ),
                signIn.state.value,
            )
        }

    @Test
    fun `a new start replaces the one before and cancel forgets it`() =
        runTest(UnconfinedTestDispatcher()) {
            val first = FakeSession("https://chat.z.ai/1")
            val second = FakeSession("https://chat.z.ai/2")
            val sessions = ArrayDeque(listOf(first, second))
            val saved = mutableListOf<String>()
            val signIn =
                ServiceSignIn(
                    start = { sessions.removeFirst() },
                    save = { id, _ -> saved += id },
                    scope = backgroundScope,
                )

            signIn.start("z1")
            signIn.start("z2")
            first.result.complete(tokens)
            assertEquals(
                ServiceSignInState.Waiting("z2", "https://chat.z.ai/2"),
                signIn.state.value,
            )

            signIn.cancel()
            second.result.complete(tokens)
            assertEquals(ServiceSignInState.Idle, signIn.state.value)
            assertTrue(saved.isEmpty())
        }

    @Test
    fun `starting again while waiting reopens the same page instead of a new flow`() =
        runTest(UnconfinedTestDispatcher()) {
            val session = FakeSession()
            var starts = 0
            val signIn =
                ServiceSignIn(
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
            val again = signIn.state.value as ServiceSignInState.Waiting
            assertEquals("https://chat.z.ai/a", again.url)
            // A new value, so the page opens again.
            assertTrue(again != first)

            session.result.complete(tokens)
            assertEquals(ServiceSignInState.Done("z1"), signIn.state.value)
        }

    @Test
    fun `a prepared page opens at once when the sign-in starts`() =
        runTest(UnconfinedTestDispatcher()) {
            val session = FakeSession()
            var starts = 0
            val signIn =
                ServiceSignIn(
                    start = {
                        starts++
                        session
                    },
                    save = { _, _ -> },
                    scope = backgroundScope,
                )

            signIn.prepare("z1")

            // The page is readied before the tap, and nothing shows yet.
            assertEquals(1, starts)
            assertEquals(ServiceSignInState.Idle, signIn.state.value)

            signIn.start("z1")

            // No second round trip: the page opens at once.
            assertEquals(1, starts)
            assertEquals(
                ServiceSignInState.Waiting("z1", "https://chat.z.ai/a"),
                signIn.state.value,
            )
        }

    @Test
    fun `a page whose prepare failed starts again on tap`() =
        runTest(UnconfinedTestDispatcher()) {
            val session = FakeSession()
            var failures = 1
            val signIn =
                ServiceSignIn(
                    start = {
                        if (failures-- > 0) throw AuthException.Network("offline") else session
                    },
                    save = { _, _ -> },
                    scope = backgroundScope,
                )

            signIn.prepare("z1")
            signIn.start("z1")

            assertEquals(
                ServiceSignInState.Waiting("z1", "https://chat.z.ai/a"),
                signIn.state.value,
            )
        }

    @Test
    fun `a forgotten prepare readies a new page on the next ask`() =
        runTest(UnconfinedTestDispatcher()) {
            var starts = 0
            val signIn =
                ServiceSignIn(
                    start = {
                        starts++
                        FakeSession("https://chat.z.ai/$starts")
                    },
                    save = { _, _ -> },
                    scope = backgroundScope,
                )

            signIn.prepare("z1")
            signIn.forgetPrepared("z1")
            signIn.start("z1")

            // The forgotten page is not reused: a ZCode page expires a few minutes after it is
            // made, so a stale one would fail the moment it is used.
            assertEquals(2, starts)
            assertEquals(
                ServiceSignInState.Waiting("z1", "https://chat.z.ai/2"),
                signIn.state.value,
            )
        }

    @Test
    fun `logs each step and why it failed without tokens`() =
        runTest(UnconfinedTestDispatcher()) {
            val lines = mutableListOf<String>()
            val session = FakeSession()
            val signIn =
                ServiceSignIn(
                    start = { session },
                    save = { _, _ -> },
                    scope = backgroundScope,
                    log = { lines += it },
                )

            signIn.start("z1")
            session.result.completeExceptionally(AuthException.SignInFailed("refused (HTTP 403)"))

            assertTrue(lines.any { "waiting" in it }, lines.toString())
            assertTrue(lines.any { "SignInFailed" in it && "HTTP 403" in it }, lines.toString())
            assertTrue(lines.none { "business" in it || "bundle" in it })
        }
}
