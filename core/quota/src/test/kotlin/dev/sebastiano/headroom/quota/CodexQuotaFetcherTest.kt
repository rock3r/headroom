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

class CodexQuotaFetcherTest {
    private val server = MockWebServer()
    private val fetcher = CodexQuotaFetcher(OkHttpQuotaHttpClient(), FIXED_CLOCK)

    @BeforeEach
    fun setUp() {
        server.start()
    }

    @AfterEach
    fun tearDown() {
        server.close()
    }

    private fun credentials(accountId: String? = "account-test-0001") =
        ProviderCredentials(
            accessToken = "test-access-token",
            accountId = accountId,
            baseUrl = server.baseUrl(),
        )

    @Test
    fun `sends the usage request with the ChatGPT account header`() = runTest {
        server.enqueueJson(fixture("codex/usage.json"))

        fetcher.fetch(credentials())

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/wham/usage", request.target)
        assertEquals("Bearer test-access-token", request.headers["Authorization"])
        assertEquals("application/json", request.headers["Accept"])
        assertEquals("account-test-0001", request.headers["ChatGPT-Account-Id"])
    }

    @Test
    fun `omits the account header when there is no account id`() = runTest {
        server.enqueueJson(fixture("codex/usage.json"))

        fetcher.fetch(credentials(accountId = null))

        assertNull(server.takeRequest().headers["ChatGPT-Account-Id"])
    }

    @Test
    fun `maps general and additional windows from their real lengths`() = runTest {
        server.enqueueJson(fixture("codex/usage.json"))

        val snapshot = assertIs<QuotaResult.Success>(fetcher.fetch(credentials())).snapshot

        assertEquals(Provider.Codex, snapshot.provider)
        assertEquals("account-test-0001", snapshot.accountId)
        assertEquals("Plus", snapshot.planLabel)
        assertEquals(FIXED_NOW, snapshot.fetchedAt)
        assertEquals(
            listOf(
                "primary_window",
                "secondary_window",
                "codex_mini:primary_window",
                "codex_mini:secondary_window",
            ),
            snapshot.windows.map { it.id },
        )
        assertEquals(
            listOf("5 hour", "Weekly", "5 hour", "Weekly"),
            snapshot.windows.map { it.label },
        )
        assertEquals(
            listOf(
                "General usage limits",
                "General usage limits",
                "GPT-Codex-Mini usage limits",
                "GPT-Codex-Mini usage limits",
            ),
            snapshot.windows.map { it.group },
        )
        assertEquals(
            listOf(WindowKind.Session, WindowKind.Weekly, WindowKind.Session, WindowKind.Weekly),
            snapshot.windows.map { it.kind },
        )
        val session = snapshot.windows[0]
        assertEquals(12.0, session.usedPercent)
        assertEquals(Duration.ofHours(5), session.length)
        assertEquals(Instant.ofEpochSecond(1_775_229_900), session.resetsAt)
        val weekly = snapshot.windows[1]
        assertEquals(34.5, weekly.usedPercent)
        assertEquals(Duration.ofDays(7), weekly.length)
        assertEquals(Instant.ofEpochSecond(1_775_578_080), weekly.resetsAt)
    }

    @Test
    fun `keeps the legacy labels and kinds when the window length is missing`() = runTest {
        server.enqueueJson(fixture("codex/usage_legacy.json"))

        val snapshot = assertIs<QuotaResult.Success>(fetcher.fetch(credentials())).snapshot

        assertEquals(listOf("Session", "Weekly"), snapshot.windows.map { it.label })
        assertEquals(
            listOf(WindowKind.Session, WindowKind.Weekly),
            snapshot.windows.map { it.kind },
        )
        assertEquals(listOf(null, null), snapshot.windows.map { it.length })
        assertEquals("Pro", snapshot.planLabel)
    }

    @Test
    fun `skips malformed parts and keeps the valid windows`() = runTest {
        server.enqueueJson(fixture("codex/usage_malformed_parts.json"))

        val snapshot = assertIs<QuotaResult.Success>(fetcher.fetch(credentials())).snapshot

        assertEquals(
            listOf("primary_window", "additional_0:primary_window"),
            snapshot.windows.map { it.id },
        )
        assertEquals(listOf("Monthly", "Session"), snapshot.windows.map { it.label })
        assertEquals(
            listOf("General usage limits", "Additional Codex usage limits"),
            snapshot.windows.map { it.group },
        )
        assertEquals(WindowKind.Monthly, snapshot.windows[0].kind)
        assertEquals(Duration.ofDays(30), snapshot.windows[0].length)
        assertNull(snapshot.windows[1].resetsAt)
    }

    @Test
    fun `maps HTTP errors to error kinds`() = runTest {
        for ((status, kind) in
            listOf(
                401 to QuotaErrorKind.Auth,
                403 to QuotaErrorKind.Access,
                429 to QuotaErrorKind.RateLimited,
                502 to QuotaErrorKind.Unknown,
            )) {
            server.enqueueStatus(status)

            val failure = assertIs<QuotaResult.Failure>(fetcher.fetch(credentials()))

            assertEquals(kind, failure.kind, "HTTP $status")
        }
    }

    @Test
    fun `maps a malformed payload to Parse`() = runTest {
        server.enqueueJson("[1, 2, 3]")

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
