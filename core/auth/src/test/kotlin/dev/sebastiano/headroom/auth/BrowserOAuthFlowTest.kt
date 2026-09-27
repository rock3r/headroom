package dev.sebastiano.headroom.auth

import dev.sebastiano.headroom.model.Provider
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest

class BrowserOAuthFlowTest {
    private val io = testIoDispatcher()

    @AfterTest
    fun tearDown() {
        io.close()
    }

    private class FakeSpec(
        override val manualRedirectUri: String? = "https://example.com/manual",
        override val pasteRequiresState: Boolean = false,
        val failExchange: AuthException? = null,
    ) : BrowserOAuthSpec {
        val exchanges = mutableListOf<Pair<String, String>>()
        val exchangeGate = CompletableDeferred<Unit>().apply { complete(Unit) }
        override val provider = Provider.Claude
        override val loopback = LoopbackConfig()

        override fun loopbackRedirectUri(port: Int) = "http://localhost:$port/callback"

        override fun authorizeUrl(redirectUri: String, pkce: Pkce, state: String) =
            "https://example.com/authorize?redirect_uri=$redirectUri&state=$state"

        override suspend fun exchange(
            code: String,
            redirectUri: String,
            pkce: Pkce,
            state: String,
        ): TokenSet {
            exchangeGate.await()
            exchanges += code to redirectUri
            failExchange?.let { throw it }
            return TokenSet(Provider.Claude, CredentialKind.OAuth, "access-$code", "r", null)
        }
    }

    private fun stateOf(signIn: BrowserSignIn) =
        queryPairs(signIn.authorizeUrl).toMap().getValue("state")

    private fun portOf(signIn: BrowserSignIn) =
        queryPairs(signIn.authorizeUrl)
            .toMap()
            .getValue("redirect_uri")
            .substringAfter("localhost:")
            .substringBefore('/')
            .toInt()

    @Test
    fun `the redirect is exchanged with the loopback redirect URI`() = runTest {
        val spec = FakeSpec()
        val signIn = BrowserOAuthFlow(spec, io).start("headroom://signed-in")
        val port = portOf(signIn)

        val browser = async {
            browserGet(io, "http://127.0.0.1:$port/callback?code=c1&state=${stateOf(signIn)}")
        }
        val tokens = signIn.awaitTokens()

        assertEquals("access-c1", tokens.accessToken)
        assertEquals(listOf("c1" to "http://localhost:$port/callback"), spec.exchanges)
        val reply = browser.await()
        assertEquals(200, reply.status)
        assertContains(reply.body, "headroom://signed-in")
    }

    @Test
    fun `the browser waits until the exchange is done`() = runTest {
        val spec = FakeSpec()
        val gate = CompletableDeferred<Unit>()
        val slow =
            object : BrowserOAuthSpec by spec {
                override suspend fun exchange(
                    code: String,
                    redirectUri: String,
                    pkce: Pkce,
                    state: String,
                ): TokenSet {
                    gate.await()
                    return spec.exchange(code, redirectUri, pkce, state)
                }
            }
        val signIn = BrowserOAuthFlow(slow, io).start(null)
        val browser = async {
            browserGet(
                io,
                "http://127.0.0.1:${portOf(signIn)}/callback?code=c&state=${stateOf(signIn)}",
            )
        }
        val tokens = async { signIn.awaitTokens() }

        delay(200)
        assertFalse(browser.isCompleted)
        gate.complete(Unit)
        tokens.await()
        assertEquals(200, browser.await().status)
    }

    @Test
    fun `a failed exchange shows a failure page and is rethrown`() = runTest {
        val failure = AuthException.Rejected(400, "invalid_grant", "bad code")
        val signIn = BrowserOAuthFlow(FakeSpec(failExchange = failure), io).start("headroom://x")
        val browser = async {
            browserGet(
                io,
                "http://127.0.0.1:${portOf(signIn)}/callback?code=c&state=${stateOf(signIn)}",
            )
        }

        assertFailsWith<AuthException.Rejected> { signIn.awaitTokens() }

        val reply = browser.await()
        assertEquals(400, reply.status)
        assertContains(reply.body, "Sign-in failed")
        assertContains(reply.body, "headroom://x")
    }

    @Test
    fun `a pasted code is exchanged with the manual redirect URI and frees the port`() = runTest {
        val spec = FakeSpec()
        val signIn = BrowserOAuthFlow(spec, io).start(null)
        val port = portOf(signIn)

        signIn.submitPastedCode("pasted#${stateOf(signIn)}")
        val tokens = signIn.awaitTokens()

        assertEquals("access-pasted", tokens.accessToken)
        assertEquals(listOf("pasted" to "https://example.com/manual"), spec.exchanges)
        ServerSocket(port, 1, InetAddress.getByName("127.0.0.1")).close()
    }

    @Test
    fun `without a manual redirect a pasted URL uses the loopback redirect URI`() = runTest {
        val spec = FakeSpec(manualRedirectUri = null)
        val signIn = BrowserOAuthFlow(spec, io).start(null)
        val port = portOf(signIn)
        assertNull(signIn.manualAuthorizeUrl)

        signIn.submitPastedCode(
            "http://127.0.0.1:$port/callback?code=from-url&state=${stateOf(signIn)}"
        )
        signIn.awaitTokens()

        assertEquals(listOf("from-url" to "http://localhost:$port/callback"), spec.exchanges)
    }

    @Test
    fun `a pasted loopback URL is exchanged with the loopback redirect URI`() = runTest {
        listOf("localhost", "127.0.0.1").forEach { host ->
            val spec = FakeSpec()
            val signIn = BrowserOAuthFlow(spec, io).start(null)
            val port = portOf(signIn)

            signIn.submitPastedCode("http://$host:$port/callback?code=c&state=${stateOf(signIn)}")
            signIn.awaitTokens()

            assertEquals(listOf("c" to "http://localhost:$port/callback"), spec.exchanges)
        }
    }

    @Test
    fun `a pasted URL from the manual page keeps the manual redirect URI`() = runTest {
        val spec = FakeSpec()
        val signIn = BrowserOAuthFlow(spec, io).start(null)

        signIn.submitPastedCode("https://example.com/manual?code=m&state=${stateOf(signIn)}")
        signIn.awaitTokens()

        assertEquals(listOf("m" to "https://example.com/manual"), spec.exchanges)
    }

    @Test
    fun `cancelling during the exchange releases the browser connection`() = runTest {
        val entered = CompletableDeferred<Unit>()
        val spec = FakeSpec()
        val stuck =
            object : BrowserOAuthSpec by spec {
                override suspend fun exchange(
                    code: String,
                    redirectUri: String,
                    pkce: Pkce,
                    state: String,
                ): TokenSet {
                    entered.complete(Unit)
                    awaitCancellation()
                }
            }
        val signIn = BrowserOAuthFlow(stuck, io).start(null)
        val url = "http://127.0.0.1:${portOf(signIn)}/callback?code=c&state=${stateOf(signIn)}"
        val browser = async { runCatching { browserGet(io, url) } }
        val tokens = async { signIn.awaitTokens() }

        entered.await()
        val started = System.nanoTime()
        tokens.cancelAndJoin()

        val reply = browser.await()
        val elapsedMillis = (System.nanoTime() - started) / 1_000_000
        assertTrue(reply.isFailure, "the browser got $reply")
        assertTrue(elapsedMillis < 5_000, "the browser waited $elapsedMillis ms")
    }

    @Test
    fun `a pasted code for another sign-in is refused and the sign-in keeps waiting`() = runTest {
        val spec = FakeSpec(pasteRequiresState = true)
        val signIn = BrowserOAuthFlow(spec, io).start(null)

        assertFailsWith<AuthException.SignInFailed> { signIn.submitPastedCode("c#other") }
        assertFailsWith<AuthException.SignInFailed> { signIn.submitPastedCode("c") }
        assertFailsWith<AuthException.SignInFailed> { signIn.submitPastedCode("   ") }

        signIn.submitPastedCode("good#${stateOf(signIn)}")
        assertEquals("access-good", signIn.awaitTokens().accessToken)
    }

    @Test
    fun `pasted text is read as code and state, a URL, or a bare code`() {
        PastedCode.parse(" abc#xyz ").let {
            assertEquals("abc", it.code)
            assertEquals("xyz", it.state)
        }
        PastedCode.parse("https://x/cb?code=a%2Bb&state=s").let {
            assertEquals("a+b", it.code)
            assertEquals("s", it.state)
        }
        PastedCode.parse("http://127.0.0.1:1/callback?error=access_denied").let {
            assertEquals("access_denied", it.error)
            assertNull(it.code)
        }
        PastedCode.parse("bare").let {
            assertEquals("bare", it.code)
            assertNull(it.state)
        }
    }
}
