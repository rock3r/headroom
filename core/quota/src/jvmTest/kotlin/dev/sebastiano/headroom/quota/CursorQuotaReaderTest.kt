package dev.sebastiano.headroom.quota

import dev.sebastiano.headroom.model.QuotaErrorKind
import dev.sebastiano.headroom.model.WindowKind
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CursorQuotaReaderTest {
    private val server = MockWebServer()
    private val reader = CursorQuotaReader(KtorQuotaHttpClient())

    @BeforeEach
    fun setUp() {
        server.start()
    }

    @AfterEach
    fun tearDown() {
        server.close()
    }

    private fun respond(period: MockResponse, sand: MockResponse? = null) {
        server.respondByTarget(
            buildMap {
                put(PERIOD_TARGET, period)
                sand?.let { put(SAND_TARGET, it) }
            }
        )
    }

    private suspend fun readKnown(): CursorRead.Known =
        assertIs<CursorRead.Known>(reader.read(TOKEN, server.baseUrl()))

    @Test
    fun `maps the first-party and third-party model pools and the Grok Bot week`() = runTest {
        respond(
            period = jsonResponse(fixture("cursor/current_period_usage.json")),
            sand = jsonResponse(fixture("cursor/sand_usage_status.json")),
        )

        val windows = readKnown().windows

        assertEquals(
            listOf("cursor-models", "cursor-other-models", "grok-bot"),
            windows.map { it.id },
        )
        // They belong to the Cursor account, not to the account they are shown beside.
        assertTrue(windows.all { it.isSecondary })
        val models = windows[0]
        assertEquals("Cursor Models", models.label)
        assertEquals("Cursor", models.group)
        assertEquals(0.7796666666666667, models.usedPercent)
        assertEquals(WindowKind.Monthly, models.kind)
        assertEquals(31.days, models.length)
        assertEquals(Instant.fromEpochMilliseconds(1794858427000), models.resetsAt)
        val other = windows[1]
        assertEquals("Other Models", other.label)
        assertEquals(25.5, other.usedPercent)
        assertEquals(models.resetsAt, other.resetsAt)
        val bot = windows[2]
        assertEquals("Grok Bot", bot.label)
        assertEquals(47.910477, bot.usedPercent)
        assertEquals(WindowKind.Weekly, bot.kind)
        assertEquals(7.days, bot.length)
        assertEquals(Instant.parse("2026-10-18T19:49:11.555Z"), bot.resetsAt)
    }

    @Test
    fun `sends Connect JSON posts with the Cursor token`() = runTest {
        respond(
            period = jsonResponse(fixture("cursor/current_period_usage.json")),
            sand = jsonResponse(fixture("cursor/sand_usage_status.json")),
        )

        reader.read(TOKEN, server.baseUrl())

        val requests = List(server.requestCount) { server.takeRequest() }
        assertEquals(listOf(PERIOD_TARGET, SAND_TARGET), requests.map { it.target })
        for (request in requests) {
            assertEquals("POST", request.method)
            assertEquals("Bearer $TOKEN", request.headers["Authorization"])
            assertEquals("1", request.headers["Connect-Protocol-Version"])
            assertEquals("{}", request.body?.utf8())
        }
    }

    @Test
    fun `keeps the model pools when the Grok Bot read fails`() = runTest {
        respond(
            period = jsonResponse(fixture("cursor/current_period_usage.json")),
            sand = MockResponse(code = 500),
        )

        val windows = readKnown().windows

        assertEquals(listOf("cursor-models", "cursor-other-models"), windows.map { it.id })
    }

    @Test
    fun `omits the Grok Bot window when its included limit is zero`() = runTest {
        respond(
            period = jsonResponse(fixture("cursor/current_period_usage.json")),
            sand =
                jsonResponse(
                    """{"currentPeriodStart":"2026-10-11T19:49:11.555Z",""" +
                        """"nextResetTimestampUtc":"2026-10-18T19:49:11.555Z",""" +
                        """"includedLimitZero":true}"""
                ),
        )

        val windows = readKnown().windows

        assertEquals(listOf("cursor-models", "cursor-other-models"), windows.map { it.id })
    }

    @Test
    fun `maps an unauthorized period read to an auth failure`() = runTest {
        respond(period = MockResponse(code = 401))

        val read = reader.read(TOKEN, server.baseUrl())

        assertEquals(CursorRead.Failed(QuotaErrorKind.Auth), read)
    }

    @Test
    fun `maps a malformed period payload to a parse failure`() = runTest {
        respond(period = jsonResponse("""{"enabled":true}"""))

        val read = reader.read(TOKEN, server.baseUrl())

        assertEquals(CursorRead.Failed(QuotaErrorKind.Parse), read)
    }

    @Test
    fun `reads no windows when the plan usage is disabled`() = runTest {
        respond(period = jsonResponse("""{"enabled":false,"planUsage":{}}"""))

        val windows = readKnown().windows

        assertEquals(emptyList(), windows)
    }

    private companion object {
        const val TOKEN = "cursor-test-token"
        const val PERIOD_TARGET = "/aiserver.v1.DashboardService/GetCurrentPeriodUsage"
        const val SAND_TARGET = "/aiserver.v1.DashboardService/GetSandUsageStatus"
    }
}
