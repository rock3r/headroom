package dev.sebastiano.headroom.quota

import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.QuotaErrorKind
import dev.sebastiano.headroom.model.QuotaResult
import dev.sebastiano.headroom.model.QuotaSnapshot
import dev.sebastiano.headroom.model.WindowKind
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class KimiQuotaFetcherTest {
    private val server = MockWebServer()
    private val fetcher = KimiQuotaFetcher(OkHttpQuotaHttpClient(), FIXED_CLOCK)

    @BeforeEach
    fun setUp() {
        server.start()
    }

    @AfterEach
    fun tearDown() {
        server.close()
    }

    private fun credentials() =
        ProviderCredentials(accessToken = "test-access-token", baseUrl = server.baseUrl())

    private suspend fun snapshotFor(body: String): QuotaSnapshot {
        server.enqueueJson(body)
        return assertIs<QuotaResult.Success>(fetcher.fetch(credentials())).snapshot
    }

    @Test
    fun `sends the usage request with the OAuth token`() = runTest {
        server.enqueueJson(fixture("kimi/usages.json"))

        fetcher.fetch(credentials())

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/v1/usages", request.target)
        assertEquals("Bearer test-access-token", request.headers["Authorization"])
        assertEquals("application/json", request.headers["Accept"])
    }

    @Test
    fun `maps the weekly usage and the rate limit windows`() = runTest {
        val snapshot = snapshotFor(fixture("kimi/usages.json"))

        assertEquals(Provider.Kimi, snapshot.provider)
        assertEquals("Allegro", snapshot.planLabel)
        assertEquals(listOf("weekly", "limit_1"), snapshot.windows.map { it.id })
        assertEquals(listOf("Weekly", "5h limit"), snapshot.windows.map { it.label })
        val weekly = snapshot.windows[0]
        assertEquals(12.0, weekly.usedPercent)
        assertEquals(WindowKind.Weekly, weekly.kind)
        assertEquals(Duration.ofDays(7), weekly.length)
        assertEquals(Instant.parse("2026-04-08T20:08:27.033436Z"), weekly.resetsAt)
        val rateLimit = snapshot.windows[1]
        assertEquals(3.0, rateLimit.usedPercent)
        assertEquals(WindowKind.Session, rateLimit.kind)
        assertEquals(Duration.ofMinutes(300), rateLimit.length)
        assertEquals(Instant.parse("2026-04-03T15:08:27.033436Z"), rateLimit.resetsAt)
    }

    @Test
    fun `maps a keyed windows payload`() = runTest {
        val snapshot = snapshotFor(fixture("kimi/usages_windows.json"))

        assertEquals("Pro", snapshot.planLabel)
        assertEquals(listOf("monthly_quota", "bonus_quota"), snapshot.windows.map { it.id })
        assertEquals(listOf("Monthly", "Bonus quota"), snapshot.windows.map { it.label })
        assertEquals(
            listOf(WindowKind.Monthly, WindowKind.Other),
            snapshot.windows.map { it.kind },
        )
        assertEquals(25.0, snapshot.windows[0].usedPercent)
        assertEquals(Instant.parse("2026-05-01T00:00:00Z"), snapshot.windows[0].resetsAt)
    }

    @Test
    fun `scales fractional utilization and resolves relative resets`() = runTest {
        val window =
            snapshotFor("""{"usage":{"utilization":0.25,"reset_in":3600}}""").windows.single()

        assertEquals(25.0, window.usedPercent)
        assertEquals(FIXED_NOW.plusSeconds(3600), window.resetsAt)
    }

    @Test
    fun `labels limits by name first, then by duration`() = runTest {
        val snapshot =
            snapshotFor(
                """
                {
                  "limits": [
                    { "name": "Burst", "detail": { "limit": "10", "used": "5" } },
                    { "duration": 2, "timeUnit": "TIME_UNIT_HOUR", "limit": 10, "used": 1 },
                    { "window": { "duration": 1, "timeUnit": "TIME_UNIT_DAY" }, "limit": 10, "remaining": 10 },
                    { "limit": 10, "used": 1 }
                  ]
                }
                """
            )

        assertEquals(
            listOf("Burst", "2h limit", "1d limit", "Limit 4"),
            snapshot.windows.map { it.label },
        )
        assertEquals(
            listOf(null, Duration.ofHours(2), Duration.ofDays(1), null),
            snapshot.windows.map { it.length },
        )
        assertEquals(listOf(50.0, 10.0, 0.0, 10.0), snapshot.windows.map { it.usedPercent })
    }

    @Test
    fun `picks the plan from plan type, then membership level`() = runTest {
        val usage = """"usage":{"limit":"100","remaining":"99"}"""

        assertEquals(
            "Prestissimo",
            snapshotFor("""{"user":{"membership":{"level":"LEVEL_PRESTISSIMO"}},$usage}""")
                .planLabel,
        )
        assertEquals(
            "Allegro",
            snapshotFor(
                    """{"plan_type":" ","user":{"membership":{"level":"LEVEL_ADVANCED"}},$usage}"""
                )
                .planLabel,
        )
        assertNull(snapshotFor("{$usage}").planLabel)
    }

    @Test
    fun `surfaces the reason of a permission denied response`() = runTest {
        server.enqueueJson(fixture("kimi/forbidden.json"), code = 403)

        val failure = assertIs<QuotaResult.Failure>(fetcher.fetch(credentials()))

        assertEquals(QuotaErrorKind.Access, failure.kind)
        assertTrue("Please subscribe to access." in failure.message)
    }

    @Test
    fun `maps other HTTP errors to error kinds`() = runTest {
        server.enqueueJson("""{"message":"token expired"}""", code = 401)
        val auth = assertIs<QuotaResult.Failure>(fetcher.fetch(credentials()))
        assertEquals(QuotaErrorKind.Auth, auth.kind)
        assertTrue("token expired" in auth.message)

        server.enqueueStatus(429)
        assertEquals(
            QuotaErrorKind.RateLimited,
            assertIs<QuotaResult.Failure>(fetcher.fetch(credentials())).kind,
        )
    }

    @Test
    fun `maps a malformed payload to Parse`() = runTest {
        server.enqueueJson("{not-json")

        val failure = assertIs<QuotaResult.Failure>(fetcher.fetch(credentials()))

        assertEquals(QuotaErrorKind.Parse, failure.kind)
    }

    @Test
    fun `maps a transport failure to Network`() = runTest {
        val credentials = credentials()
        server.close()

        val failure = assertIs<QuotaResult.Failure>(fetcher.fetch(credentials))

        assertEquals(QuotaErrorKind.Network, failure.kind)
    }
}
