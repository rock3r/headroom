package dev.sebastiano.headroom.auth

import dev.sebastiano.headroom.model.Provider
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer

class KimiDeviceAuthTest {
    private val now = Instant.parse("2026-09-27T12:00:00Z")
    private val server = MockWebServer()
    private lateinit var kimi: KimiDeviceAuth

    @BeforeTest
    fun setUp() {
        server.start()
        kimi =
            KimiDeviceAuth(
                http = OkHttpAuthHttpClient(),
                clock = fixedClock(now),
                oauthHost = server.url("/").toString().trimEnd('/'),
            )
    }

    @AfterTest
    fun tearDown() {
        server.close()
    }

    private fun grant() =
        DeviceCodeGrant("K-1", "kdc", "https://kimi/device", null, 5.seconds, null)

    private fun form(body: String?) = queryPairs("?" + checkNotNull(body))

    @Test
    fun `requesting a code sends the Kimi client id`() = runTest {
        server.enqueue(
            MockResponse(
                code = 200,
                body =
                    """{"user_code":"K-1","device_code":"kdc",""" +
                        """"verification_uri":"https://www.kimi.com/device",""" +
                        """"verification_uri_complete":"https://www.kimi.com/device?user_code=K-1",""" +
                        """"expires_in":600,"interval":0}""",
            )
        )

        val grant = kimi.requestCode()

        val request = server.takeRequest()
        assertEquals("/api/oauth/device_authorization", request.url.encodedPath)
        assertEquals(
            listOf("client_id" to "17e5f671-d194-4dfb-9706-5516cb48c098"),
            form(request.body?.utf8()),
        )
        assertEquals("K-1", grant.userCode)
        assertEquals("https://www.kimi.com/device", grant.verificationUri)
        assertEquals("https://www.kimi.com/device?user_code=K-1", grant.verificationUriComplete)
        assertEquals(1.seconds, grant.interval)
        assertEquals(600.seconds, grant.expiresIn)
    }

    @Test
    fun `the verification URI falls back to the complete one`() = runTest {
        server.enqueue(
            MockResponse(
                code = 200,
                body =
                    """{"user_code":"K","device_code":"d",""" +
                        """"verification_uri_complete":"https://www.kimi.com/device?c=K"}""",
            )
        )
        val grant = kimi.requestCode()
        assertEquals("https://www.kimi.com/device?c=K", grant.verificationUri)
        assertEquals(5.seconds, grant.interval)
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
                assertEquals(expected, kimi.poll(grant()))
            }
        server.enqueue(MockResponse(code = 400, body = """{"error":"access_denied"}"""))
        assertIs<DevicePoll.Denied>(kimi.poll(grant()))

        val request = server.takeRequest()
        assertEquals("/api/oauth/token", request.url.encodedPath)
        assertEquals(
            listOf(
                "client_id" to "17e5f671-d194-4dfb-9706-5516cb48c098",
                "device_code" to "kdc",
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
        assertFailsWith<AuthException.Rejected> { kimi.poll(grant()) }
    }

    @Test
    fun `approval returns tokens that expire a little early`() = runTest {
        server.enqueue(
            MockResponse(
                code = 200,
                body = """{"access_token":"ka","refresh_token":"kr","expires_in":900}""",
            )
        )

        val tokens = (kimi.poll(grant()) as DevicePoll.Authorized).tokens

        assertEquals(Provider.Kimi, tokens.provider)
        assertEquals("ka", tokens.accessToken)
        assertEquals("kr", tokens.refreshToken)
        assertEquals(now + 600.seconds, tokens.expiresAt)
    }

    @Test
    fun `refresh posts the refresh token and keeps at least thirty seconds`() = runTest {
        server.enqueue(
            MockResponse(
                code = 200,
                body = """{"access_token":"new","refresh_token":"new-r","expires_in":0}""",
            )
        )
        val old = TokenSet(Provider.Kimi, CredentialKind.OAuth, "old", "kr", now).toCredential("k")

        val tokens = kimi.refresh(old)

        assertEquals(
            listOf(
                "client_id" to "17e5f671-d194-4dfb-9706-5516cb48c098",
                "grant_type" to "refresh_token",
                "refresh_token" to "kr",
            ),
            form(server.takeRequest().body?.utf8()),
        )
        assertEquals("new-r", tokens.refreshToken)
        assertEquals(now + 30.seconds, tokens.expiresAt)
    }
}
