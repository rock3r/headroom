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

class ZAiQuotaFetcherTest {
    private val server = MockWebServer()
    private val fetcher = ZAiQuotaFetcher(OkHttpQuotaHttpClient(), FIXED_CLOCK)

    @BeforeEach
    fun setUp() {
        server.start()
    }

    @AfterEach
    fun tearDown() {
        server.close()
    }

    private fun credentials() =
        ProviderCredentials(accessToken = "test-api-key", baseUrl = server.baseUrl())

    private fun respond(
        quota: MockResponse = jsonResponse(fixture("zai/quota_limit.json")),
        subscription: MockResponse = jsonResponse(fixture("zai/subscription_list.json")),
    ) {
        server.respondByTarget(mapOf(QUOTA_TARGET to quota, SUBSCRIPTION_TARGET to subscription))
    }

    private fun quotaWith(limits: String, level: String = "pro"): MockResponse =
        jsonResponse("""{"code":200,"data":{"limits":[$limits],"level":"$level"},"success":true}""")

    private suspend fun fetchSnapshot(): QuotaSnapshot =
        assertIs<QuotaResult.Success>(fetcher.fetch(credentials())).snapshot

    @Test
    fun `sends the API key as a bearer token to both endpoints`() = runTest {
        respond()

        fetcher.fetch(credentials())

        val requests = List(server.requestCount) { server.takeRequest() }
        assertEquals(listOf(QUOTA_TARGET, SUBSCRIPTION_TARGET), requests.map { it.target })
        for (request in requests) {
            assertEquals("GET", request.method)
            assertEquals("Bearer test-api-key", request.headers["Authorization"])
            assertEquals("application/json", request.headers["Accept"])
        }
    }

    @Test
    fun `maps the five hour and weekly rows and skips unknown units`() = runTest {
        respond()

        val snapshot = fetchSnapshot()

        assertEquals(Provider.ZAi, snapshot.provider)
        assertEquals(listOf("five_hour", "weekly"), snapshot.windows.map { it.id })
        assertEquals(listOf("5h limit", "Weekly"), snapshot.windows.map { it.label })
        val fiveHour = snapshot.windows[0]
        assertEquals(25.0, fiveHour.usedPercent)
        assertEquals(WindowKind.Session, fiveHour.kind)
        assertEquals(Duration.ofHours(5), fiveHour.length)
        assertEquals(Instant.ofEpochMilli(1_775_235_600_000), fiveHour.resetsAt)
        val weekly = snapshot.windows[1]
        assertEquals(7.0, weekly.usedPercent)
        assertEquals(WindowKind.Weekly, weekly.kind)
        assertEquals(Duration.ofDays(7), weekly.length)
        assertEquals(Instant.ofEpochMilli(1_775_728_800_000), weekly.resetsAt)
    }

    @Test
    fun `takes the plan from the first valid subscription`() = runTest {
        respond()

        assertEquals("Pro", fetchSnapshot().planLabel)
    }

    @Test
    fun `falls back to the quota level when no subscription is valid`() = runTest {
        respond(
            subscription =
                jsonResponse("""{"data":[{"productName":"GLM Coding Max","status":"EXPIRED"}]}""")
        )

        assertEquals("Lite", fetchSnapshot().planLabel)
    }

    @Test
    fun `still returns the windows when the subscription call fails`() = runTest {
        respond(subscription = MockResponse(code = 500))

        val snapshot = fetchSnapshot()

        assertEquals("Lite", snapshot.planLabel)
        assertEquals(2, snapshot.windows.size)
    }

    @Test
    fun `uses the percentage when the amounts are missing, on a 0 to 100 scale`() = runTest {
        respond(
            quota = quotaWith("""{"type":"TOKENS_LIMIT","unit":3,"number":5,"percentage":0.5}""")
        )

        val window = fetchSnapshot().windows.single()

        assertEquals(0.5, window.usedPercent)
        assertNull(window.resetsAt)
    }

    @Test
    fun `ignores reset times that are not epoch milliseconds`() = runTest {
        respond(
            quota =
                quotaWith(
                    """{"unit":6,"number":1,"usage":100,"currentValue":50,"nextResetTime":1775728800}"""
                )
        )

        val window = fetchSnapshot().windows.single()

        assertEquals(50.0, window.usedPercent)
        assertNull(window.resetsAt)
    }

    @Test
    fun `maps a payload without known windows to Parse`() = runTest {
        for (quota in
            listOf(
                quotaWith(""),
                quotaWith("""{"unit":99,"number":1,"percentage":10}"""),
                jsonResponse("""{"code":200,"data":{"limits":null},"success":true}"""),
                jsonResponse("""{"code":200,"data":{"limits":"unexpected"},"success":true}"""),
                jsonResponse("not-json"),
            )) {
            respond(quota = quota)

            val failure = assertIs<QuotaResult.Failure>(fetcher.fetch(credentials()))

            assertEquals(QuotaErrorKind.Parse, failure.kind)
        }
    }

    @Test
    fun `maps HTTP errors to error kinds`() = runTest {
        respond(quota = MockResponse(code = 401))
        assertEquals(
            QuotaErrorKind.Auth,
            assertIs<QuotaResult.Failure>(fetcher.fetch(credentials())).kind,
        )
        respond(quota = MockResponse(code = 403))
        assertEquals(
            QuotaErrorKind.Access,
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
    fun `strips the product prefix from plan names`() {
        assertEquals("Pro", zaiPlanLabel("GLM Coding Pro"))
        assertEquals("Max", zaiPlanLabel("glm coding MAX"))
        assertEquals("Lite", zaiPlanLabel("lite"))
    }

    private companion object {
        const val QUOTA_TARGET = "/monitor/usage/quota/limit"
        const val SUBSCRIPTION_TARGET = "/biz/subscription/list"
    }
}
