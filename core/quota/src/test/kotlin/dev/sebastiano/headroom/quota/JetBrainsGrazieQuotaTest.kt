package dev.sebastiano.headroom.quota

import dev.sebastiano.headroom.model.QuotaBalance
import dev.sebastiano.headroom.model.QuotaResult
import dev.sebastiano.headroom.model.QuotaSnapshot
import dev.sebastiano.headroom.model.QuotaWindow
import dev.sebastiano.headroom.model.WindowKind
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** The JetBrains AI (Grazie) quota window, read with the ID token of the sign-in. */
class JetBrainsGrazieQuotaTest {
    private val server = MockWebServer()
    private val logs = mutableListOf<String>()
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

    private fun credentials(idToken: String? = ID_TOKEN) =
        ProviderCredentials(
            accessToken = "test-access-token",
            baseUrl = server.baseUrl(),
            idToken = idToken,
        )

    private fun routes(
        license: MockResponse = jsonResponse(fixture("jetbrains/license_obtain.json")),
        access: MockResponse = jsonResponse(fixture("jetbrains/provide_access.json")),
        quota: MockResponse = jsonResponse(fixture("jetbrains/quota_get.json")),
        refill: MockResponse = jsonResponse(fixture("jetbrains/quota_refill.json")),
    ) {
        server.respondByTarget(
            mapOf(
                AUTH_TEST to jsonResponse(fixture("jetbrains/auth_test.json")),
                LICENSE to license,
                ACCESS to access,
                QUOTA to quota,
                REFILL to refill,
            )
        )
    }

    private suspend fun fetchSnapshot(credentials: ProviderCredentials = credentials()) =
        assertIs<QuotaResult.Success>(fetcher.fetch(credentials)).snapshot

    private fun QuotaSnapshot.assertBalanceOnly() {
        assertEquals(emptyList(), windows)
        assertEquals("JetBrains AI Pro", planLabel)
        assertEquals(QuotaBalance(20.0, "AI Credits"), balance)
    }

    @Test
    fun `shows the used share of the monthly quota next to the plan and the balance`() = runTest {
        routes()

        val snapshot = fetchSnapshot()

        assertEquals(
            listOf(
                QuotaWindow(
                    id = "ai_credits",
                    label = "Monthly",
                    kind = WindowKind.Monthly,
                    usedPercent = 25.0,
                    resetsAt = Instant.parse("2026-05-01T00:00:00Z"),
                    length = Duration.ofHours(720),
                )
            ),
            snapshot.windows,
        )
        assertEquals("JetBrains AI Pro", snapshot.planLabel)
        assertEquals(QuotaBalance(20.0, "AI Credits"), snapshot.balance)
        assertTrue(logs.any { it.startsWith("quota/get amounts: {") }, logs.toString())
    }

    @Test
    fun `trades the ID token for a license, then for a JetBrains AI token`() = runTest {
        routes()

        fetcher.fetch(credentials())

        assertEquals(AUTH_TEST, server.nextRequest().target)
        val license = server.nextRequest()
        assertEquals(LICENSE, license.target)
        assertEquals("POST", license.method)
        assertEquals("Bearer $ID_TOKEN", license.headers["Authorization"])
        assertEquals("application/json", license.headers["Accept"])
        assertEquals("application/json", license.headers["Content-Type"])
        assertEquals("{}", license.body?.utf8())
        val access = server.nextRequest()
        assertEquals(ACCESS, access.target)
        assertEquals("POST", access.method)
        assertEquals("Bearer $ID_TOKEN", access.headers["Authorization"])
        assertEquals("application/json", access.headers["Content-Type"])
        assertEquals("""{"licenseId":"test-license-id"}""", access.body?.utf8())
    }

    @Test
    fun `reads the quota and the refill with the JetBrains AI token`() = runTest {
        routes()

        fetcher.fetch(credentials())

        repeat(3) { server.nextRequest() }
        for (target in listOf(QUOTA, REFILL)) {
            val request = server.nextRequest()
            assertEquals(target, request.target)
            assertEquals("POST", request.method)
            assertEquals("test-grazie-jwt", request.headers["Grazie-Authenticate-JWT"])
            assertEquals("""{"name":"headroom","version":"1"}""", request.headers["Grazie-Agent"])
            assertEquals("application/json", request.headers["Content-Type"])
            assertNull(request.headers["Authorization"])
            assertEquals("{}", request.body?.utf8())
        }
    }

    @Test
    fun `a seven day refill period is a weekly window, and other periods are Other`() = runTest {
        for ((millis, kind) in
            listOf(
                Duration.ofDays(7).toMillis() to WindowKind.Weekly,
                Duration.ofDays(1).toMillis() to WindowKind.Other,
                Duration.ofDays(90).toMillis() to WindowKind.Other,
            )) {
            routes(
                refill =
                    jsonResponse(
                        """{"current":{"tariff":{"period":{"millis":$millis}},"next":1777593600000}}"""
                    )
            )

            val window = fetchSnapshot().windows.single()

            assertEquals(kind, window.kind, "period $millis")
            assertEquals(Duration.ofMillis(millis), window.length)
        }
    }

    @Test
    fun `labels a weekly window Weekly`() = runTest {
        routes(
            refill =
                jsonResponse(
                    """{"current":{"tariff":{"period":{"millis":604800000}},"next":1777593600000}}"""
                )
        )

        assertEquals("Weekly", fetchSnapshot().windows.single().label)
    }

    @Test
    fun `falls back to the quota end when the refill has no next date`() = runTest {
        routes(refill = jsonResponse("""{"current":{"tariff":{"period":{"millis":2592000000}}}}"""))

        val window = fetchSnapshot().windows.single()

        assertEquals(Instant.parse("2026-04-30T12:00:00Z"), window.resetsAt)
    }

    @Test
    fun `accepts credits as plain numbers`() = runTest {
        routes(
            quota =
                jsonResponse("""{"current":{"current":12.5,"maximum":50,"until":1777550400000}}""")
        )

        assertEquals(25.0, fetchSnapshot().windows.single().usedPercent)
    }

    @Test
    fun `parses a credit object or a plain number`() {
        assertEquals(12.5, parseCredit(quotaJson.parseToJsonElement("""{"amount":"12.5"}""")))
        assertEquals(12.5, parseCredit(quotaJson.parseToJsonElement("""{"amount":12.5}""")))
        assertEquals(12.5, parseCredit(quotaJson.parseToJsonElement("12.5")))
        assertEquals(12.5, parseCredit(quotaJson.parseToJsonElement("\"12.5\"")))
        assertNull(parseCredit(quotaJson.parseToJsonElement("""{"value":"12.5"}""")))
        assertNull(parseCredit(quotaJson.parseToJsonElement("\"lots\"")))
        assertNull(parseCredit(null))
    }

    @Test
    fun `clamps a quota that is over its maximum to 100 percent`() = runTest {
        routes(
            quota =
                jsonResponse(
                    """{"current":{"current":{"amount":"60"},"maximum":{"amount":"50"}}}"""
                )
        )

        assertEquals(100.0, fetchSnapshot().windows.single().usedPercent)
    }

    @Test
    fun `without an ID token, shows the balance only and makes no JetBrains AI calls`() = runTest {
        routes()

        val snapshot = fetchSnapshot(credentials(idToken = null))

        snapshot.assertBalanceOnly()
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `falls back to the balance when the license lookup fails`() = runTest {
        routes(license = MockResponse(code = 403, body = """{"error":"no license"}"""))

        fetchSnapshot().assertBalanceOnly()
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `falls back to the balance when the license lookup has no license id`() = runTest {
        routes(license = jsonResponse("""{"license":{}}"""))

        fetchSnapshot().assertBalanceOnly()
    }

    @Test
    fun `falls back to the balance when the access exchange fails`() = runTest {
        routes(access = MockResponse(code = 401))

        fetchSnapshot().assertBalanceOnly()
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `falls back to the balance when the quota cannot be found`() = runTest {
        routes(quota = MockResponse(code = 404, body = "Quota could not be found"))

        fetchSnapshot().assertBalanceOnly()
    }

    @Test
    fun `falls back to the balance when the refill request fails`() = runTest {
        routes(refill = MockResponse(code = 500))

        fetchSnapshot().assertBalanceOnly()
    }

    @Test
    fun `falls back to the balance when the quota has no maximum or is not JSON`() = runTest {
        for (body in
            listOf(
                """{"current":{"current":{"amount":"1"}}}""",
                """{"current":{"current":{"amount":"1"},"maximum":{"amount":"0"}}}""",
                """{"current":null}""",
                "not json",
            )) {
            routes(quota = jsonResponse(body))

            fetchSnapshot().assertBalanceOnly()
        }
    }

    @Test
    fun `falls back to the balance when a JetBrains AI call cannot be sent`() = runTest {
        val client = FakeQuotaHttpClient { request ->
            if (request.url.endsWith(AUTH_TEST)) {
                QuotaHttpResponse(200, body = fixture("jetbrains/auth_test.json"))
            } else {
                throw IOException("offline")
            }
        }
        val offline = JetBrainsQuotaFetcher(client, FIXED_CLOCK, log = { logs += it })

        val snapshot = assertIs<QuotaResult.Success>(offline.fetch(credentials())).snapshot

        snapshot.assertBalanceOnly()
        assertTrue(logs.any { "IOException" in it }, logs.toString())
    }

    @Test
    fun `a failing balance request is still a failure`() = runTest {
        server.respondByTarget(mapOf(AUTH_TEST to MockResponse(code = 401)))

        assertIs<QuotaResult.Failure>(fetcher.fetch(credentials()))
    }

    @Test
    fun `logs each step's status code and never a token or the license id`() = runTest {
        routes()

        fetcher.fetch(credentials())

        for (step in listOf("license", "access", "quota", "refill")) {
            assertTrue(logs.any { step in it && "HTTP 200" in it }, "$step in $logs")
        }
        logs.assertNoSecrets()
    }

    @Test
    fun `logs the start of a failed step's error body`() = runTest {
        val longBody = "Quota could not be found. " + "x".repeat(400)
        routes(quota = MockResponse(code = 404, body = longBody))

        fetcher.fetch(credentials())

        val line = logs.single { "quota" in it && "HTTP 404" in it }
        assertTrue(longBody.take(200) in line, line)
        assertFalse(longBody.take(201) in line, line)
        logs.assertNoSecrets()
    }

    @Test
    fun `logs why there is no quota window when there is no ID token`() = runTest {
        routes()

        fetcher.fetch(credentials(idToken = null))

        assertTrue(logs.any { "ID token" in it }, logs.toString())
    }

    /** The next request, or a test failure when none arrives, instead of waiting forever. */
    private fun MockWebServer.nextRequest(): RecordedRequest =
        checkNotNull(takeRequest(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            "Expected another request"
        }

    private fun List<String>.assertNoSecrets() {
        for (secret in
            listOf(ID_TOKEN, "test-grazie-jwt", "test-license-id", "test-access-token")) {
            assertTrue(none { secret in it }, "$secret leaked into $this")
        }
    }

    private companion object {
        const val REQUEST_TIMEOUT_SECONDS = 5L
        const val ID_TOKEN = "test-id-token"
        const val AUTH_TEST = "/auth/test"
        const val LICENSE = "/auth/jetbrains-jwt/license/obtain/grazie-lite"
        const val ACCESS = "/auth/jetbrains-jwt/provide-access/license/v2"
        const val QUOTA = "/user/v5/quota/get"
        const val REFILL = "/user/v5/quota/metadata/refill"
    }
}
