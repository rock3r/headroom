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
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer

class ClaudeOAuthTest {
    private val now = Instant.parse("2026-09-27T12:00:00Z")
    private val server = MockWebServer()
    private val io = testIoDispatcher()
    private lateinit var claude: ClaudeOAuth

    @BeforeTest
    fun setUp() {
        server.start()
        claude =
            ClaudeOAuth(
                http = OkHttpAuthHttpClient(),
                clock = Clock.fixed(now, ZoneOffset.UTC),
                tokenEndpoint = server.url("/v1/oauth/token").toString(),
            )
    }

    @AfterTest
    fun tearDown() {
        server.close()
        io.close()
    }

    private val tokenBody =
        """
        {"token_type":"Bearer","access_token":"sk-ant-oat-access","refresh_token":"sk-ant-ort-refresh",
         "expires_in":28800,"scope":"user:inference user:profile",
         "account":{"uuid":"acc-uuid","email_address":"sam@example.com"},
         "organization":{"uuid":"org-uuid","name":"Sam's org"}}
        """
            .trimIndent()

    private fun recordedJson(): JsonObject =
        Json.parseToJsonElement(checkNotNull(server.takeRequest().body).utf8()).jsonObject

    private fun JsonObject.text(key: String) = getValue(key).jsonPrimitive.content

    @Test
    fun `the authorize URL matches the Claude Code CLI`() = runTest {
        val signIn = BrowserOAuthFlow(claude, io).start(null)
        signIn.use {
            val params = queryPairs(it.authorizeUrl)

            assertTrue(it.authorizeUrl.startsWith("https://claude.com/cai/oauth/authorize?"))
            assertEquals(
                listOf(
                    "code",
                    "client_id",
                    "response_type",
                    "redirect_uri",
                    "scope",
                    "code_challenge",
                    "code_challenge_method",
                    "state",
                ),
                params.map { pair -> pair.first },
            )
            val values = params.toMap()
            assertEquals("true", values["code"])
            assertEquals("9d1c250a-e61b-44d9-88ed-5944d1962f5e", values["client_id"])
            assertEquals("code", values["response_type"])
            assertTrue(
                Regex("^http://localhost:\\d+/callback$").matches(values.getValue("redirect_uri"))
            )
            assertEquals(
                "org:create_api_key user:profile user:inference user:sessions:claude_code " +
                    "user:mcp_servers user:file_upload user:plugins",
                values["scope"],
            )
            assertEquals("S256", values["code_challenge_method"])
            assertEquals(43, values.getValue("state").length)
            assertEquals(43, values.getValue("code_challenge").length)
        }
    }

    @Test
    fun `the manual URL differs only in its redirect URI`() = runTest {
        BrowserOAuthFlow(claude, io).start(null).use {
            val automatic = queryPairs(it.authorizeUrl).toMap()
            val manual = queryPairs(checkNotNull(it.manualAuthorizeUrl)).toMap()

            assertEquals("https://platform.claude.com/oauth/code/callback", manual["redirect_uri"])
            assertEquals(automatic - "redirect_uri", manual - "redirect_uri")
        }
    }

    @Test
    fun `a redirect is exchanged with the loopback URI and a JSON body`() = runTest {
        server.enqueue(MockResponse(code = 200, body = tokenBody))
        val signIn = BrowserOAuthFlow(claude, io).start("headroom://signed-in")
        val params = queryPairs(signIn.authorizeUrl).toMap()
        val redirectUri = params.getValue("redirect_uri")
        val state = params.getValue("state")
        val port = redirectUri.substringAfter("localhost:").substringBefore('/')

        val browser = async {
            browserGet(io, "http://127.0.0.1:$port/callback?code=the-code&state=$state")
        }
        val tokens = signIn.awaitTokens()

        val request = server.takeRequest()
        assertEquals("/v1/oauth/token", request.url.encodedPath)
        assertEquals("application/json", request.headers["Content-Type"])
        val body = Json.parseToJsonElement(checkNotNull(request.body).utf8()).jsonObject
        assertEquals(
            listOf("grant_type", "code", "redirect_uri", "client_id", "code_verifier", "state"),
            body.keys.toList(),
        )
        assertEquals("authorization_code", body.text("grant_type"))
        assertEquals("the-code", body.text("code"))
        assertEquals(redirectUri, body.text("redirect_uri"))
        assertEquals("9d1c250a-e61b-44d9-88ed-5944d1962f5e", body.text("client_id"))
        assertEquals(state, body.text("state"))
        assertNotEquals(state, body.text("code_verifier"))
        assertEquals(
            params["code_challenge"],
            Pkce.challengeFor(body.text("code_verifier")),
        )

        assertEquals(Provider.Claude, tokens.provider)
        assertEquals("sk-ant-oat-access", tokens.accessToken)
        assertEquals("sk-ant-ort-refresh", tokens.refreshToken)
        assertEquals(now.plus(Duration.ofHours(8)).minus(Duration.ofMinutes(5)), tokens.expiresAt)
        assertEquals("acc-uuid", tokens.providerAccountId)
        assertEquals("sam@example.com", tokens.label)
        assertEquals("org-uuid", tokens.extras[CredentialExtras.CLAUDE_ORGANIZATION_ID])
        assertEquals(200, browser.await().status)
    }

    @Test
    fun `a pasted code is exchanged with the manual redirect URI`() = runTest {
        server.enqueue(MockResponse(code = 200, body = tokenBody))
        val signIn = BrowserOAuthFlow(claude, io).start(null)
        val state = queryPairs(signIn.authorizeUrl).toMap().getValue("state")

        signIn.submitPastedCode("pasted-code#$state")
        signIn.awaitTokens()

        val body = recordedJson()
        assertEquals("pasted-code", body.text("code"))
        assertEquals("https://platform.claude.com/oauth/code/callback", body.text("redirect_uri"))
    }

    @Test
    fun `a pasted code must include its state`() = runTest {
        BrowserOAuthFlow(claude, io).start(null).use {
            assertFailsWith<AuthException.SignInFailed> { it.submitPastedCode("code-only") }
        }
    }

    @Test
    fun `refresh sends the refresh token and shortens the expiry by five minutes`() = runTest {
        server.enqueue(
            MockResponse(
                code = 200,
                body =
                    """{"access_token":"new-access","refresh_token":"new-refresh","expires_in":3600}""",
            )
        )
        val old =
            TokenSet(Provider.Claude, CredentialKind.OAuth, "old", "old-refresh", now)
                .toCredential("c")

        val tokens = claude.refresh(old)

        val body = recordedJson()
        assertEquals("refresh_token", body.text("grant_type"))
        assertEquals("old-refresh", body.text("refresh_token"))
        assertEquals("9d1c250a-e61b-44d9-88ed-5944d1962f5e", body.text("client_id"))
        assertEquals("new-access", tokens.accessToken)
        assertEquals("new-refresh", tokens.refreshToken)
        assertEquals(now.plus(Duration.ofMinutes(55)), tokens.expiresAt)
    }

    @Test
    fun `a rejected refresh asks for a new sign-in`() = runTest {
        server.enqueue(
            MockResponse(
                code = 400,
                body = """{"error":"invalid_grant","error_description":"Refresh token revoked"}""",
            )
        )
        val old =
            TokenSet(Provider.Claude, CredentialKind.OAuth, "old", "old-refresh", now)
                .toCredential("c")

        val error = assertFailsWith<AuthException.Rejected> { claude.refresh(old) }

        assertEquals("invalid_grant", error.error)
        assertTrue(error.requiresSignIn)
    }
}
