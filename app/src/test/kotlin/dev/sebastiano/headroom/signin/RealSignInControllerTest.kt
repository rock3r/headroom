package dev.sebastiano.headroom.signin

import dev.sebastiano.headroom.auth.AuthException
import dev.sebastiano.headroom.auth.CredentialKind
import dev.sebastiano.headroom.auth.TokenSet
import dev.sebastiano.headroom.model.Account
import dev.sebastiano.headroom.model.Provider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RealSignInControllerTest {
    private fun tokens(provider: Provider) =
        TokenSet(
            provider,
            CredentialKind.OAuth,
            "access",
            "refresh",
            null,
            label = "sam@example.com",
        )

    private class FakeBrowser(override val authorizeUrl: String) : BrowserSession {
        val result = CompletableDeferred<TokenSet>()
        val pasted = mutableListOf<String>()
        var closed = false

        override fun submitPastedCode(code: String) {
            if (!code.contains("#")) throw AuthException.SignInFailed("bad code")
            pasted += code
        }

        override suspend fun awaitTokens(): TokenSet = result.await()

        override fun close() {
            closed = true
        }
    }

    private class FakeDevice(override val userCode: String, override val verificationUrl: String) :
        DeviceSession {
        val result = CompletableDeferred<TokenSet>()

        override suspend fun awaitTokens(): TokenSet = result.await()
    }

    private class FakeSteps : SignInSteps {
        var browser: FakeBrowser? = null
        var device: FakeDevice? = null

        override fun kindOf(provider: Provider) =
            when (provider) {
                Provider.Claude -> SignInKind.Browser
                Provider.Codex -> SignInKind.DeviceCode
                else -> SignInKind.ApiKey
            }

        override suspend fun startBrowser(provider: Provider) =
            FakeBrowser("https://example.com/authorize").also { browser = it }

        override suspend fun startDeviceCode(provider: Provider) =
            FakeDevice("ABCD-1234", "https://example.com/device").also { device = it }

        override fun apiKeyTokens(provider: Provider, key: String): TokenSet {
            if (key.isBlank()) throw AuthException.SignInFailed("Enter an API key")
            return TokenSet(provider, CredentialKind.ApiKey, key, null, null)
        }
    }

    private fun TestScope.controller(
        steps: FakeSteps,
        completed: MutableList<TokenSet> = mutableListOf(),
    ) =
        RealSignInController(steps, backgroundScope) { tokens ->
            completed += tokens
            Account("id", tokens.provider, tokens.label ?: tokens.provider.displayName)
        }

    @Test
    fun `browser sign-in shows the authorize page, then succeeds`() =
        runTest(UnconfinedTestDispatcher()) {
            val steps = FakeSteps()
            val completed = mutableListOf<TokenSet>()
            val controller = controller(steps, completed)
            controller.start(Provider.Claude)
            assertEquals(
                SignInState.Browser(Provider.Claude, "https://example.com/authorize"),
                controller.state.value,
            )
            steps.browser!!.result.complete(tokens(Provider.Claude))
            assertEquals(
                SignInState.Success(Provider.Claude, "sam@example.com"),
                controller.state.value,
            )
            assertEquals(1, completed.size)
            assertTrue(steps.browser!!.closed)
        }

    @Test
    fun `a bad pasted code is flagged and the sign-in keeps waiting`() =
        runTest(UnconfinedTestDispatcher()) {
            val steps = FakeSteps()
            val controller = controller(steps)
            controller.start(Provider.Claude)
            controller.submitCode("nonsense")
            assertEquals(true, (controller.state.value as SignInState.Browser).codeRejected)
            controller.submitCode("code#state")
            assertEquals(listOf("code#state"), steps.browser!!.pasted)
        }

    @Test
    fun `device code sign-in shows the code, then succeeds`() =
        runTest(UnconfinedTestDispatcher()) {
            val steps = FakeSteps()
            val controller = controller(steps)
            controller.start(Provider.Codex)
            assertEquals(
                SignInState.DeviceCode(Provider.Codex, "ABCD-1234", "https://example.com/device"),
                controller.state.value,
            )
            steps.device!!.result.complete(tokens(Provider.Codex))
            assertEquals(
                SignInState.Success(Provider.Codex, "sam@example.com"),
                controller.state.value,
            )
        }

    @Test
    fun `an empty API key is rejected, a good one signs in`() =
        runTest(UnconfinedTestDispatcher()) {
            val controller = controller(FakeSteps())
            controller.start(Provider.ZAi)
            assertEquals(SignInState.ApiKey(Provider.ZAi), controller.state.value)
            controller.submitApiKey(" ")
            assertEquals(
                SignInState.ApiKey(Provider.ZAi, keyRejected = true),
                controller.state.value,
            )
            controller.submitApiKey("key-123")
            assertEquals(SignInState.Success(Provider.ZAi, "Z.AI"), controller.state.value)
        }

    @Test
    fun `errors map to what the user can act on`() =
        runTest(UnconfinedTestDispatcher()) {
            val steps = FakeSteps()
            val controller = controller(steps)
            controller.start(Provider.Claude)
            steps.browser!!
                .result
                .completeExceptionally(AuthException.Rejected(400, "access_denied", "denied"))
            assertEquals(
                SignInState.Failed(Provider.Claude, SignInError.Denied),
                controller.state.value,
            )

            controller.retry()
            steps.browser!!.result.completeExceptionally(AuthException.TimedOut("slow"))
            assertEquals(
                SignInState.Failed(Provider.Claude, SignInError.Expired),
                controller.state.value,
            )

            controller.retry()
            steps.browser!!.result.completeExceptionally(AuthException.Network("offline"))
            assertEquals(
                SignInState.Failed(Provider.Claude, SignInError.Network),
                controller.state.value,
            )
        }

    @Test
    fun `cancel closes the browser session and goes back to idle`() =
        runTest(UnconfinedTestDispatcher()) {
            val steps = FakeSteps()
            val controller = controller(steps)
            controller.start(Provider.Claude)
            controller.cancel()
            assertEquals(SignInState.Idle, controller.state.value)
            assertTrue(steps.browser!!.closed)
        }
}
