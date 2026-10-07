package dev.sebastiano.headroom.quota

import dev.sebastiano.headroom.model.QuotaErrorKind
import dev.sebastiano.headroom.model.RedeemOutcome
import java.io.IOException
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ClaudeResetRedeemTest {
    private val server = MockWebServer()
    private val log = RecordingResetLog()
    private val requests = mutableListOf<RecordedRequest>()
    private lateinit var resets: ClaudeResets

    /** What each endpoint answers now. A test changes them between calls. */
    private var status: MockResponse = jsonResponse(fixture("claude/usage_cedar_ember.json"))
    private var profile: MockResponse = jsonResponse(fixture("claude/profile_max_20x.json"))
    private var redeem: MockResponse = jsonResponse(fixture("claude/reset/reset.json"))

    @BeforeEach
    fun setUp() {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    synchronized(requests) { requests += request }
                    return when (request.target) {
                        STATUS_TARGET -> status
                        PROFILE_TARGET -> profile
                        REDEEM_TARGET -> redeem
                        else -> MockResponse(code = 404)
                    }
                }
            }
        server.start()
        resets = ClaudeResets(OkHttpQuotaHttpClient(), FIXED_CLOCK, log, server.baseUrl())
    }

    @AfterEach
    fun tearDown() {
        server.close()
    }

    private fun credentials() = ProviderCredentials(accessToken = "test-access-token")

    private suspend fun redeem(key: String = KEY, grant: String = "launch_week") =
        resets.redeem(credentials(), grant, key)

    private fun redeemRequests() = requests.filter { it.target == REDEEM_TARGET }

    private fun answer(name: String) {
        redeem = jsonResponse(fixture("claude/reset/$name.json"))
    }

    @Test
    fun `a redeem posts the next grant to the organization from the profile`() = runTest {
        redeem()

        val post = redeemRequests().single()
        assertEquals("POST", post.method)
        assertEquals("Bearer test-access-token", post.headers["Authorization"])
        assertEquals("oauth-2025-04-20", post.headers["anthropic-beta"])
        assertEquals("application/json", post.headers["Content-Type"])
        val body = Json.parseToJsonElement(post.body!!.utf8()).jsonObject
        assertEquals("cedar_ember", body["program"]!!.jsonPrimitive.content)
        assertEquals("launch_week", body["grant_id"]!!.jsonPrimitive.content)
        assertEquals(KEY, body["request_id"]!!.jsonPrimitive.content)
    }

    @Test
    fun `reset is a success, with the resets left`() = runTest {
        answer("reset")

        assertEquals(RedeemOutcome.Success(resetsLeft = 0), redeem())
    }

    @Test
    fun `already_used is a success of an earlier try with the same key`() = runTest {
        answer("already_used")

        assertEquals(RedeemOutcome.Success(resetsLeft = 0, replayed = true), redeem())
    }

    @Test
    fun `not_limited used nothing, because nothing was used yet`() = runTest {
        answer("not_limited")

        assertEquals(RedeemOutcome.NothingToReset, redeem())
    }

    @Test
    fun `cooldown means another reset was just started`() = runTest {
        answer("cooldown")

        assertEquals(RedeemOutcome.Cooldown, redeem())
    }

    @Test
    fun `ineligible means the grant can no longer be used`() = runTest {
        answer("ineligible")

        assertEquals(RedeemOutcome.Ineligible, redeem())
    }

    @Test
    fun `unavailable is unconfirmed, so the key is kept`() = runTest {
        answer("unavailable")

        val outcome = redeem()

        assertEquals(RedeemOutcome.Unconfirmed, outcome)
        assertTrue(!outcome.isSettled)
    }

    @Test
    fun `a result the app does not know may have worked, so the key can be sent again`() = runTest {
        redeem = jsonResponse("""{"result":"something_new"}""")

        assertEquals(RedeemOutcome.Failed(QuotaErrorKind.Unknown), redeem())
    }

    @Test
    fun `an unreadable answer fails, and says why in the log`() = runTest {
        redeem = jsonResponse("""{"outcome":"reset"}""")

        assertEquals(RedeemOutcome.Failed(QuotaErrorKind.Parse), redeem())
        assertTrue(log.warns.any { "result" in it })
    }

    @Test
    fun `HTTP 429 is rate limiting, with the time to wait`() = runTest {
        redeem = MockResponse.Builder().code(429).addHeader("Retry-After", "30").body("{}").build()

        assertEquals(RedeemOutcome.RateLimited(Instant.parse("2026-04-03T12:00:30Z")), redeem())
    }

    @Test
    fun `HTTP 401 or 403 asks the user to sign in again`() = runTest {
        redeem = MockResponse(code = 401)
        assertEquals(RedeemOutcome.SignInAgain, redeem())

        redeem = MockResponse(code = 403)
        assertEquals(RedeemOutcome.SignInAgain, redeem())
    }

    @Test
    fun `another HTTP error fails, and can be tried again with the same key`() = runTest {
        redeem = MockResponse(code = 500)

        assertEquals(RedeemOutcome.Failed(QuotaErrorKind.Unknown), redeem())
    }

    @Test
    fun `a lost connection fails as a network error`() = runTest {
        val client = FakeQuotaHttpClient { request ->
            when {
                request.method == "POST" -> throw IOException("connection reset")
                request.url.endsWith(PROFILE_TARGET) ->
                    QuotaHttpResponse(200, body = fixture("claude/profile_max_20x.json"))
                else -> QuotaHttpResponse(200, body = fixture("claude/usage_cedar_ember.json"))
            }
        }
        val offline = ClaudeResets(client, FIXED_CLOCK, log, "https://claude.test")

        assertEquals(
            RedeemOutcome.Failed(QuotaErrorKind.Network),
            offline.redeem(credentials(), "launch_week", KEY),
        )
        assertEquals(1, client.requests.count { it.method == "POST" })
    }

    @Test
    fun `without the organization nothing is sent`() = runTest {
        profile = MockResponse(code = 500)
        assertEquals(RedeemOutcome.Failed(QuotaErrorKind.Unknown), redeem())

        profile = jsonResponse("""{"organization":{}}""")
        assertEquals(RedeemOutcome.Failed(QuotaErrorKind.Parse), redeem())

        assertTrue(redeemRequests().isEmpty())
    }

    @Test
    fun `without the grant's count before the send, nothing is sent`() = runTest {
        status = MockResponse(code = 500)
        assertEquals(RedeemOutcome.Failed(QuotaErrorKind.Unknown), redeem())

        status = jsonResponse("""{"cedar_ember":{"grants":"nope"}}""")
        assertEquals(RedeemOutcome.Failed(QuotaErrorKind.Parse), redeem())

        assertTrue(redeemRequests().isEmpty())
    }

    @Test
    fun `a grant listed without a count is unreadable, and nothing is sent`() = runTest {
        status =
            jsonResponse(
                fixture("claude/usage_cedar_ember.json")
                    .replaceFirst("\"resets_left\": 1", "\"resets_left\": \"one\"")
            )

        assertEquals(RedeemOutcome.Failed(QuotaErrorKind.Parse), redeem())
        assertTrue(redeemRequests().isEmpty())
    }

    @Test
    fun `a status that asks for a sign-in asks the user to sign in again`() = runTest {
        status = MockResponse(code = 401)

        assertEquals(RedeemOutcome.SignInAgain, redeem())
        assertTrue(redeemRequests().isEmpty())
    }

    @Test
    fun `a grant the status no longer lists cannot be used, and nothing is sent`() = runTest {
        assertEquals(RedeemOutcome.Ineligible, redeem(grant = "gone"))
        assertTrue(redeemRequests().isEmpty())
    }

    @Test
    fun `a retry keeps the count from before the first send`() = runTest {
        answer("unavailable")
        redeem()
        // The first send worked after all: the retry reads one reset fewer, and is answered as a
        // replay. The check still compares with the count from before the first send.
        status = jsonResponse(fixture("claude/usage_cedar_ember.json").replaceLaunchWeekLeft(0))
        answer("unavailable")
        redeem()

        assertEquals(
            RedeemOutcome.Success(resetsLeft = 0, replayed = true),
            resets.check(credentials(), "launch_week", KEY),
        )
    }

    @Test
    fun `a profile that asks for a sign-in asks the user to sign in again`() = runTest {
        profile = MockResponse(code = 401)

        assertEquals(RedeemOutcome.SignInAgain, redeem())
        assertTrue(redeemRequests().isEmpty())
    }

    @Test
    fun `a retry of the same attempt sends the same request id`() = runTest {
        redeem = MockResponse(code = 500)
        redeem()
        answer("already_used")
        redeem()

        val ids =
            redeemRequests().map {
                Json.parseToJsonElement(it.body!!.utf8())
                    .jsonObject["request_id"]!!
                    .jsonPrimitive
                    .content
            }
        assertEquals(listOf(KEY, KEY), ids)
    }

    @Test
    fun `a key Claude would refuse becomes a stable request id it accepts`() = runTest {
        val key = "key with spaces/and:colons"
        redeem(key)
        redeem(key)

        val ids =
            redeemRequests().map {
                Json.parseToJsonElement(it.body!!.utf8())
                    .jsonObject["request_id"]!!
                    .jsonPrimitive
                    .content
            }
        assertEquals(ids[0], ids[1])
        assertTrue(Regex("^[A-Za-z0-9_-]{1,64}$").matches(ids[0]), ids[0])
    }

    @Test
    fun `a check after an unconfirmed redeem finds the grant used`() = runTest {
        answer("unavailable")
        redeem()
        status = jsonResponse(fixture("claude/usage_cedar_ember.json").replaceLaunchWeekLeft(0))

        val outcome = resets.check(credentials(), "launch_week", KEY)

        assertEquals(RedeemOutcome.Success(resetsLeft = 0, replayed = true), outcome)
        assertEquals(1, redeemRequests().size)
    }

    @Test
    fun `a try that sent nothing keeps no count, so a reset used elsewhere is not this one`() =
        runTest {
            // The first try reads the count, then fails before the send.
            profile = MockResponse(code = 500)
            redeem()
            // The grant is used elsewhere. The retry sends, and the answer is lost.
            status = jsonResponse(fixture("claude/usage_cedar_ember.json").replaceLaunchWeekLeft(0))
            profile = jsonResponse(fixture("claude/profile_max_20x.json"))
            answer("unavailable")
            redeem()

            assertEquals(RedeemOutcome.Unconfirmed, resets.check(credentials(), "launch_week", KEY))
        }

    @Test
    fun `a check that finds the grant unchanged is still unconfirmed`() = runTest {
        answer("unavailable")
        redeem()

        assertEquals(RedeemOutcome.Unconfirmed, resets.check(credentials(), "launch_week", KEY))
        assertEquals(1, redeemRequests().size)
    }

    @Test
    fun `a check that cannot read the status is still unconfirmed`() = runTest {
        answer("unavailable")
        redeem()
        status = MockResponse(code = 500)

        assertEquals(RedeemOutcome.Unconfirmed, resets.check(credentials(), "launch_week", KEY))
    }

    @Test
    fun `a check of a key that was never sent is unconfirmed`() = runTest {
        assertEquals(RedeemOutcome.Unconfirmed, resets.check(credentials(), "launch_week", KEY))
        assertTrue(redeemRequests().isEmpty())
    }

    @Test
    fun `the log names the result and the key, but never the organization or a token`() = runTest {
        answer("cooldown")
        redeem()

        assertTrue(log.debugs.any { "cooldown" in it && KEY in it })
        assertTrue(log.debugs.any { "/api/organizations/" in it })
        assertTrue(log.all.none { ORGANIZATION in it }, log.all.toString())
        assertTrue(log.all.none { "test-access-token" in it })
    }

    private fun String.replaceLaunchWeekLeft(left: Int): String =
        replaceFirst("\"resets_left\": 1", "\"resets_left\": $left")

    private companion object {
        const val KEY = "8d0f6a52-4f0e-4a63-9b7c-2c1a3e9d5b10"
        const val ORGANIZATION = "00000000-0000-0000-0000-000000000002"
        const val STATUS_TARGET = "/api/oauth/usage?cedar_ember=1&skip_spend=1"
        const val PROFILE_TARGET = "/api/oauth/profile"
        const val REDEEM_TARGET = "/api/organizations/$ORGANIZATION/reset_rate_limits"
    }
}
