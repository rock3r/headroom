package dev.sebastiano.headroom.quota

import dev.sebastiano.headroom.model.AskOutcome
import dev.sebastiano.headroom.model.QuotaErrorKind
import dev.sebastiano.headroom.model.RedeemOutcome
import dev.sebastiano.headroom.model.ResetAvailability
import dev.sebastiano.headroom.model.ResetScope
import dev.sebastiano.headroom.model.WindowKind
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ZAiResetsTest {
    private val server = MockWebServer()
    private val log = RecordingResetLog()
    private var now = FIXED_NOW
    private val clock =
        object : Clock() {
            override fun instant(): Instant = now

            override fun getZone() = ZoneOffset.UTC

            override fun withZone(zone: java.time.ZoneId?) = this
        }
    private lateinit var resets: ZAiResets

    @BeforeEach
    fun setUp() {
        server.start()
        resets = ZAiResets(OkHttpQuotaHttpClient(), clock, log, zCodeHost = server.baseUrl())
    }

    @AfterEach
    fun tearDown() {
        server.close()
    }

    private fun credentials(zCode: ZCodeSignIn? = ZCodeSignIn.Ready("zcode-jwt", "business")) =
        ProviderCredentials(accessToken = "zai-api-key", zCode = zCode)

    private fun requests(): List<RecordedRequest> =
        List(server.requestCount) { server.takeRequest() }

    private suspend fun redeem(pool: String = "five_hour", key: String = "key-1") =
        resets.redeem(credentials(), pool, key)

    private fun used(value: String = "true") = """{"code":0,"msg":"ok","data":{"used":$value}}"""

    private val ok = """{"code":0,"msg":"ok","data":{}}"""

    // Reading.

    @Test
    fun `reads the status from zcode with both ZCode tokens`() = runTest {
        server.enqueueJson(fixture("zai/reset_status.json"))

        resets.read(credentials())

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/api/v1/coding-plan/reset/status", request.target)
        assertEquals("Bearer zcode-jwt", request.headers["Authorization"])
        assertEquals("business", request.headers["X-Bigmodel-Authorization"])
        assertEquals("PERSONAL", request.headers["Bigmodel-Target-Type"])
        assertEquals("application/json", request.headers["Accept"])
    }

    @Test
    fun `a 5-hour and a weekly pool, without expired resets, soonest expiry first`() = runTest {
        server.enqueueJson(fixture("zai/reset_status.json"))

        val availability = assertIs<ResetRead.Known>(resets.read(credentials())).availability!!

        val (fiveHour, week) = availability.pools
        assertEquals("five_hour", fiveHour.id)
        assertEquals("5-hour limit", fiveHour.label)
        assertEquals(2, fiveHour.available)
        assertEquals(ResetScope.of(WindowKind.Session), fiveHour.scope)
        assertEquals(
            listOf(Instant.parse("2026-04-05T12:00:00Z"), Instant.parse("2026-04-08T12:00:00Z")),
            fiveHour.expiries,
        )
        assertEquals("week", week.id)
        assertEquals("Weekly limit", week.label)
        assertEquals(1, week.available)
        assertEquals(ResetScope.of(WindowKind.Weekly), week.scope)
        // Seconds are read as seconds.
        assertEquals(listOf(Instant.parse("2026-04-12T12:00:00Z")), week.expiries)
        assertTrue(availability.canAskForMore)
        assertFalse(availability.requiresSignIn)
    }

    @Test
    fun `empty pools are still shown, and a reset card can be asked for`() = runTest {
        server.enqueueJson(fixture("zai/reset_status_empty.json"))

        val availability = assertIs<ResetRead.Known>(resets.read(credentials())).availability!!

        assertEquals(listOf(0, 0), availability.pools.map { it.available })
        assertTrue(availability.canAskForMore)
    }

    @Test
    fun `without a ZCode sign-in the resets ask for one, with no call`() = runTest {
        listOf(ZCodeSignIn.Missing, null).forEach { zCode ->
            val read = assertIs<ResetRead.Known>(resets.read(credentials(zCode)))
            assertEquals(ResetAvailability(emptyList(), requiresSignIn = true), read.availability)
        }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `a ZCode sign-in that cannot be used now is a failed read, with no call`() = runTest {
        assertEquals(ResetRead.Failed, resets.read(credentials(ZCodeSignIn.Unavailable)))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `a refused ZCode sign-in asks for a new one`() = runTest {
        listOf(401, 403).forEach { status ->
            server.enqueueStatus(status)
            val read = assertIs<ResetRead.Known>(resets.read(credentials()))
            assertTrue(read.availability!!.requiresSignIn)
        }
    }

    @Test
    fun `an error envelope on an HTTP error is read, and 401 still asks to sign in`() = runTest {
        server.enqueueJson("""{"code":2007,"msg":"dependency failed"}""", code = 502)
        assertEquals(ResetRead.Failed, resets.read(credentials()))
        server.enqueueJson("""{"code":1001,"msg":"unauthorized"}""", code = 401)
        val read = assertIs<ResetRead.Known>(resets.read(credentials()))
        assertTrue(read.availability!!.requiresSignIn)
    }

    @Test
    fun `other failures are failed reads`() = runTest {
        server.enqueueStatus(500)
        assertEquals(ResetRead.Failed, resets.read(credentials()))
        server.enqueueJson("""{"code":1001,"msg":"busy"}""")
        assertEquals(ResetRead.Failed, resets.read(credentials()))
        server.enqueueJson("""{"msg":"no code","data":{}}""")
        assertEquals(ResetRead.Failed, resets.read(credentials()))
        server.enqueueJson("not json")
        assertEquals(ResetRead.Failed, resets.read(credentials()))
        assertTrue(log.warns.isNotEmpty())
    }

    // Redeeming.

    @Test
    fun `using a reset sends the attempt key and the limit, then marks the history read`() =
        runTest {
            server.enqueueJson(fixture("zai/reset_status.json"))
            server.enqueueJson(used())
            server.enqueueJson(ok)

            val outcome = redeem()

            assertEquals(RedeemOutcome.Success(resetsLeft = 1), outcome)
            val (status, use, history) = requests()
            assertEquals("/api/v1/coding-plan/reset/status", status.target)
            assertEquals("POST", use.method)
            assertEquals("/api/v1/coding-plan/reset/use", use.target)
            assertEquals("application/json", use.headers["Content-Type"])
            assertEquals("Bearer zcode-jwt", use.headers["Authorization"])
            assertEquals(
                """{"idempotency_key":"key-1","reset_type":"FIVE_HOUR"}""",
                use.body?.utf8(),
            )
            assertEquals("/api/v1/coding-plan/reset/history/read", history.target)
            // The read mark is shared by all of the user's plans: no body and no target scope.
            assertEquals("", history.body?.utf8())
            assertEquals(null, history.headers["Bigmodel-Target-Type"])
            assertEquals("Bearer zcode-jwt", history.headers["Authorization"])
            assertEquals("business", history.headers["X-Bigmodel-Authorization"])
        }

    @Test
    fun `the weekly pool is the WEEK limit`() = runTest {
        server.enqueueJson(fixture("zai/reset_status.json"))
        server.enqueueJson(used())
        server.enqueueJson(ok)

        assertEquals(RedeemOutcome.Success(resetsLeft = 0), redeem(pool = "week"))

        assertTrue(requests()[1].body?.utf8().orEmpty().contains(""""reset_type":"WEEK""""))
    }

    @Test
    fun `an empty pool is no credit, and nothing is used`() = runTest {
        server.enqueueJson(fixture("zai/reset_status_week_only.json"))

        assertEquals(RedeemOutcome.NoCredit, redeem())
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `an unknown pool uses nothing`() = runTest {
        assertEquals(RedeemOutcome.Unsupported, redeem(pool = "monthly"))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `a history read that answers only a code is a success`() = runTest {
        server.enqueueJson(fixture("zai/reset_status.json"))
        server.enqueueJson(used())
        // What the server answered on 2026-10-04: no data at all.
        server.enqueueJson("""{"code":0,"msg":"success","logid":"abc"}""")

        assertEquals(RedeemOutcome.Success(resetsLeft = 1), redeem())
        assertEquals(emptyList(), log.warns)
    }

    @Test
    fun `the history read cannot hold up a reset that worked for long`() = runTest {
        val http = FakeQuotaHttpClient { request ->
            val body =
                when {
                    request.url.endsWith("/status") -> fixture("zai/reset_status.json")
                    request.url.endsWith("/use") -> used()
                    else -> ok
                }
            QuotaHttpResponse(statusCode = 200, body = body)
        }
        val fake = ZAiResets(http, clock, log, zCodeHost = "https://zcode.test")

        assertEquals(
            RedeemOutcome.Success(resetsLeft = 1),
            fake.redeem(credentials(), "five_hour", "key-1"),
        )

        val history = http.requests.single { it.url.endsWith("/history/read") }
        assertEquals(Duration.ofSeconds(5), history.timeout)
    }

    @Test
    fun `a failed history read does not undo the reset`() = runTest {
        server.enqueueJson(fixture("zai/reset_status.json"))
        server.enqueueJson(used())
        server.enqueueStatus(500)

        assertEquals(RedeemOutcome.Success(resetsLeft = 1), redeem())
    }

    @Test
    fun `a lost answer is retried with the same key, even once the pool is empty`() = runTest {
        server.enqueueJson(fixture("zai/reset_status.json"))
        server.enqueueStatus(502)
        assertEquals(RedeemOutcome.Failed(QuotaErrorKind.Unknown), redeem())

        // The first use emptied the pool after all. The retry must reach the server, which
        // recognises the key, instead of answering "no credit" for a reset that worked.
        server.enqueueJson(fixture("zai/reset_status_week_only.json"))
        server.enqueueJson(used())
        server.enqueueJson(ok)
        assertEquals(RedeemOutcome.Success(resetsLeft = 0), redeem())

        val use = requests()[3]
        assertEquals("/api/v1/coding-plan/reset/use", use.target)
        assertTrue(use.body?.utf8().orEmpty().contains(""""idempotency_key":"key-1""""))
    }

    @Test
    fun `a retried key with resets still visible cannot say how many are left`() = runTest {
        server.enqueueJson(fixture("zai/reset_status.json"))
        server.enqueueJson("""{"code":0,"data":{}}""")
        assertEquals(RedeemOutcome.Failed(QuotaErrorKind.Parse), redeem())

        server.enqueueJson(fixture("zai/reset_status.json"))
        server.enqueueJson(used())
        server.enqueueJson(ok)
        assertEquals(RedeemOutcome.Success(resetsLeft = null), redeem())
    }

    @Test
    fun `a definite no forgets the attempt, so a retry on an empty pool is no credit`() = runTest {
        server.enqueueJson(fixture("zai/reset_status.json"))
        server.enqueueJson(used("false"))
        assertEquals(RedeemOutcome.Failed(QuotaErrorKind.Unknown), redeem())

        server.enqueueJson(fixture("zai/reset_status_week_only.json"))
        assertEquals(RedeemOutcome.NoCredit, redeem())
    }

    @Test
    fun `a quoted false is not a definite no`() = runTest {
        server.enqueueJson(fixture("zai/reset_status.json"))
        server.enqueueJson(used("\"false\""))
        assertEquals(RedeemOutcome.Failed(QuotaErrorKind.Parse), redeem())

        server.enqueueJson(fixture("zai/reset_status_week_only.json"))
        server.enqueueJson(used())
        server.enqueueJson(ok)
        assertEquals(RedeemOutcome.Success(resetsLeft = 0), redeem())
    }

    @Test
    fun `a refused sign-in asks for a new one and forgets a first attempt`() = runTest {
        server.enqueueJson(fixture("zai/reset_status.json"))
        server.enqueueStatus(401)
        assertEquals(RedeemOutcome.SignInAgain, redeem())

        server.enqueueJson(fixture("zai/reset_status_week_only.json"))
        assertEquals(RedeemOutcome.NoCredit, redeem())
    }

    @Test
    fun `a refusal on a retry keeps the earlier attempt's doubt`() = runTest {
        server.enqueueJson(fixture("zai/reset_status.json"))
        server.enqueueStatus(503)
        assertEquals(RedeemOutcome.Failed(QuotaErrorKind.Unknown), redeem())
        server.enqueueJson(fixture("zai/reset_status.json"))
        server.enqueueStatus(401)
        assertEquals(RedeemOutcome.SignInAgain, redeem())

        server.enqueueJson(fixture("zai/reset_status_week_only.json"))
        server.enqueueJson(used())
        server.enqueueJson(ok)
        assertEquals(RedeemOutcome.Success(resetsLeft = 0), redeem())
    }

    @Test
    fun `rate limiting keeps the attempt`() = runTest {
        server.enqueueJson(fixture("zai/reset_status.json"))
        server.enqueue(
            jsonResponse("").newBuilder().code(429).addHeader("Retry-After", "30").build()
        )
        assertEquals(RedeemOutcome.RateLimited(FIXED_NOW.plusSeconds(30)), redeem())

        server.enqueueJson(fixture("zai/reset_status_week_only.json"))
        server.enqueueJson(used())
        server.enqueueJson(ok)
        assertEquals(RedeemOutcome.Success(resetsLeft = 0), redeem())
    }

    @Test
    fun `an attempt belongs to its pool`() = runTest {
        server.enqueueJson(fixture("zai/reset_status.json"))
        server.enqueueStatus(502)
        assertEquals(RedeemOutcome.Failed(QuotaErrorKind.Unknown), redeem(pool = "five_hour"))

        server.enqueueJson(fixture("zai/reset_status_empty.json"))
        assertEquals(RedeemOutcome.NoCredit, redeem(pool = "week"))
    }

    @Test
    fun `a status that cannot be read uses nothing`() = runTest {
        server.enqueueStatus(500)
        assertEquals(RedeemOutcome.Failed(QuotaErrorKind.Unknown), redeem())
        server.enqueueStatus(403)
        assertEquals(RedeemOutcome.SignInAgain, redeem())
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `without a usable ZCode sign-in nothing is sent`() = runTest {
        assertEquals(
            RedeemOutcome.SignInAgain,
            resets.redeem(credentials(ZCodeSignIn.Missing), "five_hour", "k"),
        )
        assertEquals(
            RedeemOutcome.Failed(QuotaErrorKind.Network),
            resets.redeem(credentials(ZCodeSignIn.Unavailable), "five_hour", "k"),
        )
        assertEquals(0, server.requestCount)
    }

    // Asking for a reset card.

    @Test
    fun `asking posts to the opportunity endpoint`() = runTest {
        server.enqueueJson("""{"code":0,"msg":"ok","data":{"granted":true}}""")

        assertEquals(AskOutcome.Granted(poolId = null), resets.ask(credentials()))

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/coding-plan/reset/opportunity", request.target)
        assertTrue(askKey(request).isNotBlank())
        assertEquals("business", request.headers["X-Bigmodel-Authorization"])
        assertEquals("PERSONAL", request.headers["Bigmodel-Target-Type"])
    }

    private fun askKey(request: RecordedRequest): String =
        (quotaJson.parseToJsonElement(request.body?.utf8().orEmpty()).jsonObject["idempotency_key"]
                as JsonPrimitive)
            .content

    @Test
    fun `each ask has its own key, and only a passing failure sends it again`() = runTest {
        server.enqueueJson("""{"code":2007,"msg":"dependency failed"}""", code = 500)
        assertEquals(AskOutcome.Failed(QuotaErrorKind.Unknown), resets.ask(credentials()))
        server.enqueueJson("""{"code":0,"data":{"granted":true}}""")
        assertEquals(AskOutcome.Granted(null), resets.ask(credentials()))
        server.enqueueJson("""{"code":0,"data":{"granted":true}}""")
        resets.ask(credentials())

        val (first, retry, next) = requests().map(::askKey)
        assertEquals(first, retry)
        assertTrue(next != retry)
    }

    @Test
    fun `a decline names the next try, and asking again before it stays local`() = runTest {
        val next = FIXED_NOW.plus(Duration.ofHours(3))
        server.enqueueJson(
            """{"code":3301,"msg":"no opportunity","data":{"next_try_at":${next.toEpochMilli()}}}"""
        )

        assertEquals(AskOutcome.NotYet(next), resets.ask(credentials()))
        assertEquals(AskOutcome.NotYet(next), resets.ask(credentials()))
        assertEquals(1, server.requestCount)

        now = next
        server.enqueueJson("""{"code":0,"data":{"granted":true}}""")
        assertEquals(AskOutcome.Granted(null), resets.ask(credentials()))
    }

    @Test
    fun `a decline on an HTTP error is still a decline`() = runTest {
        val next = FIXED_NOW.plus(Duration.ofHours(1))
        server.enqueueJson(
            """{"code":3301,"data":{"granted":false,"next_try_at":${next.toEpochMilli()}}}""",
            code = 400,
        )
        assertEquals(AskOutcome.NotYet(next), resets.ask(credentials()))
    }

    @Test
    fun `a decline holds asks off for at least 5 minutes`() = runTest {
        val soon = FIXED_NOW.plus(Duration.ofMinutes(1))
        server.enqueueJson("""{"code":3301,"data":{"next_try_at":${soon.toEpochMilli()}}}""")
        assertEquals(
            AskOutcome.NotYet(FIXED_NOW.plus(Duration.ofMinutes(5))),
            resets.ask(credentials()),
        )

        server.enqueueJson("""{"code":3301,"data":{}}""")
        now = FIXED_NOW.plus(Duration.ofMinutes(5))
        assertEquals(AskOutcome.NotYet(now.plus(Duration.ofMinutes(10))), resets.ask(credentials()))
    }

    @Test
    fun `a success that grants nothing is a decline`() = runTest {
        server.enqueueJson("""{"code":0,"data":{"granted":false,"next_try_at":1775228400}}""")
        assertEquals(
            AskOutcome.NotYet(Instant.parse("2026-04-03T15:00:00Z")),
            resets.ask(credentials()),
        )
    }

    @Test
    fun `throttling holds asks off for 10 minutes`() = runTest {
        server.enqueueStatus(429)
        assertEquals(AskOutcome.Throttled, resets.ask(credentials()))

        assertEquals(
            AskOutcome.NotYet(FIXED_NOW.plus(Duration.ofMinutes(10))),
            resets.ask(credentials()),
        )
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `a failure holds nothing off`() = runTest {
        server.enqueueStatus(500)
        assertEquals(AskOutcome.Failed(QuotaErrorKind.Unknown), resets.ask(credentials()))
        server.enqueueJson("""{"code":0,"data":{"granted":true}}""")
        assertEquals(AskOutcome.Granted(null), resets.ask(credentials()))
    }

    @Test
    fun `a hold belongs to one sign-in`() = runTest {
        server.enqueueStatus(429)
        assertEquals(AskOutcome.Throttled, resets.ask(credentials()))

        server.enqueueJson("""{"code":0,"data":{"granted":true}}""")
        val other = credentials(ZCodeSignIn.Ready("other-jwt", "other-business"))
        assertEquals(AskOutcome.Granted(null), resets.ask(other))
    }

    @Test
    fun `asking without a usable ZCode sign-in sends nothing`() = runTest {
        assertEquals(
            AskOutcome.Failed(QuotaErrorKind.Auth),
            resets.ask(credentials(ZCodeSignIn.Missing)),
        )
        assertEquals(
            AskOutcome.Failed(QuotaErrorKind.Network),
            resets.ask(credentials(ZCodeSignIn.Unavailable)),
        )
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `the logs never carry the ZCode tokens`() = runTest {
        server.enqueueJson(fixture("zai/reset_status.json"))
        server.enqueueJson(used())
        server.enqueueJson(ok)
        redeem()

        assertTrue(log.debugs.isNotEmpty())
        log.all.forEach { line ->
            assertFalse("zcode-jwt" in line, line)
            assertFalse("business" in line, line)
        }
    }
}
