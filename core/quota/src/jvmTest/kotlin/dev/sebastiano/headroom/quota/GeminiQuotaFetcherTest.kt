package dev.sebastiano.headroom.quota

import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.QuotaErrorKind
import dev.sebastiano.headroom.model.QuotaResult
import dev.sebastiano.headroom.model.WindowKind
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class GeminiQuotaFetcherTest {
    private val server = MockWebServer()
    private val fetcher = GeminiQuotaFetcher(KtorQuotaHttpClient(), FIXED_CLOCK)

    @BeforeEach
    fun setUp() {
        server.start()
    }

    @AfterEach
    fun tearDown() {
        server.close()
    }

    private fun credentials(baseUrl: String = server.baseUrl()) =
        ProviderCredentials(accessToken = "test-gemini-token", baseUrl = baseUrl)

    @Test
    fun `sends the usage request with Bearer authorization`() = runTest {
        server.enqueueJson(fixture("gemini/usage.json"))

        fetcher.fetch(credentials())

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/v1/usage", request.target)
        assertEquals("Bearer test-gemini-token", request.headers["Authorization"])
        assertEquals("application/json", request.headers["Accept"])
    }

    @Test
    fun `maps the daily and session windows`() = runTest {
        server.enqueueJson(fixture("gemini/usage.json"))

        val snapshot = assertIs<QuotaResult.Success>(fetcher.fetch(credentials())).snapshot

        assertEquals(Provider.Gemini, snapshot.provider)
        assertEquals("Gemini Code Assist Standard", snapshot.planLabel)
        assertEquals(listOf("daily", "session"), snapshot.windows.map { it.id })
        assertEquals(listOf("Daily", "Session"), snapshot.windows.map { it.label })
        assertEquals(listOf(WindowKind.Daily, WindowKind.Session), snapshot.windows.map { it.kind })
        assertEquals(listOf(1.days, 5.hours), snapshot.windows.map { it.length })
        assertEquals(listOf(30.0, 25.0), snapshot.windows.map { it.usedPercent })
        assertEquals(Instant.parse("2026-10-19T07:00:00Z"), snapshot.windows[0].resetsAt)
        assertEquals(Instant.parse("2026-10-18T20:00:00Z"), snapshot.windows[1].resetsAt)
    }

    @Test
    fun `maps a payload without usable windows to Parse`() = runTest {
        for (body in
            listOf(
                """{"limits":[]}""",
                """{"other":{}}""",
                "not-json",
            )) {
            server.enqueueJson(body)

            val failure = assertIs<QuotaResult.Failure>(fetcher.fetch(credentials()))

            assertEquals(QuotaErrorKind.Parse, failure.kind, body)
        }
    }

    @Test
    fun `maps HTTP 401 to Auth`() = runTest {
        server.enqueueStatus(401)

        val failure = assertIs<QuotaResult.Failure>(fetcher.fetch(credentials()))

        assertEquals(QuotaErrorKind.Auth, failure.kind)
        assertNull(failure.authReason)
    }

    @Test
    fun `maps transport failure to Network`() = runTest {
        val creds = credentials()
        server.close()

        val failure = assertIs<QuotaResult.Failure>(fetcher.fetch(creds))

        assertEquals(QuotaErrorKind.Network, failure.kind)
    }
}
