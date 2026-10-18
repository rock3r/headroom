package dev.sebastiano.headroom.quota

import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.QuotaErrorKind
import dev.sebastiano.headroom.model.QuotaResult
import dev.sebastiano.headroom.model.QuotaSnapshot
import dev.sebastiano.headroom.model.WindowKind
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class MuseQuotaFetcherTest {
    private val server = MockWebServer()
    private val fetcher = MuseQuotaFetcher(KtorQuotaHttpClient(), FIXED_CLOCK)

    @BeforeEach
    fun setUp() {
        server.start()
    }

    @AfterEach
    fun tearDown() {
        server.close()
    }

    private fun credentials() =
        ProviderCredentials(accessToken = "dca:test-token", baseUrl = server.baseUrl())

    private suspend fun snapshotFor(body: String): QuotaSnapshot {
        server.enqueueJson(body)
        return assertIs<QuotaResult.Success>(fetcher.fetch(credentials())).snapshot
    }

    @Test
    fun `reads the subscription windows with the DCA token`() = runTest {
        server.enqueueJson(fixture("muse/key.json"))

        val snapshot = assertIs<QuotaResult.Success>(fetcher.fetch(credentials())).snapshot

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/muse-code/key", request.target)
        assertEquals("Bearer " + credentials().accessToken, request.headers["Authorization"])
        assertEquals("1.0.0", request.headers["x-api-version"])
        assertEquals("""{"onboard":false}""", request.body?.utf8()?.trim())
        assertEquals(Provider.Muse, snapshot.provider)
        assertEquals("Muse Code Power Usage", snapshot.planLabel)
        assertEquals(listOf("five_hour", "weekly"), snapshot.windows.map { it.id })
        val session = snapshot.windows[0]
        assertEquals("Session", session.label)
        assertEquals(WindowKind.Session, session.kind)
        assertEquals(34.0, session.usedPercent)
        assertEquals(5.hours, session.length)
        assertEquals(Instant.fromEpochSeconds(1_789_078_632), session.resetsAt)
        val weekly = snapshot.windows[1]
        assertEquals("Weekly", weekly.label)
        assertEquals(WindowKind.Weekly, weekly.kind)
        assertEquals(12.5, weekly.usedPercent)
        assertEquals(7.days, weekly.length)
        assertEquals(Instant.fromEpochSeconds(1_789_344_000), weekly.resetsAt)
    }

    @Test
    fun `reads string figures and keeps a changed rolling duration`() = runTest {
        val snapshot =
            snapshotFor(
                """{"is_subs_active":true,"subs_usage":{"window":{"used_percent":"40",""" +
                    """"window_duration_mins":90,"resets_at":"1789078632"},"weekly":{"used_percent":"3"}}}"""
            )

        val session = snapshot.windows[0]
        assertEquals(40.0, session.usedPercent)
        assertEquals(90.minutes, session.length)
        assertEquals(WindowKind.Session, session.kind)
        assertEquals(Instant.fromEpochSeconds(1_789_078_632), session.resetsAt)
        assertEquals(3.0, snapshot.windows[1].usedPercent)
        assertNull(snapshot.windows[1].resetsAt)
        assertNull(snapshot.planLabel)
    }

    @Test
    fun `an idle active subscription keeps its plan without inventing windows`() = runTest {
        val snapshot =
            snapshotFor(
                """{"subs_tier_name":"Muse Code Everyday Usage","is_subs_active":true,"subs_usage":null}"""
            )

        assertEquals("Muse Code Everyday Usage", snapshot.planLabel)
        assertEquals(emptyList(), snapshot.windows.map { it.id })
    }

    @Test
    fun `an account without an active subscription has no quota to show`() = runTest {
        server.enqueueJson("""{"is_subs_active":false,"subs_usage":null}""")

        val failure = assertIs<QuotaResult.Failure>(fetcher.fetch(credentials()))

        assertEquals(QuotaErrorKind.Access, failure.kind)
        assertTrue("subscription" in failure.message)
    }

    @Test
    fun `a window without a figure is left out, never shown as zero`() = runTest {
        val snapshot =
            snapshotFor(
                """{"is_subs_active":true,"subs_usage":{"window":{},""" +
                    """"weekly":{"used_percent":20,"resets_at":1789344000}}}"""
            )

        assertEquals(listOf("weekly"), snapshot.windows.map { it.id })
        assertEquals(20.0, snapshot.windows.single().usedPercent)
    }

    @Test
    fun `maps HTTP errors to error kinds`() = runTest {
        server.enqueueStatus(401)
        assertEquals(
            QuotaErrorKind.Auth,
            assertIs<QuotaResult.Failure>(fetcher.fetch(credentials())).kind,
        )
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
