package dev.sebastiano.headroom.quota

import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.QuotaErrorKind
import dev.sebastiano.headroom.model.QuotaResult
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class JetBrainsQuotaFetcherTest {
    private val server = MockWebServer()
    private val fetcher = JetBrainsQuotaFetcher(OkHttpQuotaHttpClient(), FIXED_CLOCK)

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
    fun `sends the balance request with the OAuth token`() = runTest {
        server.enqueueJson(fixture("jetbrains/auth_test.json"))

        fetcher.fetch(credentials())

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/auth/test", request.target)
        assertEquals("Bearer test-access-token", request.headers["Authorization"])
        assertEquals("application/json", request.headers["Accept"])
    }

    @Test
    fun `reports the plan and the balance, but no windows, because a balance is not a used share`() =
        runTest {
            server.enqueueJson(fixture("jetbrains/auth_test.json"))

            val snapshot = assertIs<QuotaResult.Success>(fetcher.fetch(credentials())).snapshot

            assertEquals(Provider.JetBrains, snapshot.provider)
            assertEquals("JetBrains AI Pro", snapshot.planLabel)
            assertEquals(emptyList(), snapshot.windows)
            assertEquals(
                dev.sebastiano.headroom.model.QuotaBalance(20.0, "AI Credits"),
                snapshot.balance,
            )
            assertEquals(FIXED_NOW, snapshot.fetchedAt)
        }

    @Test
    fun `maps license types to plan names`() {
        assertEquals("JetBrains AI Pro", jetBrainsPlanLabel("AIP"))
        assertEquals("JetBrains AI Ultimate", jetBrainsPlanLabel("AIU"))
        assertEquals("Junie", jetBrainsPlanLabel("JUNP"))
        assertEquals("AIF", jetBrainsPlanLabel("AIF"))
    }

    @Test
    fun `leaves the plan empty when the license type is missing`() = runTest {
        server.enqueueJson("""{"balanceLeft":20.0,"balanceUnit":"AI Credits"}""")

        val snapshot = assertIs<QuotaResult.Success>(fetcher.fetch(credentials())).snapshot

        assertNull(snapshot.planLabel)
    }

    @Test
    fun `maps a payload without a balance to Parse`() = runTest {
        for (body in listOf("""{"balanceUnit":"AI Credits"}""", """{"balanceLeft":20.0}""", "[]")) {
            server.enqueueJson(body)

            val failure = assertIs<QuotaResult.Failure>(fetcher.fetch(credentials()))

            assertEquals(QuotaErrorKind.Parse, failure.kind, body)
        }
    }

    @Test
    fun `maps HTTP errors to error kinds`() = runTest {
        server.enqueueStatus(401)
        server.enqueueStatus(403)

        assertEquals(
            QuotaErrorKind.Auth,
            assertIs<QuotaResult.Failure>(fetcher.fetch(credentials())).kind,
        )
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
}
