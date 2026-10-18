package dev.sebastiano.headroom.auth

import dev.sebastiano.headroom.model.Provider
import kotlin.io.encoding.Base64
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer

class CursorAuthTest {
    private val now = Instant.parse("2026-10-17T12:00:00Z")
    private val server = MockWebServer()
    private lateinit var cursor: CursorAuth

    @BeforeTest
    fun setUp() {
        server.start()
        val host = server.url("/").toString().trimEnd('/')
        cursor =
            CursorAuth(
                http = KtorAuthHttpClient(),
                clock = fixedClock(now),
                websiteUrl = host,
                apiUrl = host,
                newPkce = { Pkce("verifier-1", "challenge-1") },
                newUuid = { "uuid-1" },
            )
    }

    @AfterTest
    fun tearDown() {
        server.close()
    }

    private fun jwt(payload: String): String {
        val encode = { text: String ->
            Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(text.encodeToByteArray())
        }
        return "${encode("""{"alg":"none"}""")}.${encode(payload)}.signature"
    }

    private fun accessJwt(expiresAt: Instant = now.plus(3600.seconds)) =
        jwt(
            """{"sub":"grok|user_1","email":"someone@example.com","exp":${expiresAt.epochSeconds}}"""
        )

    private fun grant() =
        DeviceCodeGrant(
            userCode = "",
            deviceCode = "uuid-1",
            verificationUri = "https://cursor.com/loginDeepControl",
            verificationUriComplete = null,
            interval = 2.seconds,
            expiresIn = null,
            pollToken = "verifier-1",
        )

    @Test
    fun `starting builds the Cursor login page from a fresh challenge and uuid`() = runTest {
        val grant = cursor.requestCode()

        assertEquals("", grant.userCode)
        assertEquals("uuid-1", grant.deviceCode)
        assertEquals("verifier-1", grant.pollToken)
        assertEquals(
            server.url("/").toString().trimEnd('/') +
                "/loginDeepControl?challenge=challenge-1&uuid=uuid-1" +
                "&mode=login&supportsSelectedTeamLogin=true",
            grant.verificationUriComplete,
        )
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `a poll the backend has not seen yet is pending`() = runTest {
        server.enqueue(MockResponse(code = 404))

        assertEquals(DevicePoll.Pending, cursor.poll(grant()))

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals(
            "/auth/poll?uuid=uuid-1&verifier=verifier-1",
            request.url.toString().substringAfter(server.url("/").toString().trimEnd('/')),
        )
    }

    @Test
    fun `a finished poll gives the account's tokens`() = runTest {
        server.enqueue(
            MockResponse(
                code = 200,
                body =
                    """{"accessToken":"${accessJwt()}","refreshToken":"refresh-1",""" +
                        """"authId":"grok|user_1"}""",
            )
        )

        val authorized = assertIs<DevicePoll.Authorized>(cursor.poll(grant()))

        val tokens = authorized.tokens
        assertEquals(Provider.Grok, tokens.provider)
        assertEquals(CredentialKind.OAuth, tokens.kind)
        assertEquals("refresh-1", tokens.refreshToken)
        assertEquals("grok|user_1", tokens.providerAccountId)
        assertEquals("someone@example.com", tokens.label)
        // The token's own expiry, minus the two-minute margin: the expiry JWT claim minus 120s
        // lands before half the lifetime, so expiryWithSkew semantics apply via the exp claim.
        assertEquals(now.plus(3600.seconds).minus(120.seconds), tokens.expiresAt)
    }

    @Test
    fun `a finished poll without a refresh token is refused`() = runTest {
        server.enqueue(MockResponse(code = 200, body = """{"accessToken":"${accessJwt()}"}"""))

        assertIs<DevicePoll.Denied>(cursor.poll(grant()))
    }

    @Test
    fun `a refresh trades the refresh token at the OAuth endpoint`() = runTest {
        server.enqueue(
            MockResponse(
                code = 200,
                body =
                    """{"access_token":"${accessJwt(now.plus(7200.seconds))}",""" +
                        """"refresh_token":"refresh-2"}""",
            )
        )
        val credential =
            TokenSet(Provider.Grok, CredentialKind.OAuth, "old", "refresh-1", now)
                .toCredential(CursorCredential.idFor("g1"))

        val tokens = cursor.refresh(credential)

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/oauth/token", request.url.encodedPath)
        assertEquals(
            """{"grant_type":"refresh_token","client_id":"${CursorAuth.CLIENT_ID}",""" +
                """"refresh_token":"refresh-1"}""",
            request.body?.utf8(),
        )
        assertEquals("refresh-2", tokens.refreshToken)
        assertEquals(now.plus(7200.seconds).minus(120.seconds), tokens.expiresAt)
        assertEquals("someone@example.com", tokens.label)
    }

    @Test
    fun `a refresh the backend ends with a logout requires a new sign-in`() = runTest {
        server.enqueue(
            MockResponse(code = 200, body = """{"shouldLogout":true,"error":"revoked"}""")
        )
        val credential =
            TokenSet(Provider.Grok, CredentialKind.OAuth, "old", "refresh-1", now)
                .toCredential(CursorCredential.idFor("g1"))

        val failure = assertFailsWith<AuthException.Rejected> { cursor.refresh(credential) }

        assertTrue(failure.requiresSignIn)
    }

    @Test
    fun `the credential id sits next to the Grok account's own id`() {
        assertEquals("abc#cursor", CursorCredential.idFor("abc"))
        assertTrue(CursorCredential.isCursorId("abc#cursor"))
        assertEquals(false, CursorCredential.isCursorId("abc"))
    }
}
