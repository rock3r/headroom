package dev.sebastiano.headroom.auth

import dev.sebastiano.headroom.model.Provider
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer

class ZCodeAuthTest {
    private val now = Instant.parse("2026-10-04T12:00:00Z")
    private val server = MockWebServer()
    private lateinit var zCode: ZCodeAuth

    @BeforeTest
    fun setUp() {
        server.start()
        val host = server.url("/").toString().trimEnd('/')
        zCode =
            ZCodeAuth(
                http = KtorAuthHttpClient(),
                clock = fixedClock(now),
                zCodeHost = host,
                apiHost = host,
                newPollToken = { "client-poll-token" },
            )
    }

    @AfterTest
    fun tearDown() {
        server.close()
    }

    private fun grant(pollToken: String? = "poll-token") =
        DeviceCodeGrant(
            userCode = "",
            deviceCode = "flow-1",
            verificationUri = "https://chat.z.ai/api/oauth/authorize",
            verificationUriComplete = null,
            interval = 2.seconds,
            expiresIn = null,
            pollToken = pollToken,
        )

    private fun initBody(authorizeUrl: String = "https://chat.z.ai/api/oauth/authorize?x=1") =
        """{"code":0,"msg":"ok","data":{"flow_id":"flow-1","authorize_url":"$authorizeUrl",""" +
            """"expires_at":${now.epochSeconds + 600},"poll_interval_sec":3,""" +
            """"poll_token":"server-poll-token"}}"""

    @Test
    fun `starting asks zcode for a flow with a poll token`() = runTest {
        server.enqueue(MockResponse(code = 200, body = initBody()))

        val grant = zCode.requestCode()

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/oauth/cli/init", request.url.encodedPath)
        assertEquals("Bearer client-poll-token", request.headers["Authorization"])
        assertEquals("""{"provider":"zai"}""", request.body?.utf8())
        assertEquals("flow-1", grant.deviceCode)
        assertEquals("https://chat.z.ai/api/oauth/authorize?x=1", grant.verificationUri)
        assertEquals(3.seconds, grant.interval)
        assertEquals(600.seconds, grant.expiresIn)
        // The server may hand out its own poll token, which then replaces the client's.
        assertEquals("server-poll-token", grant.pollToken)
    }

    @Test
    fun `the client's poll token is kept when the server sends none`() = runTest {
        server.enqueue(
            MockResponse(
                code = 200,
                body =
                    """{"code":0,"data":{"flow_id":"f",""" +
                        """"authorize_url":"https://chat.z.ai/a","poll_interval_sec":0}}""",
            )
        )

        val grant = zCode.requestCode()

        assertEquals("client-poll-token", grant.pollToken)
        assertEquals(1.seconds, grant.interval)
        assertEquals(null, grant.expiresIn)
    }

    @Test
    fun `a flow that has already expired times out at once, without polling`() = runTest {
        val expired = initBody().replace("${now.epochSeconds + 600}", "${now.epochSeconds - 30}")
        server.enqueue(MockResponse(code = 200, body = expired))
        val flow = DeviceCodeFlow(zCode, fixedClock(now)) {}

        val prompt = flow.start()

        assertEquals(now, prompt.expiresAt)
        assertFailsWith<AuthException.TimedOut> { flow.awaitTokens(prompt) }
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `a sign-in page that is not https is refused`() = runTest {
        server.enqueue(MockResponse(code = 200, body = initBody("http://chat.z.ai/authorize")))
        assertFailsWith<AuthException.InvalidResponse> { zCode.requestCode() }
    }

    @Test
    fun `an envelope that is not a success is refused`() = runTest {
        server.enqueue(MockResponse(code = 200, body = """{"code":500,"msg":"busy"}"""))
        assertFailsWith<AuthException.InvalidResponse> { zCode.requestCode() }
        server.enqueue(MockResponse(code = 200, body = """{"msg":"no code"}"""))
        assertFailsWith<AuthException.InvalidResponse> { zCode.requestCode() }
    }

    @Test
    fun `polling waits while the flow is pending`() = runTest {
        server.enqueue(
            MockResponse(code = 200, body = """{"code":0,"data":{"status":"pending"}}""")
        )

        assertEquals(DevicePoll.Pending, zCode.poll(grant()))

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/api/v1/oauth/cli/poll/flow-1", request.url.encodedPath)
        assertEquals("Bearer poll-token", request.headers["Authorization"])
    }

    @Test
    fun `polling keeps going through server errors and rate limits`() = runTest {
        listOf(500, 502, 429).forEach { status ->
            server.enqueue(MockResponse(code = status, body = ""))
            assertEquals(DevicePoll.Pending, zCode.poll(grant()))
        }
    }

    @Test
    fun `a refused or failed flow ends the sign-in`() = runTest {
        server.enqueue(MockResponse(code = 403, body = ""))
        assertIs<DevicePoll.Denied>(zCode.poll(grant()))
        server.enqueue(MockResponse(code = 200, body = """{"code":0,"data":{"status":"failed"}}"""))
        assertIs<DevicePoll.Denied>(zCode.poll(grant()))
        server.enqueue(MockResponse(code = 200, body = """{"code":0,"data":{"status":"odd"}}"""))
        assertIs<DevicePoll.Denied>(zCode.poll(grant()))
    }

    @Test
    fun `a ready flow trades the Z_AI token for a business token`() = runTest {
        server.enqueue(
            MockResponse(
                code = 200,
                body =
                    """{"code":0,"data":{"status":"ready","token":"zcode-jwt",""" +
                        """"zai":{"access_token":"zai-oauth"},""" +
                        """"user":{"name":"Ada","email":"ada@example.com"}}}""",
            )
        )
        server.enqueue(
            MockResponse(
                code = 200,
                body =
                    """{"code":200,"success":true,""" +
                        """"data":{"access_token":"business","expires_in":3600}}""",
            )
        )

        val tokens = (zCode.poll(grant()) as DevicePoll.Authorized).tokens

        server.takeRequest()
        val login = server.takeRequest()
        assertEquals("/api/auth/z/login", login.url.encodedPath)
        assertEquals("""{"token":"zai-oauth"}""", login.body?.utf8())
        assertEquals(Provider.ZAi, tokens.provider)
        assertEquals(CredentialKind.OAuth, tokens.kind)
        assertEquals("business", tokens.accessToken)
        assertEquals("ada@example.com", tokens.label)
        assertEquals(now + (3600 - 300).seconds, tokens.expiresAt)
        val bundle = assertNotNull(ZCodeTokens.decode(checkNotNull(tokens.refreshToken)))
        assertEquals("zcode-jwt", bundle.zCodeJwt)
        assertEquals("zai-oauth", bundle.zAiAccessToken)
    }

    @Test
    fun `a ready flow without both tokens ends the sign-in`() = runTest {
        server.enqueue(
            MockResponse(code = 200, body = """{"code":0,"data":{"status":"ready","token":"j"}}""")
        )
        assertIs<DevicePoll.Denied>(zCode.poll(grant()))
    }

    @Test
    fun `refresh mints a new business token from the saved tokens`() = runTest {
        server.enqueue(
            MockResponse(code = 200, body = """{"code":0,"data":{"access_token":"fresh"}}""")
        )
        val bundle = ZCodeTokens(zAiAccessToken = "zai-oauth", zCodeJwt = "zcode-jwt").encode()
        val old =
            TokenSet(Provider.ZAi, CredentialKind.OAuth, "stale", bundle, now)
                .toCredential(ZCodeCredential.idFor("account"))

        val tokens = zCode.refresh(old)

        assertEquals("""{"token":"zai-oauth"}""", server.takeRequest().body?.utf8())
        assertEquals("fresh", tokens.accessToken)
        assertEquals(bundle, tokens.refreshToken)
        // No expires_in: an hour, less the margin.
        assertEquals(now + (3600 - 300).seconds, tokens.expiresAt)
    }

    @Test
    fun `a short-lived business token is never kept past its expiry`() = runTest {
        server.enqueue(
            MockResponse(
                code = 200,
                body = """{"code":0,"data":{"access_token":"brief","expires_in":10}}""",
            )
        )
        val bundle = ZCodeTokens("zai-oauth", "zcode-jwt").encode()
        val old =
            TokenSet(Provider.ZAi, CredentialKind.OAuth, "stale", bundle, now).toCredential("z")

        val tokens = zCode.refresh(old)

        // Refreshed half-way through its 10 seconds, never after them.
        assertEquals(now + 5.seconds, tokens.expiresAt)
    }

    @Test
    fun `a refused business token exchange asks for a new sign-in`() = runTest {
        server.enqueue(MockResponse(code = 200, body = """{"code":401,"msg":"expired"}"""))
        val bundle = ZCodeTokens("zai-oauth", "zcode-jwt").encode()
        val old =
            TokenSet(Provider.ZAi, CredentialKind.OAuth, "stale", bundle, now).toCredential("z")

        val failure = assertFailsWith<AuthException.Rejected> { zCode.refresh(old) }

        assertTrue(failure.requiresSignIn)
    }

    @Test
    fun `saved tokens that cannot be read ask for a new sign-in`() = runTest {
        val old =
            TokenSet(Provider.ZAi, CredentialKind.OAuth, "stale", "not json", now).toCredential("z")
        val failure = assertFailsWith<AuthException.Rejected> { zCode.refresh(old) }
        assertTrue(failure.requiresSignIn)
    }

    @Test
    fun `the ZCode credential lives next to the account's own`() {
        assertEquals("abc#zcode", ZCodeCredential.idFor("abc"))
    }
}
