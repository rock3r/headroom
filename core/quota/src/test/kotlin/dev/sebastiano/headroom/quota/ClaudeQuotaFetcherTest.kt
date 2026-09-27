package dev.sebastiano.headroom.quota

import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.QuotaErrorKind
import dev.sebastiano.headroom.model.QuotaResult
import dev.sebastiano.headroom.model.WindowKind
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ClaudeQuotaFetcherTest {
    private val server = MockWebServer()
    private val fetcher = ClaudeQuotaFetcher(OkHttpQuotaHttpClient(), FIXED_CLOCK)

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

    @Test
    fun `sends the usage and profile requests with the expected headers`() = runTest {
        server.enqueueJson(fixture("claude/usage.json"))
        server.enqueueJson(fixture("claude/profile_max_20x.json"))

        fetcher.fetch(credentials())

        val usage = server.takeRequest()
        assertEquals("GET", usage.method)
        assertEquals("/api/oauth/usage", usage.target)
        assertEquals("Bearer test-access-token", usage.headers["Authorization"])
        assertEquals("application/json", usage.headers["Accept"])
        assertEquals("claude-code-20250219,oauth-2025-04-20", usage.headers["anthropic-beta"])
        assertEquals("claude-cli/2.1.281", usage.headers["User-Agent"])
        assertEquals("cli", usage.headers["x-app"])
        val profile = server.takeRequest()
        assertEquals("/api/oauth/profile", profile.target)
        assertEquals("Bearer test-access-token", profile.headers["Authorization"])
        assertEquals("application/json", profile.headers["Content-Type"])
    }

    @Test
    fun `maps the usage windows with labels, kinds and lengths`() = runTest {
        server.enqueueJson(fixture("claude/usage.json"))
        server.enqueueJson(fixture("claude/profile_max_20x.json"))

        val snapshot = assertIs<QuotaResult.Success>(fetcher.fetch(credentials())).snapshot

        assertEquals(Provider.Claude, snapshot.provider)
        assertEquals(FIXED_NOW, snapshot.fetchedAt)
        assertEquals(
            listOf(
                "five_hour",
                "seven_day",
                "weekly_scoped_fable",
                "seven_day_opus",
                "seven_day_sonnet",
                "future_window",
            ),
            snapshot.windows.map { it.id },
        )
        assertEquals(
            listOf(
                "Session",
                "Weekly · all models",
                "Weekly · Fable",
                "Weekly (Opus)",
                "Weekly (Sonnet)",
                "Future window",
            ),
            snapshot.windows.map { it.label },
        )
        assertEquals(
            listOf(
                WindowKind.Session,
                WindowKind.Weekly,
                WindowKind.Weekly,
                WindowKind.Weekly,
                WindowKind.Weekly,
                WindowKind.Other,
            ),
            snapshot.windows.map { it.kind },
        )
        val session = snapshot.windows[0]
        assertEquals(38.0, session.usedPercent)
        assertEquals(Duration.ofHours(5), session.length)
        assertEquals(Instant.parse("2026-04-03T17:00:00Z"), session.resetsAt)
        val fable = snapshot.windows[2]
        assertEquals(23.0, fable.usedPercent)
        assertEquals(Duration.ofDays(7), fable.length)
        assertEquals(Instant.parse("2026-04-08T09:00:00Z"), fable.resetsAt)
        val unknown = snapshot.windows.last()
        assertNull(unknown.length)
        assertNull(unknown.resetsAt)
    }

    @Test
    fun `takes the plan label from the profile`() = runTest {
        server.enqueueJson(fixture("claude/usage.json"))
        server.enqueueJson(fixture("claude/profile_max_20x.json"))

        val snapshot = assertIs<QuotaResult.Success>(fetcher.fetch(credentials())).snapshot

        assertEquals("Max 20x", snapshot.planLabel)
    }

    @Test
    fun `falls back to the usage plan type when the profile call fails`() = runTest {
        server.enqueueJson(fixture("claude/usage_extra_enabled.json"))
        server.enqueueStatus(500)

        val snapshot = assertIs<QuotaResult.Success>(fetcher.fetch(credentials())).snapshot

        assertEquals("Pro", snapshot.planLabel)
        assertEquals(listOf("five_hour", "extra_usage"), snapshot.windows.map { it.id })
    }

    @Test
    fun `keeps the windows when the profile payload is malformed`() = runTest {
        server.enqueueJson(fixture("claude/usage.json"))
        server.enqueueJson("{not-json")

        val snapshot = assertIs<QuotaResult.Success>(fetcher.fetch(credentials())).snapshot

        assertNull(snapshot.planLabel)
        assertEquals(6, snapshot.windows.size)
    }

    @Test
    fun `maps enabled extra usage to a monthly window`() = runTest {
        server.enqueueJson(fixture("claude/usage_extra_enabled.json"))
        server.enqueueJson("{}")

        val snapshot = assertIs<QuotaResult.Success>(fetcher.fetch(credentials())).snapshot

        val extra = snapshot.windows.single { it.id == "extra_usage" }
        assertEquals("Extra usage", extra.label)
        assertEquals(25.0, extra.usedPercent)
        assertEquals(WindowKind.Monthly, extra.kind)
        assertNull(extra.resetsAt)
    }

    @Test
    fun `an empty usage payload is a snapshot without windows`() = runTest {
        server.enqueueJson("{}")
        server.enqueueJson("{}")

        val snapshot = assertIs<QuotaResult.Success>(fetcher.fetch(credentials())).snapshot

        assertEquals(emptyList(), snapshot.windows)
    }

    @Test
    fun `ignores top level entries that are not objects`() = runTest {
        server.enqueueJson("""{"five_hour":"not-an-object"}""")
        server.enqueueJson("{}")

        val snapshot = assertIs<QuotaResult.Success>(fetcher.fetch(credentials())).snapshot

        assertEquals(emptyList(), snapshot.windows)
    }

    @Test
    fun `maps HTTP errors to error kinds`() = runTest {
        for ((status, kind) in
            listOf(
                401 to QuotaErrorKind.Auth,
                403 to QuotaErrorKind.Access,
                429 to QuotaErrorKind.RateLimited,
                500 to QuotaErrorKind.Unknown,
            )) {
            server.enqueueStatus(status)

            val failure = assertIs<QuotaResult.Failure>(fetcher.fetch(credentials()))

            assertEquals(kind, failure.kind, "HTTP $status")
        }
    }

    @Test
    fun `maps a malformed payload to Parse`() = runTest {
        server.enqueueJson("{not-json")

        val failure = assertIs<QuotaResult.Failure>(fetcher.fetch(credentials()))

        assertEquals(QuotaErrorKind.Parse, failure.kind)
    }

    @Test
    fun `maps a malformed reset timestamp to Parse`() = runTest {
        server.enqueueJson("""{"five_hour":{"utilization":25.0,"resets_at":"not-an-instant"}}""")

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

    @Test
    fun `derives plan labels from the organization type and rate limit tier`() {
        assertEquals("Max 20x", claudePlanLabel("claude_max", "default_claude_max_20x"))
        assertEquals("Max 5x", claudePlanLabel("claude_max", "default_claude_max_5x"))
        assertEquals("Max", claudePlanLabel("claude_max", "some_future_tier"))
        assertEquals("Pro", claudePlanLabel("claude_pro", null))
        assertEquals("Team", claudePlanLabel("claude_team", null))
        assertEquals("Enterprise", claudePlanLabel("claude_enterprise", null))
        assertEquals("Future Plan Name", claudePlanLabel("claude_future_plan_name", null))
        assertNull(claudePlanLabel(" ", null))
        assertNull(claudePlanLabel(null, null))
    }
}
