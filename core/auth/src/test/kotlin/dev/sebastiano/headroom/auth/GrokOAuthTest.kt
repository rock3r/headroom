package dev.sebastiano.headroom.auth

import dev.sebastiano.headroom.model.Provider
import java.net.ServerSocket
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer

class GrokOAuthTest {
    private val now = Instant.parse("2026-09-27T12:00:00Z")
    private val server = MockWebServer()
    private val io = testIoDispatcher()
    private val port = ServerSocket(0).use { it.localPort }
    private lateinit var grok: GrokOAuth

    @BeforeTest
    fun setUp() {
        server.start()
        grok =
            GrokOAuth(
                http = OkHttpAuthHttpClient(),
                clock = Clock.fixed(now, ZoneOffset.UTC),
                tokenEndpoint = server.url("/oauth2/token").toString(),
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
    fun `the default listener is the fixed xAI callback port`() {
        val config = GrokOAuth(OkHttpAuthHttpClient(), Clock.systemUTC()).loopback
        assertEquals(56121..56121, config.ports)
        assertEquals("/callback", config.path)
        assertEquals(setOf("https://accounts.x.ai", "https://auth.x.ai"), config.allowedOrigins)
    }

    @Test
    fun `the authorize URL carries the xAI client, scopes and a nonce`() {
        BrowserOAuthFlow(grok, io).start(null).use { signIn ->
            val params = queryPairs(signIn.authorizeUrl)
            assertTrue(signIn.authorizeUrl.startsWith("https://auth.x.ai/oauth2/authorize?"))
            assertEquals(
                listOf(
                    "response_type",
                    "client_id",
                    "redirect_uri",
                    "scope",
                    "code_challenge",
                    "code_challenge_method",
                    "state",
                    "nonce",
                ),
                params.map { it.first },
            )
            val values = params.toMap()
            assertEquals("b1a00492-073a-47ea-816f-4c329264a828", values["client_id"])
            assertEquals("http://127.0.0.1:$port/callback", values["redirect_uri"])
            assertEquals(
                "openid profile email offline_access grok-cli:access api:access",
                values["scope"],
            )
            assertTrue(Regex("^[0-9a-f]{32}$").matches(values.getValue("state")))
            assertTrue(Regex("^[0-9a-f]{32}$").matches(values.getValue("nonce")))
            assertNull(signIn.manualAuthorizeUrl)
        }
    }

    @Test
    fun `a redirect is exchanged with a form body`() = runTest {
        val idToken = fakeJwt("""{"sub":"xai-user","email":"sam@example.com"}""")
        server.enqueue(
            MockResponse(
                code = 200,
                body =
                    """{"access_token":"xa","refresh_token":"xr","expires_in":3600,"id_token":"$idToken"}""",
            )
        )
        val signIn = BrowserOAuthFlow(grok, io).start(null)
        val state = queryPairs(signIn.authorizeUrl).toMap().getValue("state")

        val browser = async {
            browserGet(io, "http://127.0.0.1:$port/callback?code=gc&state=$state")
        }
        val tokens = signIn.awaitTokens()

        val request = server.takeRequest()
        assertEquals("application/x-www-form-urlencoded", request.headers["Content-Type"])
        assertEquals("application/json", request.headers["Accept"])
        val form = formOf(request.body?.utf8())
        assertEquals(
            listOf("grant_type", "code", "redirect_uri", "client_id", "code_verifier"),
            form.map { it.first },
        )
        assertEquals("authorization_code", form.toMap()["grant_type"])
        assertEquals("gc", form.toMap()["code"])
        assertEquals("http://127.0.0.1:$port/callback", form.toMap()["redirect_uri"])

        assertEquals(Provider.Grok, tokens.provider)
        assertEquals("xa", tokens.accessToken)
        assertEquals("xr", tokens.refreshToken)
        assertEquals(now.plus(Duration.ofMinutes(58)), tokens.expiresAt)
        assertEquals("xai-user", tokens.providerAccountId)
        assertEquals("sam@example.com", tokens.label)
        assertEquals(200, browser.await().status)
    }

    @Test
    fun `a sign-in without a refresh token is refused`() = runTest {
        server.enqueue(
            MockResponse(code = 200, body = """{"access_token":"xa","expires_in":3600}""")
        )
        assertFailsWith<AuthException.InvalidResponse> {
            grok.exchange("c", "http://127.0.0.1:$port/callback", Pkce.generate(), "s")
        }
    }

    @Test
    fun `refresh keeps the old refresh token when none comes back`() = runTest {
        server.enqueue(
            MockResponse(code = 200, body = """{"access_token":"new","expires_in":60}""")
        )
        val old =
            TokenSet(Provider.Grok, CredentialKind.OAuth, "old", "old-refresh", now)
                .toCredential("g")

        val tokens = grok.refresh(old)

        val form = formOf(server.takeRequest().body?.utf8()).toMap()
        assertEquals("refresh_token", form["grant_type"])
        assertEquals("old-refresh", form["refresh_token"])
        assertEquals("b1a00492-073a-47ea-816f-4c329264a828", form["client_id"])
        assertNull(tokens.refreshToken)
        assertEquals("old-refresh", old.refreshedWith(tokens).refreshToken)
        assertEquals(now.plusSeconds(30), tokens.expiresAt)
    }

    @Test
    fun `a missing expiry defaults to one hour`() = runTest {
        server.enqueue(MockResponse(code = 200, body = """{"access_token":"new"}"""))
        val old = TokenSet(Provider.Grok, CredentialKind.OAuth, "old", "r", now).toCredential("g")
        assertEquals(now.plus(Duration.ofMinutes(58)), grok.refresh(old).expiresAt)
    }
}
