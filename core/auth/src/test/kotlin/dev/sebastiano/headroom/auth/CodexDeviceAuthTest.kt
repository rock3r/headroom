package dev.sebastiano.headroom.auth

import dev.sebastiano.headroom.model.Provider
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
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer

class CodexDeviceAuthTest {
    private val now = Instant.parse("2026-09-27T12:00:00Z")
    private val server = MockWebServer()
    private lateinit var codex: CodexDeviceAuth

    private val idToken =
        fakeJwt(
            """{"email":"sam@example.com","https://api.openai.com/auth":{"chatgpt_account_id":"acct-123"}}"""
        )

    @BeforeTest
    fun setUp() {
        server.start()
        codex =
            CodexDeviceAuth(
                http = OkHttpAuthHttpClient(),
                clock = Clock.fixed(now, ZoneOffset.UTC),
                issuer = server.url("/").toString().trimEnd('/'),
            )
    }

    @AfterTest
    fun tearDown() {
        server.close()
    }

    private fun grant() =
        DeviceCodeGrant("ABCD-1234", "dev-auth-1", "https://x", null, Duration.ofSeconds(5), null)

    @Test
    fun `requesting a code posts the client id as JSON`() = runTest {
        server.enqueue(
            MockResponse(
                code = 200,
                body = """{"device_auth_id":"dev-auth-1","user_code":"ABCD-1234","interval":"7"}""",
            )
        )

        val grant = codex.requestCode()

        val request = server.takeRequest()
        assertEquals("/api/accounts/deviceauth/usercode", request.url.encodedPath)
        assertEquals("application/json", request.headers["Content-Type"])
        assertEquals("""{"client_id":"app_EMoamEEZ73f0CkXaXp7hrann"}""", request.body?.utf8())
        assertEquals("ABCD-1234", grant.userCode)
        assertEquals("dev-auth-1", grant.deviceCode)
        assertEquals(Duration.ofSeconds(7), grant.interval)
        assertEquals("https://auth.openai.com/codex/device", grant.verificationUri)
        assertNull(grant.verificationUriComplete)
        assertEquals(Duration.ofMinutes(15), grant.expiresIn)
    }

    @Test
    fun `the interval defaults to five seconds`() = runTest {
        server.enqueue(
            MockResponse(code = 200, body = """{"device_auth_id":"d","usercode":"U-1"}""")
        )
        val grant = codex.requestCode()
        assertEquals("U-1", grant.userCode)
        assertEquals(Duration.ofSeconds(5), grant.interval)
    }

    @Test
    fun `403 while polling means the user has not approved yet`() = runTest {
        server.enqueue(MockResponse(code = 403, body = """{"error":"pending"}"""))

        assertEquals(DevicePoll.Pending, codex.poll(grant()))

        val request = server.takeRequest()
        assertEquals("/api/accounts/deviceauth/token", request.url.encodedPath)
        assertEquals(
            """{"device_auth_id":"dev-auth-1","user_code":"ABCD-1234"}""",
            request.body?.utf8(),
        )
    }

    @Test
    fun `404 while polling means the session is gone`() = runTest {
        server.enqueue(MockResponse(code = 404, body = ""))
        assertFailsWith<AuthException.SignInFailed> { codex.poll(grant()) }
    }

    @Test
    fun `an approved code is exchanged with the server's verifier`() = runTest {
        server.enqueue(
            MockResponse(
                code = 200,
                body =
                    """{"authorization_code":"auth-code","code_challenge":"ch","code_verifier":"server-verifier"}""",
            )
        )
        server.enqueue(
            MockResponse(
                code = 200,
                body =
                    """{"access_token":"ca","refresh_token":"cr","id_token":"$idToken","expires_in":864000}""",
            )
        )

        val answer = codex.poll(grant())

        server.takeRequest()
        val exchange = server.takeRequest()
        assertEquals("/oauth/token", exchange.url.encodedPath)
        assertEquals("application/x-www-form-urlencoded", exchange.headers["Content-Type"])
        assertEquals(
            listOf(
                "grant_type" to "authorization_code",
                "code" to "auth-code",
                "redirect_uri" to "https://auth.openai.com/deviceauth/callback",
                "client_id" to "app_EMoamEEZ73f0CkXaXp7hrann",
                "code_verifier" to "server-verifier",
            ),
            queryPairs("?" + checkNotNull(exchange.body).utf8()),
        )
        val tokens = (answer as DevicePoll.Authorized).tokens
        assertEquals(Provider.Codex, tokens.provider)
        assertEquals("ca", tokens.accessToken)
        assertEquals("cr", tokens.refreshToken)
        assertEquals(now.plus(Duration.ofDays(10)), tokens.expiresAt)
        assertEquals("acct-123", tokens.extras[CredentialExtras.CHATGPT_ACCOUNT_ID])
        assertEquals("acct-123", tokens.providerAccountId)
        assertEquals("sam@example.com", tokens.label)
    }

    @Test
    fun `the account id falls back to the access token claim`() = runTest {
        val access = fakeJwt("""{"https://api.openai.com/auth":{"chatgpt_account_id":"acct-9"}}""")
        server.enqueue(
            MockResponse(
                code = 200,
                body = """{"authorization_code":"a","code_challenge":"c","code_verifier":"v"}""",
            )
        )
        server.enqueue(
            MockResponse(code = 200, body = """{"access_token":"$access","refresh_token":"r"}""")
        )

        val tokens = (codex.poll(grant()) as DevicePoll.Authorized).tokens

        assertEquals("acct-9", tokens.extras[CredentialExtras.CHATGPT_ACCOUNT_ID])
        assertEquals(now.plusSeconds(3600), tokens.expiresAt)
    }

    @Test
    fun `a sign-in without a ChatGPT account id is refused`() = runTest {
        server.enqueue(
            MockResponse(
                code = 200,
                body = """{"authorization_code":"a","code_challenge":"c","code_verifier":"v"}""",
            )
        )
        server.enqueue(
            MockResponse(code = 200, body = """{"access_token":"plain","refresh_token":"r"}""")
        )

        assertFailsWith<AuthException.InvalidResponse> { codex.poll(grant()) }
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
        assertEquals(now.plusSeconds(100), tokens.expiresAt)
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
