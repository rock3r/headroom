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
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class GrokQuotaFetcherTest {
    private val server = MockWebServer()
    private val fetcher = GrokQuotaFetcher(OkHttpQuotaHttpClient(), FIXED_CLOCK)

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

    private fun respond(
        weekly: MockResponse,
        monthly: MockResponse? = null,
        user: MockResponse? = null,
    ) {
        server.respondByTarget(
            buildMap {
                put(WEEKLY_TARGET, weekly)
                monthly?.let { put(MONTHLY_TARGET, it) }
                user?.let { put(USER_TARGET, it) }
            }
        )
    }

    private fun requestedTargets(): List<String> =
        List(server.requestCount) { server.takeRequest().target }

    private suspend fun fetchSnapshot(): QuotaSnapshot =
        assertIs<QuotaResult.Success>(fetcher.fetch(credentials())).snapshot

    @Test
    fun `sends the weekly credits request with the CLI token header`() = runTest {
        respond(weekly = jsonResponse(fixture("grok/billing_weekly.json")))

        fetcher.fetch(credentials())

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals(WEEKLY_TARGET, request.target)
        assertEquals("Bearer test-access-token", request.headers["Authorization"])
        assertEquals("application/json", request.headers["Accept"])
        assertEquals("xai-grok-cli", request.headers["X-XAI-Token-Auth"])
    }

    @Test
    fun `maps the weekly credit pool and its period`() = runTest {
        respond(weekly = jsonResponse(fixture("grok/billing_weekly.json")))

        val snapshot = fetchSnapshot()

        assertEquals(Provider.Grok, snapshot.provider)
        assertEquals("SuperGrok", snapshot.planLabel)
        val weekly = snapshot.windows.single()
        assertEquals("weekly", weekly.id)
        assertEquals("Weekly", weekly.label)
        assertEquals(37.5, weekly.usedPercent)
        assertEquals(WindowKind.Weekly, weekly.kind)
        assertEquals(Duration.ofDays(7), weekly.length)
        assertEquals(Instant.parse("2026-04-07T09:00:00Z"), weekly.resetsAt)
        // The plan was in the billing payload, so the user profile is not requested.
        assertEquals(listOf(WEEKLY_TARGET), requestedTargets())
    }

    @Test
    fun `loads the plan from the user profile when billing omits it`() = runTest {
        respond(
            weekly = jsonResponse(fixture("grok/billing_weekly_no_tier.json")),
            user = jsonResponse(fixture("grok/user.json")),
        )

        val snapshot = fetchSnapshot()

        assertEquals("SuperGrok", snapshot.planLabel)
        assertEquals(88.0, snapshot.windows.single().usedPercent)
        assertEquals(ONE_WEEK, snapshot.windows.single().length)
        assertEquals(listOf(WEEKLY_TARGET, USER_TARGET), requestedTargets())
    }

    @Test
    fun `bounds the user profile request to five seconds`() = runTest {
        val client = FakeQuotaHttpClient { request ->
            val body =
                if (request.url.endsWith("/v1/user")) "{}"
                else fixture("grok/billing_weekly_no_tier.json")
            QuotaHttpResponse(statusCode = 200, body = body)
        }

        GrokQuotaFetcher(client, FIXED_CLOCK).fetch(ProviderCredentials("test-access-token"))

        assertEquals(
            "https://cli-chat-proxy.grok.com/v1/billing?format=credits",
            client.requests.first().url,
        )
        assertEquals(Duration.ofSeconds(5), client.requests.last().timeout)
    }

    @Test
    fun `treats an omitted percentage as zero while the weekly period is present`() = runTest {
        respond(weekly = jsonResponse(fixture("grok/billing_weekly_after_reset.json")))

        val snapshot = fetchSnapshot()

        val weekly = snapshot.windows.single()
        assertEquals(0.0, weekly.usedPercent)
        assertEquals(Instant.parse("2026-04-09T17:44:04.345489Z"), weekly.resetsAt)
        assertEquals("SuperGrok", snapshot.planLabel)
    }

    @Test
    fun `falls back to the monthly budget when there is no weekly period`() = runTest {
        respond(
            weekly = jsonResponse(fixture("grok/billing_unified.json")),
            monthly = jsonResponse(fixture("grok/billing_monthly.json")),
        )

        val snapshot = fetchSnapshot()

        assertEquals("SuperGrok Heavy", snapshot.planLabel)
        val monthly = snapshot.windows.single()
        assertEquals("monthly", monthly.id)
        assertEquals("Monthly", monthly.label)
        assertEquals(30.0, monthly.usedPercent)
        assertEquals(WindowKind.Monthly, monthly.kind)
        assertEquals(Duration.ofDays(30), monthly.length)
        assertEquals(Instant.parse("2026-05-01T00:00:00Z"), monthly.resetsAt)
        assertEquals(listOf(WEEKLY_TARGET, MONTHLY_TARGET), requestedTargets())
    }

    @Test
    fun `does not treat a malformed weekly percentage as zero`() = runTest {
        respond(
            weekly =
                jsonResponse(
                    """
                    {
                      "config": {
                        "creditUsagePercent": "unknown",
                        "currentPeriod": { "end": "2026-04-07T09:00:00Z" }
                      }
                    }
                    """
                ),
            monthly = jsonResponse("""{"config":{}}"""),
        )

        val failure = assertIs<QuotaResult.Failure>(fetcher.fetch(credentials()))

        assertEquals(QuotaErrorKind.Parse, failure.kind)
        assertEquals(listOf(WEEKLY_TARGET, MONTHLY_TARGET), requestedTargets())
    }

    @Test
    fun `keeps the weekly window when the user profile is malformed`() = runTest {
        respond(
            weekly = jsonResponse(fixture("grok/billing_weekly_no_tier.json")),
            user = jsonResponse("not-json"),
        )

        val snapshot = fetchSnapshot()

        assertNull(snapshot.planLabel)
        assertEquals(88.0, snapshot.windows.single().usedPercent)
    }

    @Test
    fun `accepts a weekly payload without the config wrapper`() = runTest {
        respond(
            weekly =
                jsonResponse(
                    """
                    {
                      "creditUsagePercent": 12,
                      "currentPeriod": { "end": "2026-04-07T09:00:00Z" }
                    }
                    """
                )
        )

        assertEquals(12.0, fetchSnapshot().windows.single().usedPercent)
    }

    @Test
    fun `maps HTTP errors on the weekly and monthly requests`() = runTest {
        respond(weekly = MockResponse(code = 401))
        assertEquals(
            QuotaErrorKind.Auth,
            assertIs<QuotaResult.Failure>(fetcher.fetch(credentials())).kind,
        )

        respond(
            weekly = jsonResponse(fixture("grok/billing_unified.json")),
            monthly = MockResponse(code = 429),
        )
        assertEquals(
            QuotaErrorKind.RateLimited,
            assertIs<QuotaResult.Failure>(fetcher.fetch(credentials())).kind,
        )
    }

    @Test
    fun `maps a transport failure to Network`() = runTest {
        val credentials = credentials()
        server.close()

        val failure = assertIs<QuotaResult.Failure>(fetcher.fetch(credentials))

        assertEquals(QuotaErrorKind.Network, failure.kind)
    }

    @Test
    fun `maps subscription tiers to plan names`() {
        assertEquals("SuperGrok", grokPlanLabel("super_grok"))
        assertEquals("SuperGrok", grokPlanLabel("SUBSCRIPTION_TIER_SUPERGROK"))
        assertEquals("SuperGrok Heavy", grokPlanLabel("SUBSCRIPTION_TIER_SUPER_GROK_HEAVY"))
        assertEquals("X Premium+", grokPlanLabel("x-premium-plus"))
        assertEquals("X Premium", grokPlanLabel("XPREMIUM"))
        assertEquals("Some New Tier", grokPlanLabel("some_new_tier"))
        assertNull(grokPlanLabel("  "))
    }

    private companion object {
        const val WEEKLY_TARGET = "/v1/billing?format=credits"
        const val MONTHLY_TARGET = "/v1/billing"
        const val USER_TARGET = "/v1/user"
    }
}
