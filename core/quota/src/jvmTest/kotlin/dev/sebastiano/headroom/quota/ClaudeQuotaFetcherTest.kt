package dev.sebastiano.headroom.quota

import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.QuotaErrorKind
import dev.sebastiano.headroom.model.QuotaResult
import dev.sebastiano.headroom.model.WindowKind
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ClaudeQuotaFetcherTest {
    private val server = MockWebServer()
    private val fetcher = ClaudeQuotaFetcher(KtorQuotaHttpClient(), FIXED_CLOCK)

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
        assertEquals("claude-cli/${ClaudeCodeIdentity.VERSION}", usage.headers["User-Agent"])
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
        assertEquals(5.hours, session.length)
        assertEquals(Instant.parse("2026-04-03T17:00:00Z"), session.resetsAt)
        val fable = snapshot.windows[2]
        assertEquals(23.0, fable.usedPercent)
        assertEquals(7.days, fable.length)
        assertEquals(Instant.parse("2026-04-08T09:00:00Z"), fable.resetsAt)
        val unknown = snapshot.windows.last()
        assertNull(unknown.length)
        assertNull(unknown.resetsAt)
        assertFalse(unknown.isRecognised)
        assertTrue(snapshot.windows.dropLast(1).all { it.isRecognised })
    }

    @Test
    fun `maps the cloud session credit to a credit that expires, with its dollars`() = runTest {
        server.enqueueJson(fixture("claude/usage_credits.json"))
        server.enqueueJson("{}")

        val snapshot = assertIs<QuotaResult.Success>(fetcher.fetch(credentials())).snapshot

        val credit = snapshot.windows.single { it.id == "iguana_necktie" }
        assertEquals("Cloud session credit", credit.label)
        assertEquals(WindowKind.Credit, credit.kind)
        assertEquals(40.8, credit.usedPercent)
        assertNull(credit.resetsAt)
        assertEquals(Instant.parse("2026-11-05T07:59:00Z"), credit.expiresAt)
        assertNull(credit.length)
        assertEquals(102.0, credit.usedAmount)
        assertEquals(250.0, credit.limitAmount)
        assertEquals("USD", credit.amountUnit)
        assertTrue(credit.isRecognised)
    }

    @Test
    fun `maps a credit without dollars to its percentage`() = runTest {
        server.enqueueJson(fixture("claude/usage_credits.json"))
        server.enqueueJson("{}")

        val snapshot = assertIs<QuotaResult.Success>(fetcher.fetch(credentials())).snapshot

        val credit = snapshot.windows.single { it.id == "cinder_cove" }
        assertEquals("Claude Code and Cowork credit", credit.label)
        assertEquals(WindowKind.Credit, credit.kind)
        assertEquals(15.0, credit.usedPercent)
        assertNull(credit.resetsAt)
        assertEquals(Instant.parse("2026-12-01T08:00:00Z"), credit.expiresAt)
        assertNull(credit.usedAmount)
        assertNull(credit.limitAmount)
        assertNull(credit.amountUnit)
        assertTrue(credit.isRecognised)
    }

    @Test
    fun `works out a credit's percentage from its dollars when utilization is missing`() = runTest {
        server.enqueueJson(
            """{"iguana_necktie":{"utilization":null,"resets_at":null,""" +
                """"limit_dollars":100,"used_dollars":25}}"""
        )
        server.enqueueJson("{}")

        val snapshot = assertIs<QuotaResult.Success>(fetcher.fetch(credentials())).snapshot

        val credit = snapshot.windows.single()
        assertEquals(25.0, credit.usedPercent)
        assertNull(credit.expiresAt)
    }

    @Test
    fun `keeps an unknown key without dollars as an unrecognised window`() = runTest {
        server.enqueueJson(fixture("claude/usage_credits.json"))
        server.enqueueJson("{}")

        val snapshot = assertIs<QuotaResult.Success>(fetcher.fetch(credentials())).snapshot

        val unknown = snapshot.windows.single { it.id == "nimbus_quill" }
        assertEquals("Nimbus quill", unknown.label)
        assertEquals(WindowKind.Other, unknown.kind)
        assertEquals(0.0, unknown.usedPercent)
        assertNull(unknown.resetsAt)
        assertNull(unknown.expiresAt)
        assertNull(unknown.usedAmount)
        assertFalse(unknown.isRecognised)
    }

    @Test
    fun `maps an unknown key with a dollar limit to a generic credit`() = runTest {
        server.enqueueJson(fixture("claude/usage_credits.json"))
        server.enqueueJson("{}")

        val snapshot = assertIs<QuotaResult.Success>(fetcher.fetch(credentials())).snapshot

        val credit = snapshot.windows.single { it.id == "harbor_lantern" }
        assertEquals("Credit · Harbor lantern", credit.label)
        assertEquals(WindowKind.Credit, credit.kind)
        assertEquals(20.0, credit.usedPercent)
        assertNull(credit.resetsAt)
        assertEquals(Instant.parse("2027-01-15T00:00:00Z"), credit.expiresAt)
        assertEquals(10.0, credit.usedAmount)
        assertEquals(50.0, credit.limitAmount)
        assertEquals("USD", credit.amountUnit)
        assertFalse(credit.isRecognised)
    }

    @Test
    fun `keeps the credits and unknown keys in the order the response lists them`() = runTest {
        server.enqueueJson(fixture("claude/usage_credits.json"))
        server.enqueueJson("{}")

        val snapshot = assertIs<QuotaResult.Success>(fetcher.fetch(credentials())).snapshot

        assertEquals(
            listOf(
                "five_hour",
                "seven_day",
                "iguana_necktie",
                "cinder_cove",
                "nimbus_quill",
                "harbor_lantern",
            ),
            snapshot.windows.map { it.id },
        )
    }

    @Test
    fun `takes every scoped weekly limit by its kind, after the all-models weekly window`() =
        runTest {
            server.enqueueJson(fixture("claude/usage_limits_scoped.json"))
            server.enqueueJson("{}")

            val snapshot = assertIs<QuotaResult.Success>(fetcher.fetch(credentials())).snapshot

            assertEquals(
                listOf(
                    "five_hour",
                    "seven_day",
                    "weekly_scoped_fable",
                    "weekly_scoped_haiku",
                    "weekly_scoped_claude_code",
                    "seven_day_opus",
                ),
                snapshot.windows.map { it.id },
            )
            assertEquals(
                listOf(
                    "Session",
                    "Weekly · all models",
                    "Weekly · Fable",
                    "Weekly · Haiku",
                    "Weekly · Claude Code",
                    "Weekly (Opus)",
                ),
                snapshot.windows.map { it.label },
            )
            val haiku = snapshot.windows.single { it.id == "weekly_scoped_haiku" }
            assertEquals(WindowKind.Weekly, haiku.kind)
            assertEquals(9.0, haiku.usedPercent)
            assertEquals(7.days, haiku.length)
            assertEquals(Instant.parse("2026-10-07T10:00:00Z"), haiku.resetsAt)
            assertTrue(haiku.isRecognised)
        }

    @Test
    fun `a scoped limit never shows twice when a flat weekly key covers the same model`() =
        runTest {
            server.enqueueJson(fixture("claude/usage_limits_scoped.json"))
            server.enqueueJson("{}")

            val snapshot = assertIs<QuotaResult.Success>(fetcher.fetch(credentials())).snapshot

            val opus = snapshot.windows.filter { it.label.contains("Opus") }
            assertEquals(listOf("seven_day_opus"), opus.map { it.id })
            assertEquals(1, snapshot.windows.count { it.label.contains("Haiku") })
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
