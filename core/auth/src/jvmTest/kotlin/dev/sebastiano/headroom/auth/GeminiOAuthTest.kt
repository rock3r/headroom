package dev.sebastiano.headroom.auth

import dev.sebastiano.headroom.model.Provider
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer

class GeminiOAuthTest {
    private val now = Instant.parse("2026-09-27T12:00:00Z")
    private val server = MockWebServer()
    private lateinit var gemini: GeminiOAuth

    @BeforeTest
    fun setUp() {
        server.start()
        gemini =
            GeminiOAuth(
                http = KtorAuthHttpClient(),
                clock = fixedClock(now),
                tokenEndpoint = server.url("/token").toString(),
            )
    }

    @AfterTest
    fun tearDown() {
        server.close()
    }

    private val tokenBody =
        """
        {"access_token":"ya29.test-access-token","token_type":"Bearer","expires_in":3600,
         "refresh_token":"1//test-refresh-token","scope":"openid email profile"}
        """
            .trimIndent()

    @Test
    fun `authorize URL includes Google OAuth parameters and scopes`() {
        val pkce = Pkce.generate()
        val url = gemini.authorizeUrl("http://localhost:8085/oauth2callback", pkce, "test-state")

        val query = queryPairs(url).toMap()
        assertEquals(GeminiOAuth.CLIENT_ID, query["client_id"])
        assertEquals("code", query["response_type"])
        assertEquals("http://localhost:8085/oauth2callback", query["redirect_uri"])
        assertEquals(GeminiOAuth.SCOPES, query["scope"])
        assertEquals(pkce.challenge, query["code_challenge"])
        assertEquals("S256", query["code_challenge_method"])
        assertEquals("test-state", query["state"])
        assertEquals("offline", query["access_type"])
    }

    @Test
    fun `exchanges authorization code for token set`() = runTest {
        server.enqueue(MockResponse(code = 200, body = tokenBody))

        val pkce = Pkce.generate()
        val tokens =
            gemini.exchange(
                code = "4/test-code",
                redirectUri = "http://localhost:8085/oauth2callback",
                pkce = pkce,
                state = "state",
            )

        val request = server.takeRequest()
        val form = queryPairs("?" + checkNotNull(request.body).utf8()).toMap()
        assertEquals("authorization_code", form["grant_type"])
        assertEquals("4/test-code", form["code"])
        assertEquals("http://localhost:8085/oauth2callback", form["redirect_uri"])
        assertEquals(GeminiOAuth.CLIENT_ID, form["client_id"])
        assertEquals(pkce.verifier, form["code_verifier"])

        assertEquals(Provider.Gemini, tokens.provider)
        assertEquals("ya29.test-access-token", tokens.accessToken)
        assertEquals("1//test-refresh-token", tokens.refreshToken)
        assertNotNull(tokens.expiresAt)
    }

    @Test
    fun `refreshes tokens using refresh token`() = runTest {
        server.enqueue(MockResponse(code = 200, body = tokenBody))

        val old =
            TokenSet(
                    provider = Provider.Gemini,
                    kind = CredentialKind.OAuth,
                    accessToken = "old-token",
                    refreshToken = "1//test-refresh-token",
                    expiresAt = now,
                )
                .toCredential("gemini-account")

        val tokens = gemini.refresh(old)

        val request = server.takeRequest()
        val form = queryPairs("?" + checkNotNull(request.body).utf8()).toMap()
        assertEquals("refresh_token", form["grant_type"])
        assertEquals("1//test-refresh-token", form["refresh_token"])
        assertEquals(GeminiOAuth.CLIENT_ID, form["client_id"])

        assertEquals(Provider.Gemini, tokens.provider)
        assertEquals("ya29.test-access-token", tokens.accessToken)
    }
}
