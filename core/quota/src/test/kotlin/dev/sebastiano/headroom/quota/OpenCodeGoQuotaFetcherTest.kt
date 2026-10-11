package dev.sebastiano.headroom.quota

import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.QuotaErrorKind
import dev.sebastiano.headroom.model.QuotaResult
import dev.sebastiano.headroom.model.WindowKind
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class OpenCodeGoQuotaFetcherTest {
    private val server = MockWebServer()
    private val fetcher = OpenCodeGoQuotaFetcher(OkHttpQuotaHttpClient(), FIXED_CLOCK)

    @BeforeEach
    fun setUp() {
        server.start()
    }

    @AfterEach
    fun tearDown() {
        server.close()
    }

    private fun credentials(baseUrl: String = server.baseUrl()) =
        ProviderCredentials(accessToken = "test-api-key", baseUrl = baseUrl)

    @Test
    fun `sends the usage request with the API key`() = runTest {
        server.enqueueJson(fixture("opencodego/usage.json"))

        fetcher.fetch(credentials())

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/v1/usage", request.target)
        assertEquals("Bearer test-api-key", request.headers["Authorization"])
        assertEquals("application/json", request.headers["Accept"])
    }

    @Test
    fun `appends the usage path to a base URL override with a path`() = runTest {
        server.enqueueJson(fixture("opencodego/usage.json"))

        fetcher.fetch(credentials(baseUrl = server.url("/opencode-go/").toString()))

        assertEquals("/opencode-go/v1/usage", server.takeRequest().target)
    }

    @Test
    fun `maps the rolling, weekly and monthly windows`() = runTest {
        server.enqueueJson(fixture("opencodego/usage.json"))

        val snapshot = assertIs<QuotaResult.Success>(fetcher.fetch(credentials())).snapshot

        assertEquals(Provider.OpenCodeGo, snapshot.provider)
        assertEquals("OpenCode Go", snapshot.planLabel)
        assertEquals(listOf("rolling", "weekly", "monthly"), snapshot.windows.map { it.id })
        assertEquals(listOf("5 hour", "Weekly", "Monthly"), snapshot.windows.map { it.label })
        assertEquals(
            listOf(WindowKind.Session, WindowKind.Weekly, WindowKind.Monthly),
            snapshot.windows.map { it.kind },
        )
        assertEquals(
            listOf(5.hours, 7.days, 30.days),
            snapshot.windows.map { it.length },
        )
        assertEquals(listOf(25.0, 100.0, 50.5), snapshot.windows.map { it.usedPercent })
        assertEquals(Instant.parse("2026-04-03T17:00:00Z"), snapshot.windows[0].resetsAt)
    }

    @Test
    fun `maps a payload without usable windows to Parse`() = runTest {
        for (body in
            listOf(
                """{"usage":{}}""",
                """{"other":{}}""",
                """{"usage":{"rolling":{"percent":25,"resetsAt":"not-a-date"}}}""",
                "not-json",
            )) {
            server.enqueueJson(body)

            val failure = assertIs<QuotaResult.Failure>(fetcher.fetch(credentials()))

            assertEquals(QuotaErrorKind.Parse, failure.kind, body)
        }
    }

    @Test
    fun `maps HTTP errors to error kinds`() = runTest {
        server.enqueueStatus(401)

        val failure = assertIs<QuotaResult.Failure>(fetcher.fetch(credentials()))

        assertEquals(QuotaErrorKind.Auth, failure.kind)
    }

    @Test
    fun `maps a transport failure to Network`() = runTest {
        val credentials = credentials()
        server.close()

        val failure = assertIs<QuotaResult.Failure>(fetcher.fetch(credentials))

        assertEquals(QuotaErrorKind.Network, failure.kind)
    }
}
