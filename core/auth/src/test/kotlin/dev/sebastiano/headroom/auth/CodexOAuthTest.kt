package dev.sebastiano.headroom.auth

import dev.sebastiano.headroom.model.Provider
import java.net.ServerSocket
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer

class CodexOAuthTest {
    private val now = Instant.parse("2026-09-27T12:00:00Z")
    private val server = MockWebServer()
    private val io = testIoDispatcher()
    private val port = ServerSocket(0).use { it.localPort }
    private lateinit var codex: CodexOAuth

    private val idToken =
        fakeJwt(
            """{"email":"sam@example.com","https://api.openai.com/auth":{"chatgpt_account_id":"acct-123"}}"""
        )

    @BeforeTest
    fun setUp() {
        server.start()
        codex =
            CodexOAuth(
                http = OkHttpAuthHttpClient(),
                clock = fixedClock(now),
                tokenEndpoint = server.url("/oauth/token").toString(),
                callbackPort = port,
            )
    }

    @AfterTest
    fun tearDown() {
        server.close()
        io.close()
    }

    private fun formOf(body: String?) = queryPairs("?" + checkNotNull(body))

    @Test
    fun `the default listener is the fixed Codex callback port on both loopback families`() {
        val config = CodexOAuth(OkHttpAuthHttpClient(), Clock.System).loopback
        assertEquals(1455..1455, config.ports)
        assertEquals("/auth/callback", config.path)
        assertTrue(config.bindIpv6)
    }

    @Test
    fun `the redirect URI uses the localhost host that OpenAI allows`() {
        val codex = CodexOAuth(OkHttpAuthHttpClient(), Clock.System)
        assertEquals("http://localhost:1455/auth/callback", codex.loopbackRedirectUri(1455))
    }

    @Test
    fun `the authorize URL carries the Codex client and flow parameters`() = runTest {
        BrowserOAuthFlow(codex, io).start(null).use { signIn ->
            val params = queryPairs(signIn.authorizeUrl)
            assertTrue(signIn.authorizeUrl.startsWith("https://auth.openai.com/oauth/authorize?"))
            assertEquals(
                listOf(
                    "response_type",
                    "client_id",
                    "redirect_uri",
                    "scope",
                    "code_challenge",
                    "code_challenge_method",
                    "state",
                    "id_token_add_organizations",
                    "codex_cli_simplified_flow",
                    "originator",
                ),
                params.map { it.first },
            )
            val values = params.toMap()
            assertEquals("code", values["response_type"])
            assertEquals("app_EMoamEEZ73f0CkXaXp7hrann", values["client_id"])
            assertEquals("http://localhost:$port/auth/callback", values["redirect_uri"])
            assertEquals("openid profile email offline_access", values["scope"])
            assertEquals("S256", values["code_challenge_method"])
            assertEquals("true", values["id_token_add_organizations"])
            assertEquals("true", values["codex_cli_simplified_flow"])
            assertEquals("codex_cli_rs", values["originator"])
            assertTrue(Regex("^[0-9a-f]{32}$").matches(values.getValue("state")))
            assertNull(signIn.manualAuthorizeUrl)
        }
    }

    @Test
    fun `a redirect is exchanged with a form body and yields the account id`() = runTest {
        server.enqueue(
            MockResponse(
                code = 200,
                body =
                    """{"access_token":"ca","refresh_token":"cr","id_token":"$idToken","expires_in":864000}""",
            )
        )
        val signIn = BrowserOAuthFlow(codex, io).start(null)
        val state = queryPairs(signIn.authorizeUrl).toMap().getValue("state")

        val browser = async {
            browserGet(io, "http://127.0.0.1:$port/auth/callback?code=auth-code&state=$state")
        }
        val tokens = signIn.awaitTokens()

        val request = server.takeRequest()
        assertEquals("/oauth/token", request.url.encodedPath)
        assertEquals("application/x-www-form-urlencoded", request.headers["Content-Type"])
        val form = formOf(request.body?.utf8())
        assertEquals(
            listOf("grant_type", "client_id", "code", "code_verifier", "redirect_uri"),
            form.map { it.first },
        )
        val values = form.toMap()
        assertEquals("authorization_code", values["grant_type"])
        assertEquals("app_EMoamEEZ73f0CkXaXp7hrann", values["client_id"])
        assertEquals("auth-code", values["code"])
        assertEquals("http://localhost:$port/auth/callback", values["redirect_uri"])
        assertTrue(values.getValue("code_verifier").length >= 43)

        assertEquals(Provider.Codex, tokens.provider)
        assertEquals("ca", tokens.accessToken)
        assertEquals("cr", tokens.refreshToken)
        assertEquals(now.plus(10.days), tokens.expiresAt)
        assertEquals("acct-123", tokens.extras[CredentialExtras.CHATGPT_ACCOUNT_ID])
        assertEquals("acct-123", tokens.providerAccountId)
        assertEquals("sam@example.com", tokens.label)
        assertEquals(200, browser.await().status)
    }

    @Test
    fun `the account id falls back to the access token claim`() = runTest {
        val access = fakeJwt("""{"https://api.openai.com/auth":{"chatgpt_account_id":"acct-9"}}""")
        server.enqueue(
            MockResponse(code = 200, body = """{"access_token":"$access","refresh_token":"r"}""")
        )

        val tokens = codex.exchange("a", codex.loopbackRedirectUri(port), Pkce.generate(), "s")

        assertEquals("acct-9", tokens.extras[CredentialExtras.CHATGPT_ACCOUNT_ID])
        assertEquals(now + 3600.seconds, tokens.expiresAt)
    }

    @Test
    fun `a sign-in without a ChatGPT account id is refused`() = runTest {
        server.enqueue(
            MockResponse(code = 200, body = """{"access_token":"plain","refresh_token":"r"}""")
        )

        assertFailsWith<AuthException.InvalidResponse> {
            codex.exchange("a", codex.loopbackRedirectUri(port), Pkce.generate(), "s")
        }
    }

    @Test
    fun `refresh posts a form and reads the new account id`() = runTest {
        server.enqueue(
            MockResponse(
                code = 200,
                body =
                    """{"access_token":"new","refresh_token":"new-r","id_token":"$idToken","expires_in":100}""",
            )
        )
        val old =
            TokenSet(Provider.Codex, CredentialKind.OAuth, "old", "old-r", now).toCredential("c")

        val tokens = codex.refresh(old)

        assertEquals(
            listOf(
                "grant_type" to "refresh_token",
                "refresh_token" to "old-r",
                "client_id" to "app_EMoamEEZ73f0CkXaXp7hrann",
            ),
            queryPairs("?" + checkNotNull(server.takeRequest().body).utf8()),
        )
        assertEquals("new", tokens.accessToken)
        assertEquals("new-r", tokens.refreshToken)
        assertEquals(now + 100.seconds, tokens.expiresAt)
        assertEquals("acct-123", tokens.extras[CredentialExtras.CHATGPT_ACCOUNT_ID])
    }

    @Test
    fun `refresh keeps the stored account id when the new tokens carry none`() = runTest {
        server.enqueue(MockResponse(code = 200, body = """{"access_token":"plain"}"""))
        val old =
            TokenSet(
                    Provider.Codex,
                    CredentialKind.OAuth,
                    "old",
                    "old-r",
                    now,
                    extras = mapOf(CredentialExtras.CHATGPT_ACCOUNT_ID to "acct-old"),
                )
                .toCredential("c")

        val refreshed = old.refreshedWith(codex.refresh(old))

        assertEquals("acct-old", refreshed.chatGptAccountId)
        assertEquals("old-r", refreshed.refreshToken)
    }
}
