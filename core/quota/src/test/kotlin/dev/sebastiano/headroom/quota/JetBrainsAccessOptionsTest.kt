package dev.sebastiano.headroom.quota

import dev.sebastiano.headroom.model.QuotaResult
import dev.sebastiano.headroom.model.QuotaSnapshot
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * The JetBrains AI license comes from the account's AI access options when the sign-in has a
 * refresh token, and from the free grazie-lite license otherwise.
 */
class JetBrainsAccessOptionsTest {
    private val server = MockWebServer()
    private val logs = mutableListOf<String>()
    private val requests = CopyOnWriteArrayList<RecordedRequest>()
    private val fetcher =
        JetBrainsQuotaFetcher(OkHttpQuotaHttpClient(), FIXED_CLOCK, log = { logs += it })

    @BeforeEach
    fun setUp() {
        server.start()
    }

    @AfterEach
    fun tearDown() {
        server.close()
    }

    private fun credentials(refreshToken: String? = REFRESH_TOKEN) =
        ProviderCredentials(
            accessToken = ACCESS_TOKEN,
            baseUrl = server.baseUrl(),
            idToken = ID_TOKEN,
            refreshToken = refreshToken,
        )

    /**
     * Answers like the JetBrains services. The access exchange returns a different JetBrains AI
     * token for each license, and the quota answer depends on that token, so a test can tell which
     * license was read.
     */
    private fun routes(
        switchAudience: MockResponse = jsonResponse(fixture("jetbrains/switch_audience.json")),
        options: MockResponse = jsonResponse(fixture("jetbrains/ai_access_options.json")),
        optionQuota: MockResponse = jsonResponse(fixture("jetbrains/quota_get.json")),
        liteQuota: MockResponse = jsonResponse(LITE_QUOTA),
    ) {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    requests += request
                    return when (request.target) {
                        AUTH_TEST -> jsonResponse(fixture("jetbrains/auth_test.json"))
                        TOKEN -> switchAudience
                        OPTIONS -> options
                        LICENSE -> jsonResponse(fixture("jetbrains/license_obtain.json"))
                        ACCESS -> accessFor(request.body?.utf8().orEmpty())
                        QUOTA ->
                            when (request.headers["Grazie-Authenticate-JWT"]) {
                                OPTION_JWT -> optionQuota
                                LITE_JWT -> liteQuota
                                else -> MockResponse(code = 401)
                            }
                        REFILL -> jsonResponse(fixture("jetbrains/quota_refill.json"))
                        else -> MockResponse(code = 404)
                    }
                }
            }
    }

    private fun accessFor(body: String): MockResponse =
        when {
            OPTION_LICENSE in body -> jsonResponse("""{"token":"$OPTION_JWT"}""")
            LITE_LICENSE in body -> jsonResponse("""{"token":"$LITE_JWT"}""")
            else -> MockResponse(code = 403)
        }

    private suspend fun fetchSnapshot(credentials: ProviderCredentials = credentials()) =
        assertIs<QuotaResult.Success>(fetcher.fetch(credentials)).snapshot

    private fun targets() = requests.map { it.target }

    private fun accessLicenses() =
        requests.filter { it.target == ACCESS }.map { it.body?.utf8().orEmpty() }

    private fun QuotaSnapshot.usedPercent() = windows.single().usedPercent

    @Test
    fun `reads the quota of the account's own license from its AI access options`() = runTest {
        routes()

        val snapshot = fetchSnapshot()

        assertEquals(25.0, snapshot.usedPercent())
        assertEquals(listOf("""{"licenseId":"$OPTION_LICENSE"}"""), accessLicenses())
        assertEquals(listOf(AUTH_TEST, TOKEN, OPTIONS, ACCESS, QUOTA, REFILL), targets())
    }

    @Test
    fun `switches the refresh token's audience like the Junie CLI does`() = runTest {
        routes()

        fetchSnapshot()

        val switch = requests.single { it.target == TOKEN }
        assertEquals("POST", switch.method)
        assertEquals("application/x-www-form-urlencoded", switch.headers["Content-Type"])
        assertEquals(
            "grant_type=switch_audience&refresh_token=$REFRESH_TOKEN" +
                "&audience=jcp-user-management&client_id=junie-cli",
            switch.body?.utf8(),
        )
        val options = requests.single { it.target == OPTIONS }
        assertEquals("GET", options.method)
        assertEquals("Bearer test-jcp-token", options.headers["Authorization"])
        assertEquals("application/json", options.headers["Accept"])
        val access = requests.single { it.target == ACCESS }
        assertEquals("Bearer $ID_TOKEN", access.headers["Authorization"])
    }

    @Test
    fun `prefers a personal license, and uses another option when there is none`() = runTest {
        routes(
            options =
                jsonResponse(
                    """{"aiAccessOptions":[""" +
                        """{"type":"workspace","aiAccessEnabled":true,"licenseId":"$OPTION_LICENSE"},""" +
                        """{"type":"license","aiAccessEnabled":false,"licenseId":"other-id"}]}"""
                )
        )

        fetchSnapshot()

        assertEquals(listOf("""{"licenseId":"$OPTION_LICENSE"}"""), accessLicenses())
        assertTrue(logs.any { "access option (workspace)" in it }, logs.toString())
    }

    @Test
    fun `uses the grazie-lite license when no option is enabled and has a license id`() = runTest {
        routes(
            options =
                jsonResponse(
                    """{"aiAccessOptions":[""" +
                        """{"type":"license","aiAccessEnabled":false,"licenseId":"$OPTION_LICENSE"},""" +
                        """{"type":"workspace","aiAccessEnabled":true,"licenseId":" "},""" +
                        """{"type":"workspace","aiAccessEnabled":true}]}"""
                )
        )

        val snapshot = fetchSnapshot()

        assertEquals(20.0, snapshot.usedPercent())
        assertEquals(listOf("""{"licenseId":"$LITE_LICENSE"}"""), accessLicenses())
    }

    @Test
    fun `uses the grazie-lite license when the audience switch fails`() = runTest {
        routes(switchAudience = jsonResponse("""{"error":"invalid_grant"}""", code = 400))

        val snapshot = fetchSnapshot()

        assertEquals(20.0, snapshot.usedPercent())
        assertEquals(listOf(AUTH_TEST, TOKEN, LICENSE, ACCESS, QUOTA, REFILL), targets())
        assertTrue(
            logs.any { "switch-audience" in it && "HTTP 400" in it && "invalid_grant" in it },
            logs.toString(),
        )
    }

    @Test
    fun `uses the grazie-lite license when the audience switch has no access token`() = runTest {
        routes(switchAudience = jsonResponse("""{"token_type":"Bearer"}"""))

        assertEquals(20.0, fetchSnapshot().usedPercent())
        assertTrue(OPTIONS !in targets(), targets().toString())
    }

    @Test
    fun `uses the grazie-lite license when the access options request fails`() = runTest {
        for (options in
            listOf(
                MockResponse(code = 500, body = "oops"),
                jsonResponse("not json"),
                jsonResponse("""{"aiAccessOptions":"none"}"""),
            )) {
            requests.clear()
            routes(options = options)

            assertEquals(20.0, fetchSnapshot().usedPercent())
            assertEquals(listOf("""{"licenseId":"$LITE_LICENSE"}"""), accessLicenses())
        }
    }

    @Test
    fun `tries the grazie-lite license when the account's license has no quota`() = runTest {
        routes(
            optionQuota =
                jsonResponse(
                    """{"current":{"current":{"amount":"0.0"},"maximum":{"amount":"0.0"}}}"""
                )
        )

        val snapshot = fetchSnapshot()

        assertEquals(20.0, snapshot.usedPercent())
        assertEquals(
            listOf("""{"licenseId":"$OPTION_LICENSE"}""", """{"licenseId":"$LITE_LICENSE"}"""),
            accessLicenses(),
        )
    }

    @Test
    fun `gives up after the account's license and the grazie-lite license`() = runTest {
        val empty = """{"current":{"current":{"amount":"0.0"},"maximum":{"amount":"0.0"}}}"""
        routes(optionQuota = jsonResponse(empty), liteQuota = jsonResponse(empty))

        val snapshot = fetchSnapshot()

        assertEquals(emptyList(), snapshot.windows)
        assertEquals(2, accessLicenses().size)
        assertEquals(1, targets().count { it == LICENSE })
    }

    @Test
    fun `without a refresh token, goes straight to the grazie-lite license`() = runTest {
        routes()

        assertEquals(20.0, fetchSnapshot(credentials(refreshToken = null)).usedPercent())
        assertEquals(listOf(AUTH_TEST, LICENSE, ACCESS, QUOTA, REFILL), targets())
    }

    @Test
    fun `logs the steps, the option types and the license used, and never a secret`() = runTest {
        routes()

        fetchSnapshot()

        for (step in listOf("switch-audience", "ai-access-options")) {
            assertTrue(logs.any { step in it && "HTTP 200" in it }, "$step in $logs")
        }
        assertTrue(logs.any { "ai-access-options shape:" in it }, logs.toString())
        assertTrue(
            logs.any {
                "workspace enabled=true" in it &&
                    "license enabled=false" in it &&
                    "license enabled=true" in it
            },
            logs.toString(),
        )
        assertTrue(logs.any { "new refresh token" in it }, logs.toString())
        assertTrue(logs.any { "Using the access option (license) license" in it }, logs.toString())
        logs.assertNoSecrets()
    }

    @Test
    fun `logs the grazie-lite fallback and never a secret`() = runTest {
        routes(
            optionQuota =
                jsonResponse(
                    """{"current":{"current":{"amount":"0.0"},"maximum":{"amount":"0.0"}}}"""
                )
        )

        fetchSnapshot()

        assertTrue(logs.any { "Using the grazie-lite license" in it }, logs.toString())
        logs.assertNoSecrets()
    }

    @Test
    fun `logs no secret when the new steps fail`() = runTest {
        routes(
            switchAudience =
                jsonResponse(
                    """{"error":"invalid_grant","error_description":"$REFRESH_TOKEN is bad"}""",
                    code = 400,
                )
        )
        fetchSnapshot()
        routes(options = MockResponse(code = 403, body = """{"message":"sam@example.com"}"""))
        fetchSnapshot()

        logs.assertNoSecrets()
    }

    private fun List<String>.assertNoSecrets() {
        for (secret in
            listOf(
                ID_TOKEN,
                ACCESS_TOKEN,
                REFRESH_TOKEN,
                "test-new-refresh-token",
                "test-jcp-token",
                OPTION_LICENSE,
                LITE_LICENSE,
                "test-workspace-license-id",
                "test-disabled-license-id",
                OPTION_JWT,
                LITE_JWT,
                "test-org-id",
                "test-workspace-id",
                "Sam Example",
                "sam@example.com",
                "Example Org",
                "Example Team AI",
                "Junie Pro",
            )) {
            assertTrue(none { secret in it }, "$secret leaked into $this")
        }
    }

    private companion object {
        const val ID_TOKEN = "test-id-token"
        const val ACCESS_TOKEN = "test-access-token"
        const val REFRESH_TOKEN = "test-refresh-token"
        const val OPTION_LICENSE = "test-junie-license-id"
        const val LITE_LICENSE = "test-license-id"
        const val OPTION_JWT = "test-option-grazie-jwt"
        const val LITE_JWT = "test-lite-grazie-jwt"
        const val LITE_QUOTA =
            """{"current":{"current":{"amount":"10"},"maximum":{"amount":"50"}}}"""

        const val AUTH_TEST = "/auth/test"
        const val TOKEN = "/oauth2/token"
        const val OPTIONS = "/user-management/api/ai-access-options"
        const val LICENSE = "/auth/jetbrains-jwt/license/obtain/grazie-lite"
        const val ACCESS = "/auth/jetbrains-jwt/provide-access/license/v2"
        const val QUOTA = "/user/v5/quota/get"
        const val REFILL = "/user/v5/quota/metadata/refill"
    }
}
