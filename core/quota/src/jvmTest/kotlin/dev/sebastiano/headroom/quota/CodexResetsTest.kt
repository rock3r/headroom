package dev.sebastiano.headroom.quota

import dev.sebastiano.headroom.model.QuotaErrorKind
import dev.sebastiano.headroom.model.RedeemOutcome
import dev.sebastiano.headroom.model.ResetScope
import dev.sebastiano.headroom.model.WindowKind
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CodexResetsTest {
    private val server = MockWebServer()
    private val log = RecordingResetLog()
    private val resets = CodexResets(KtorQuotaHttpClient(), FIXED_CLOCK, log)

    @BeforeEach
    fun setUp() {
        server.start()
    }

    @AfterEach
    fun tearDown() {
        server.close()
    }

    private fun credentials() =
        ProviderCredentials(
            accessToken = "test-access-token",
            accountId = "chatgpt-account",
            baseUrl = server.baseUrl(),
        )

    private fun requests(): List<RecordedRequest> =
        List(server.requestCount) { server.takeRequest() }

    private suspend fun redeem(key: String = "key-1") = resets.redeem(credentials(), "codex", key)

    @Test
    fun `lists the credits with the ChatGPT headers`() = runTest {
        server.enqueueJson(fixture("codex/reset_credits.json"))

        resets.read(credentials())

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/wham/rate-limit-reset-credits", request.target)
        assertEquals("Bearer test-access-token", request.headers["Authorization"])
        assertEquals("chatgpt-account", request.headers["ChatGPT-Account-Id"])
        assertEquals("application/json", request.headers["Accept"])
    }

    @Test
    fun `one pool holds the available, unexpired credits, soonest expiry first`() = runTest {
        server.enqueueJson(fixture("codex/reset_credits.json"))

        val read = assertIs<ResetRead.Known>(resets.read(credentials()))

        val pool = read.availability!!.pools.single()
        assertEquals("codex", pool.id)
        assertEquals(2, pool.available)
        assertEquals(ResetScope.of(WindowKind.Session, WindowKind.Weekly), pool.scope)
        assertEquals(
            listOf(
                Instant.parse("2026-04-05T23:58:50.843110Z"),
                Instant.parse("2026-04-20T02:01:21.959053Z"),
            ),
            pool.expiries,
        )
    }

    @Test
    fun `an account with no credits has an empty pool`() = runTest {
        server.enqueueJson(fixture("codex/reset_credits_none.json"))

        val read = assertIs<ResetRead.Known>(resets.read(credentials()))

        assertEquals(0, read.availability!!.pools.single().available)
    }

    @Test
    fun `a failed or unreadable list fails the read, and says why in the log`() = runTest {
        server.enqueueStatus(500)
        server.enqueueJson("""{"credits":"nope"}""")

        assertEquals(ResetRead.Failed, resets.read(credentials()))
        assertEquals(ResetRead.Failed, resets.read(credentials()))

        assertTrue(log.debugs.any { "500" in it && "/wham/rate-limit-reset-credits" in it })
        assertTrue(log.warns.any { "credits" in it })
    }

    @Test
    fun `a redeem spends the soonest-expiring credit, with the attempt key`() = runTest {
        server.enqueueJson(fixture("codex/reset_credits.json"))
        server.enqueueJson("""{"code":"reset","windows_reset":2}""")

        val outcome = redeem()

        assertEquals(RedeemOutcome.Success(resetsLeft = 1), outcome)
        val consume = requests().last()
        assertEquals("POST", consume.method)
        assertEquals("/wham/rate-limit-reset-credits/consume", consume.target)
        assertEquals("chatgpt-account", consume.headers["ChatGPT-Account-Id"])
        assertEquals(
            """{"redeem_request_id":"key-1","credit_id":"RateLimitResetCredit_soon"}""",
            consume.body?.utf8(),
        )
    }

    @Test
    fun `a retry of the same attempt addresses the same credit`() = runTest {
        server.enqueueJson(fixture("codex/reset_credits.json"))
        server.enqueueStatus(502)
        server.enqueueJson("""{"code":"already_redeemed"}""")

        assertEquals(RedeemOutcome.Failed(QuotaErrorKind.Unknown), redeem())
        assertEquals(RedeemOutcome.Success(resetsLeft = null, replayed = true), redeem())

        val consumes = requests().filter { it.method == "POST" }
        assertEquals(2, consumes.size)
        consumes.forEach {
            assertEquals(
                """{"redeem_request_id":"key-1","credit_id":"RateLimitResetCredit_soon"}""",
                it.body?.utf8(),
            )
        }
    }

    @Test
    fun `without a readable list the consume lets the server pick`() = runTest {
        server.enqueueStatus(500)
        server.enqueueJson("""{"code":"reset"}""")

        assertEquals(RedeemOutcome.Success(resetsLeft = null), redeem())

        assertEquals("""{"redeem_request_id":"key-1"}""", requests().last().body?.utf8())
    }

    @Test
    fun `maps every consume code`() = runTest {
        val expected =
            mapOf(
                "nothing_to_reset" to RedeemOutcome.NothingToReset,
                "no_credit" to RedeemOutcome.NoCredit,
                "something_new" to RedeemOutcome.Failed(QuotaErrorKind.Unknown),
            )
        expected.forEach { (code, outcome) ->
            server.enqueueJson(fixture("codex/reset_credits_none.json"))
            server.enqueueJson("""{"code":"$code"}""")

            assertEquals(outcome, redeem("key-$code"), code)
        }
    }

    @Test
    fun `maps the HTTP failures of a consume`() = runTest {
        val expected =
            listOf(
                MockResponse(code = 401) to RedeemOutcome.SignInAgain,
                MockResponse(code = 403) to RedeemOutcome.SignInAgain,
                MockResponse.Builder().code(429).addHeader("Retry-After", "30").build() to
                    RedeemOutcome.RateLimited(FIXED_NOW + 30.seconds),
                MockResponse(code = 500) to RedeemOutcome.Failed(QuotaErrorKind.Unknown),
                jsonResponse("not json") to RedeemOutcome.Failed(QuotaErrorKind.Parse),
            )
        expected.forEachIndexed { index, (response, outcome) ->
            server.enqueueJson(fixture("codex/reset_credits_none.json"))
            server.enqueue(response)

            assertEquals(outcome, redeem("key-$index"), "case $index")
        }
    }

    @Test
    fun `logs the consume with its key and code, never the token`() = runTest {
        server.enqueueJson(fixture("codex/reset_credits.json"))
        server.enqueueJson("""{"code":"reset","windows_reset":2}""")

        redeem()

        val line = log.debugs.single { "consume" in it }
        assertTrue("key-1" in line, line)
        assertTrue("reset" in line, line)
        assertTrue("200" in line, line)
        assertTrue(log.all.none { "test-access-token" in it || "chatgpt-account" in it })
    }

    @Test
    fun `a network error on consume can be tried again`() = runTest {
        val failing =
            CodexResets(
                FakeQuotaHttpClient { throw java.io.IOException("reset") },
                FIXED_CLOCK,
                log,
            )

        val outcome = failing.redeem(credentials(), "codex", "key-1")

        assertEquals(RedeemOutcome.Failed(QuotaErrorKind.Network), outcome)
    }
}

/** Keeps every line the reset clients log. */
internal class RecordingResetLog : ResetLog {
    val debugs = mutableListOf<String>()
    val warns = mutableListOf<String>()
    val all: List<String>
        get() = debugs + warns

    override fun debug(message: String) {
        debugs += message
    }

    override fun warn(message: String) {
        warns += message
    }
}
