package dev.sebastiano.headroom.auth

import dev.sebastiano.headroom.model.Provider
import java.time.Duration
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer

class CopilotDeviceAuthTest {
    private val now = Instant.parse("2026-09-27T12:00:00Z")
    private val server = MockWebServer()
    private lateinit var copilot: CopilotDeviceAuth

    @BeforeTest
    fun setUp() {
        server.start()
        val base = server.url("/").toString().trimEnd('/')
        copilot =
            CopilotDeviceAuth(
                http = OkHttpAuthHttpClient(),
                gitHubBaseUrl = base,
                gitHubApiBaseUrl = "$base/api",
            )
    }

    @AfterTest
    fun tearDown() {
        server.close()
    }

    private fun grant() =
        DeviceCodeGrant(
            "WDJB-MJHT",
            "dc-1",
            "https://github.com/login/device",
            null,
            Duration.ofSeconds(5),
            null,
        )

    private fun copilotToken(expiresAt: Long = now.epochSecond + 1800) =
        MockResponse(
            code = 200,
            body = """{"token":"tid=1;exp=2;proxy-ep=proxy.x","expires_at":$expiresAt}""",
        )

    @Test
    fun `requesting a code asks for read-user with the Copilot client`() = runTest {
        server.enqueue(
            MockResponse(
                code = 200,
                body =
                    """{"device_code":"dc-1","user_code":"WDJB-MJHT",""" +
                        """"verification_uri":"https://github.com/login/device",""" +
                        """"expires_in":899,"interval":5}""",
            )
        )

        val grant = copilot.requestCode()

        val request = server.takeRequest()
        assertEquals("/login/device/code", request.url.encodedPath)
        assertEquals("application/json", request.headers["Accept"])
        assertEquals("GitHubCopilotChat/0.35.0", request.headers["User-Agent"])
        assertEquals(
            listOf("client_id" to "Iv1.b507a08c87ecfe98", "scope" to "read:user"),
            queryPairs("?" + checkNotNull(request.body).utf8()),
        )
        assertEquals("WDJB-MJHT", grant.userCode)
        assertEquals("https://github.com/login/device", grant.verificationUri)
        assertNull(grant.verificationUriComplete)
        assertEquals(Duration.ofSeconds(899), grant.expiresIn)
    }

    @Test
    fun `an untrusted verification URI is refused`() = runTest {
        server.enqueue(
            MockResponse(
                code = 200,
                body =
                    """{"device_code":"d","user_code":"u","verification_uri":"javascript:alert(1)"}""",
            )
        )
        assertFailsWith<AuthException.InvalidResponse> { copilot.requestCode() }
    }

    @Test
    fun `pending, slow down, expired and denied map to their poll answers`() = runTest {
        listOf(
                """{"error":"authorization_pending"}""" to DevicePoll.Pending,
                """{"error":"slow_down","interval":10}""" to DevicePoll.SlowDown,
                """{"error":"expired_token"}""" to DevicePoll.Expired,
            )
            .forEach { (body, expected) ->
                server.enqueue(MockResponse(code = 200, body = body))
                assertEquals(expected, copilot.poll(grant()))
            }
        server.enqueue(
            MockResponse(
                code = 200,
                body = """{"error":"access_denied","error_description":"Denied"}""",
            )
        )
        assertIs<DevicePoll.Denied>(copilot.poll(grant()))

        val poll = server.takeRequest()
        assertEquals("/login/oauth/access_token", poll.url.encodedPath)
        assertEquals(
            listOf(
                "client_id" to "Iv1.b507a08c87ecfe98",
                "device_code" to "dc-1",
                "grant_type" to "urn:ietf:params:oauth:grant-type:device_code",
            ),
            queryPairs("?" + checkNotNull(poll.body).utf8()),
        )
    }

    @Test
    fun `an approved code is turned into a Copilot token`() = runTest {
        server.enqueue(
            MockResponse(
                code = 200,
                body = """{"access_token":"gho_github","token_type":"bearer"}""",
            )
        )
        server.enqueue(copilotToken())

        val tokens = (copilot.poll(grant()) as DevicePoll.Authorized).tokens

        server.takeRequest()
        val exchange = server.takeRequest()
        assertEquals("GET", exchange.method)
        assertEquals("/api/copilot_internal/v2/token", exchange.url.encodedPath)
        assertEquals("Bearer gho_github", exchange.headers["Authorization"])
        assertEquals("GitHubCopilotChat/0.35.0", exchange.headers["User-Agent"])
        assertEquals("vscode/1.107.0", exchange.headers["Editor-Version"])
        assertEquals("copilot-chat/0.35.0", exchange.headers["Editor-Plugin-Version"])
        assertEquals("vscode-chat", exchange.headers["Copilot-Integration-Id"])
        assertEquals(Provider.Copilot, tokens.provider)
        assertEquals("tid=1;exp=2;proxy-ep=proxy.x", tokens.accessToken)
        assertEquals("gho_github", tokens.refreshToken)
        assertEquals(now.plus(Duration.ofMinutes(25)), tokens.expiresAt)
        assertEquals("gho_github", tokens.toCredential("c").gitHubToken)
    }

    @Test
    fun `refresh mints a new Copilot token from the GitHub token`() = runTest {
        server.enqueue(copilotToken(expiresAt = now.epochSecond + 600))
        val old =
            TokenSet(Provider.Copilot, CredentialKind.OAuth, "old", "gho_github", now)
                .toCredential("c")

        val tokens = copilot.refresh(old)

        assertEquals("Bearer gho_github", server.takeRequest().headers["Authorization"])
        assertEquals(now.plus(Duration.ofMinutes(5)), tokens.expiresAt)
        assertEquals("gho_github", tokens.refreshToken)
    }

    @Test
    fun `a GitHub token without Copilot access needs a new sign-in`() = runTest {
        server.enqueue(MockResponse(code = 401, body = """{"message":"Bad credentials"}"""))
        val old =
            TokenSet(Provider.Copilot, CredentialKind.OAuth, "old", "gho_revoked", now)
                .toCredential("c")

        val error = assertFailsWith<AuthException.Rejected> { copilot.refresh(old) }

        assertTrue(error.requiresSignIn)
    }
}
