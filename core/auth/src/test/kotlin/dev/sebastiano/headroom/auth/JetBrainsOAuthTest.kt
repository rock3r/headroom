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
import kotlin.test.assertTrue
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer

class JetBrainsOAuthTest {
    private val now = Instant.parse("2026-09-27T12:00:00Z")
    private val server = MockWebServer()
    private val io = testIoDispatcher()
    private lateinit var jetBrains: JetBrainsOAuth

    @BeforeTest
    fun setUp() {
        server.start()
        jetBrains =
            JetBrainsOAuth(
                http = OkHttpAuthHttpClient(),
                clock = Clock.fixed(now, ZoneOffset.UTC),
                tokenEndpoint = server.url("/oauth2/token").toString(),
            )
    }

    @AfterTest
    fun tearDown() {
        server.close()
        io.close()
    }

    @Test
    fun `the listener uses the registered port range on both loopback families`() {
        val config = jetBrains.loopback
        assertEquals(62345..62364, config.ports)
        assertEquals("/", config.path)
        assertTrue(config.bindIpv6)
    }

    @Test
    fun `the authorize URL uses a localhost redirect without a path`() = runTest {
        BrowserOAuthFlow(jetBrains, io).start(null).use { signIn ->
            val params = queryPairs(signIn.authorizeUrl)
            assertTrue(signIn.authorizeUrl.startsWith("https://junie.jetbrains.com/cli-auth?"))
            assertEquals(
                listOf(
                    "client_id",
                    "scope",
                    "state",
                    "code_challenge",
                    "code_challenge_method",
                    "redirect_uri",
                ),
                params.map { it.first },
            )
            val values = params.toMap()
            assertEquals("junie-cli", values["client_id"])
            assertEquals("offline_access openid jb-authn-service", values["scope"])
            assertTrue(
                Regex("^http://localhost:623(4[5-9]|5\\d|6[0-4])$")
                    .matches(values.getValue("redirect_uri"))
            )
        }
    }

    @Test
    fun `a redirect to the root path is exchanged for tokens`() = runTest {
        val idToken = fakeJwt("""{"sub":"jb-user","email":"sam@example.com"}""")
        server.enqueue(
            MockResponse(
                code = 200,
                body =
                    """{"access_token":"ja","refresh_token":"jr","expires_in":3600,"id_token":"$idToken"}""",
            )
        )
        val signIn = BrowserOAuthFlow(jetBrains, io).start(null)
        val values = queryPairs(signIn.authorizeUrl).toMap()
        val redirectUri = values.getValue("redirect_uri")
        val port = redirectUri.substringAfterLast(':')

        val browser = async {
            browserGet(io, "http://127.0.0.1:$port/?code=jc&state=${values["state"]}")
        }
        val tokens = signIn.awaitTokens()

        val form = queryPairs("?" + checkNotNull(server.takeRequest().body).utf8())
        assertEquals(
            listOf(
                "grant_type" to "authorization_code",
                "code" to "jc",
                "redirect_uri" to redirectUri,
                "client_id" to "junie-cli",
            ),
            form.take(4),
        )
        assertEquals("code_verifier", form[4].first)
        assertEquals(Provider.JetBrains, tokens.provider)
        assertEquals("ja", tokens.accessToken)
        assertEquals("jr", tokens.refreshToken)
        assertEquals(now.plus(Duration.ofMinutes(55)), tokens.expiresAt)
        assertEquals("sam@example.com", tokens.label)
        assertEquals(200, browser.await().status)
    }

    @Test
    fun `refresh posts the refresh token`() = runTest {
        server.enqueue(
            MockResponse(code = 200, body = """{"access_token":"new","expires_in":600}""")
        )
        val old =
            TokenSet(Provider.JetBrains, CredentialKind.OAuth, "old", "jr", now).toCredential("j")

        val tokens = jetBrains.refresh(old)

        val form = queryPairs("?" + checkNotNull(server.takeRequest().body).utf8())
        assertEquals(
            listOf(
                "grant_type" to "refresh_token",
                "refresh_token" to "jr",
                "client_id" to "junie-cli",
            ),
            form,
        )
        assertEquals("new", tokens.accessToken)
        assertEquals(now.plusSeconds(300), tokens.expiresAt)
    }

    @Test
    fun `a token response without expiry is refused`() = runTest {
        server.enqueue(MockResponse(code = 200, body = """{"access_token":"new"}"""))
        val old =
            TokenSet(Provider.JetBrains, CredentialKind.OAuth, "old", "jr", now).toCredential("j")
        assertFailsWith<AuthException.InvalidResponse> { jetBrains.refresh(old) }
    }
}
