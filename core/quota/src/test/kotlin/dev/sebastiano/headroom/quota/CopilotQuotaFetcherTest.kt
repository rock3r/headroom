package dev.sebastiano.headroom.quota

import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.QuotaErrorKind
import dev.sebastiano.headroom.model.QuotaResult
import dev.sebastiano.headroom.model.QuotaWindow
import dev.sebastiano.headroom.model.WindowKind
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CopilotQuotaFetcherTest {
    private val server = MockWebServer()
    private val fetcher = CopilotQuotaFetcher(OkHttpQuotaHttpClient(), FIXED_CLOCK)

    @BeforeEach
    fun setUp() {
        server.start()
    }

    @AfterEach
    fun tearDown() {
        server.close()
    }

    private fun credentials() =
        ProviderCredentials(accessToken = "test-github-oauth-token", baseUrl = server.baseUrl())

    private suspend fun windowsFor(body: String): List<QuotaWindow> {
        server.enqueueJson(body)
        return assertIs<QuotaResult.Success>(fetcher.fetch(credentials())).snapshot.windows
    }

    @Test
    fun `sends the GitHub OAuth token with the editor identity headers`() = runTest {
        server.enqueueJson(fixture("copilot/user.json"))

        fetcher.fetch(credentials())

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/copilot_internal/user", request.target)
        assertEquals("Bearer test-github-oauth-token", request.headers["Authorization"])
        assertEquals("application/json", request.headers["Accept"])
        assertEquals("GitHubCopilotChat/0.35.0", request.headers["User-Agent"])
        assertEquals("vscode/1.107.0", request.headers["Editor-Version"])
        assertEquals("copilot-chat/0.35.0", request.headers["Editor-Plugin-Version"])
        assertEquals("vscode-chat", request.headers["Copilot-Integration-Id"])
    }

    @Test
    fun `maps the quota snapshots to monthly windows`() = runTest {
        server.enqueueJson(fixture("copilot/user.json"))

        val snapshot = assertIs<QuotaResult.Success>(fetcher.fetch(credentials())).snapshot

        assertEquals(Provider.Copilot, snapshot.provider)
        assertEquals("octocat-test", snapshot.accountId)
        assertEquals("Individual", snapshot.planLabel)
        assertEquals(
            listOf("premium_interactions", "chat", "completions"),
            snapshot.windows.map { it.id },
        )
        assertEquals(
            listOf("AI Credits", "Chat", "Completions"),
            snapshot.windows.map { it.label },
        )
        assertTrue(snapshot.windows.all { it.kind == WindowKind.Monthly })
        assertTrue(snapshot.windows.all { it.length == 30.days })
        assertTrue(snapshot.windows.all { it.resetsAt == Instant.parse("2026-05-01T00:00:00Z") })
        val credits = snapshot.windows[0]
        assertEquals(2.4, credits.usedPercent, absoluteTolerance = 0.0001)
        assertFalse(credits.isUnlimited)
        assertEquals(listOf(true, true), snapshot.windows.drop(1).map { it.isUnlimited })
    }

    @Test
    fun `derives the percentage from remaining and entitlement, with a per-window reset`() =
        runTest {
            val window =
                windowsFor(
                        """
                        {
                          "quota_snapshots": {
                            "premium_interactions": {
                              "remaining": 25,
                              "entitlement": 100,
                              "quota_reset_at": 1777593600
                            }
                          }
                        }
                        """
                    )
                    .single()

            assertEquals(75.0, window.usedPercent)
            assertEquals(Instant.fromEpochSeconds(1_777_593_600), window.resetsAt)
        }

    @Test
    fun `derives the percentage from used and entitlement`() = runTest {
        val window =
            windowsFor(
                    """{"quota_snapshots":{"premium_interactions":{"used":25,"entitlement":100}}}"""
                )
                .single()

        assertEquals(25.0, window.usedPercent)
    }

    @Test
    fun `prefers the precise remaining amount over the rounded one`() = runTest {
        val window =
            windowsFor(
                    """
                    {
                      "quota_snapshots": {
                        "premium_interactions": {
                          "quota_remaining": 93.5,
                          "remaining": 93,
                          "entitlement": 300
                        }
                      }
                    }
                    """
                )
                .single()

        assertEquals(68.8333, window.usedPercent, absoluteTolerance = 0.0001)
    }

    @Test
    fun `treats a negative entitlement as unlimited and drops zero placeholders`() = runTest {
        val windows =
            windowsFor(
                """
                {
                  "quota_snapshots": {
                    "chat": { "entitlement": -1 },
                    "completions": { "remaining": 0, "entitlement": 0 }
                  }
                }
                """
            )

        assertEquals(listOf("chat"), windows.map { it.id })
        assertTrue(windows.single().isUnlimited)
    }

    @Test
    fun `shows an exhausted pooled quota as fully used instead of unlimited`() = runTest {
        val window =
            windowsFor("""{"quota_snapshots":{"chat":{"unlimited":true,"has_quota":false}}}""")
                .single()

        assertFalse(window.isUnlimited)
        assertEquals(100.0, window.usedPercent)
    }

    @Test
    fun `accepts a date-only or a full timestamp as the fallback reset date`() = runTest {
        fun body(date: String) =
            """{"quota_reset_date":"$date","quota_snapshots":{"chat":{"unlimited":true}}}"""

        assertEquals(
            Instant.parse("2026-05-01T00:00:00Z"),
            windowsFor(body("2026-05-01")).single().resetsAt,
        )
        assertEquals(
            Instant.parse("2026-05-01T12:34:56Z"),
            windowsFor(body("2026-05-01T12:34:56Z")).single().resetsAt,
        )
    }

    @Test
    fun `maps a malformed reset date to Parse`() = runTest {
        server.enqueueJson(
            """{"quota_reset_date_utc":"not-a-date","quota_snapshots":{"chat":{"unlimited":true}}}"""
        )

        val failure = assertIs<QuotaResult.Failure>(fetcher.fetch(credentials()))

        assertEquals(QuotaErrorKind.Parse, failure.kind)
    }

    @Test
    fun `maps HTTP errors to error kinds`() = runTest {
        for ((status, kind) in
            listOf(
                401 to QuotaErrorKind.Auth,
                403 to QuotaErrorKind.Access,
                429 to QuotaErrorKind.RateLimited,
            )) {
            server.enqueueStatus(status)

            val failure = assertIs<QuotaResult.Failure>(fetcher.fetch(credentials()))

            assertEquals(kind, failure.kind, "HTTP $status")
        }
    }

    @Test
    fun `maps a transport failure to Network`() = runTest {
        val credentials = credentials()
        server.close()

        val failure = assertIs<QuotaResult.Failure>(fetcher.fetch(credentials))

        assertEquals(QuotaErrorKind.Network, failure.kind)
    }
}
