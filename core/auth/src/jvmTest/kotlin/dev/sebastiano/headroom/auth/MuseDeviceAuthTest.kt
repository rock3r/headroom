package dev.sebastiano.headroom.auth

import dev.sebastiano.headroom.model.Provider
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer

class MuseDeviceAuthTest {
    private val server = MockWebServer()
    private lateinit var muse: MuseDeviceAuth

    companion object {
        val NOW: Instant = Instant.parse("2026-09-18T12:00:00Z")
    }

    @BeforeTest
    fun setUp() {
        server.start()
        muse =
            MuseDeviceAuth(
                http = KtorAuthHttpClient(),
                clock = fixedClock(NOW),
                authHost = server.url("/").toString().trimEnd('/'),
                apiBaseUrl = server.url("/").toString().trimEnd('/'),
            )
    }

    @AfterTest
    fun tearDown() {
        server.close()
    }

    private fun grant() =
        DeviceCodeGrant("MUSE-1", "mdc", "https://auth.meta.com/device", null, 5.seconds, null)

    private fun form(body: String?) = queryPairs("?" + checkNotNull(body))

    @Test
    fun `requesting a code sends only the Muse client id`() = runTest {
        server.enqueue(
            MockResponse(
                code = 200,
                body =
                    """{"user_code":"MUSE-1","device_code":"mdc",""" +
                        """"verification_uri":"https://auth.meta.com/device",""" +
                        """"verification_uri_complete":"https://auth.meta.com/device?user_code=MUSE-1",""" +
                        """"expires_in":900,"interval":0}""",
            )
        )

        val grant = muse.requestCode()

        val request = server.takeRequest()
        assertEquals("/oidc/device/authorization/", request.url.encodedPath)
        assertEquals(listOf("client_id" to "1031625952748946"), form(request.body?.utf8()))
        assertEquals("MUSE-1", grant.userCode)
        assertEquals("mdc", grant.deviceCode)
        assertEquals("https://auth.meta.com/device", grant.verificationUri)
        assertEquals("https://auth.meta.com/device?user_code=MUSE-1", grant.verificationUriComplete)
        assertEquals(1.seconds, grant.interval)
        assertEquals(900.seconds, grant.expiresIn)
    }

    @Test
    fun `a verification page outside auth meta com is refused`() = runTest {
        server.enqueue(
            MockResponse(
                code = 200,
                body =
                    """{"user_code":"M","device_code":"d",""" +
                        """"verification_uri":"https://example.com/device"}""",
            )
        )
        assertFailsWith<AuthException.InvalidResponse> { muse.requestCode() }
    }

    @Test
    fun `polling maps OAuth errors to answers`() = runTest {
        listOf(
                """{"error":"authorization_pending"}""" to DevicePoll.Pending,
                """{"error":"slow_down"}""" to DevicePoll.SlowDown,
                """{"error":"expired_token"}""" to DevicePoll.Expired,
            )
            .forEach { (body, expected) ->
                server.enqueue(MockResponse(code = 400, body = body))
                assertEquals(expected, muse.poll(grant()))
            }
        server.enqueue(MockResponse(code = 400, body = """{"error":"access_denied"}"""))
        assertIs<DevicePoll.Denied>(muse.poll(grant()))

        val request = server.takeRequest()
        assertEquals("/oidc/device/token/", request.url.encodedPath)
        assertEquals(
            listOf(
                "client_id" to "1031625952748946",
                "device_code" to "mdc",
                "grant_type" to "urn:ietf:params:oauth:grant-type:device_code",
            ),
            form(request.body?.utf8()),
        )
    }

    @Test
    fun `an unknown error ends the sign-in`() = runTest {
        server.enqueue(
            MockResponse(
                code = 400,
                body = """{"error":"invalid_client","error_description":"x"}""",
            )
        )
        assertFailsWith<AuthException.Rejected> { muse.poll(grant()) }
    }

    @Test
    fun `approval keeps the DCA token and learns the account from the key call`() = runTest {
        server.enqueue(
            MockResponse(
                code = 200,
                body = """{"access_token":"dca:token","token_type":"Bearer"}""",
            )
        )
        server.enqueue(
            MockResponse(
                code = 200,
                body =
                    """{"api_key":"LLM|key","user_email":"sam@example.com",""" +
                        """"user_full_name":"Sam","subs_tier_name":"Muse Code Power Usage",""" +
                        """"is_subs_active":true}""",
            )
        )

        val tokens = (muse.poll(grant()) as DevicePoll.Authorized).tokens

        assertEquals("/oidc/device/token/", server.takeRequest().url.encodedPath)
        val mint = server.takeRequest()
        assertEquals("/muse-code/key", mint.url.encodedPath)
        assertEquals("Bearer dca:token", mint.headers["Authorization"])
        assertEquals("1.0.0", mint.headers["x-api-version"])
        // The first exchange onboards the account; quota reads afterwards do not.
        assertEquals("""{"onboard":true}""", mint.body?.utf8()?.trim())
        assertEquals(Provider.Muse, tokens.provider)
        assertEquals(CredentialKind.OAuth, tokens.kind)
        assertEquals("dca:token", tokens.accessToken)
        assertNull(tokens.refreshToken)
        assertNull(tokens.expiresAt)
        assertEquals("sam@example.com", tokens.label)
        assertEquals("sam@example.com", tokens.providerAccountId)
    }

    @Test
    fun `approval falls back to the full name when the key call names no email`() = runTest {
        server.enqueue(MockResponse(code = 200, body = """{"access_token":"dca:token"}"""))
        server.enqueue(
            MockResponse(code = 200, body = """{"api_key":"LLM|key","user_full_name":"Sam Meta"}""")
        )

        val tokens = (muse.poll(grant()) as DevicePoll.Authorized).tokens

        assertEquals("Sam Meta", tokens.label)
        // A display name is not an identity: two accounts can share one.
        assertNull(tokens.providerAccountId)
    }

    @Test
    fun `approval prefers the stable user id for the account identity`() = runTest {
        server.enqueue(MockResponse(code = 200, body = """{"access_token":"dca:token"}"""))
        server.enqueue(
            MockResponse(
                code = 200,
                body = """{"user_id":"u-42","user_email":"sam@example.com"}""",
            )
        )

        val tokens = (muse.poll(grant()) as DevicePoll.Authorized).tokens

        assertEquals("sam@example.com", tokens.label)
        assertEquals("u-42", tokens.providerAccountId)
    }

    @Test
    fun `approval keeps the expiry Meta reports for the token`() = runTest {
        server.enqueue(
            MockResponse(
                code = 200,
                body = """{"access_token":"dca:token","expires_in":3600}""",
            )
        )
        server.enqueue(MockResponse(code = 200, body = """{"user_email":"sam@example.com"}"""))

        val tokens = (muse.poll(grant()) as DevicePoll.Authorized).tokens

        assertEquals(NOW + 3300.seconds, tokens.expiresAt)
    }

    @Test
    fun `approval still works when the key call fails`() = runTest {
        server.enqueue(MockResponse(code = 200, body = """{"access_token":"dca:token"}"""))
        server.enqueue(MockResponse(code = 500, body = """{"error":"boom"}"""))

        val tokens = (muse.poll(grant()) as DevicePoll.Authorized).tokens

        assertEquals("dca:token", tokens.accessToken)
        assertNull(tokens.label)
        assertNull(tokens.providerAccountId)
    }
}
