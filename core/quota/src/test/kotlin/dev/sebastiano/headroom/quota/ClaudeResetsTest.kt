package dev.sebastiano.headroom.quota

import dev.sebastiano.headroom.model.ResetAvailability
import dev.sebastiano.headroom.model.ResetPoolStatus
import dev.sebastiano.headroom.model.ResetScope
import dev.sebastiano.headroom.model.ResetTiming
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ClaudeResetsTest {
    private val server = MockWebServer()
    private val log = RecordingResetLog()
    private lateinit var resets: ClaudeResets

    @BeforeEach
    fun setUp() {
        server.start()
        resets = ClaudeResets(OkHttpQuotaHttpClient(), FIXED_CLOCK, log, server.baseUrl())
    }

    @AfterEach
    fun tearDown() {
        server.close()
    }

    private fun credentials() = ProviderCredentials(accessToken = "test-access-token")

    private suspend fun availability(body: String): ResetAvailability? {
        server.enqueueJson(body)
        return assertIs<ResetRead.Known>(resets.read(credentials())).availability
    }

    private fun cedarEmber(block: String) = """{"five_hour":{"utilization":10.0},$block}"""

    @Test
    fun `asks for the saved resets with the CLI identity`() = runTest {
        server.enqueueJson(fixture("claude/usage_cedar_ember.json"))

        resets.read(credentials())

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/api/oauth/usage?cedar_ember=1&skip_spend=1", request.target)
        assertEquals("Bearer test-access-token", request.headers["Authorization"])
        assertEquals("oauth-2025-04-20", request.headers["anthropic-beta"])
        assertEquals(
            "claude-cli/${ClaudeCodeIdentity.VERSION} (external, cli)",
            request.headers["User-Agent"],
        )
    }

    @Test
    fun `each live grant is a pool that clears its windows`() = runTest {
        val pools = availability(fixture("claude/usage_cedar_ember.json"))!!.pools

        assertEquals(listOf("launch_week", "team_bonus", "anytime"), pools.map { it.id })
        val launch = pools[0]
        assertEquals("Launch week", launch.label)
        assertEquals(1, launch.available)
        assertEquals(2, launch.total)
        assertEquals(ResetScope.ofWindows("five_hour", "seven_day"), launch.scope)
        assertEquals(listOf(Instant.parse("2026-04-15T00:00:00Z")), launch.expiries)
        assertEquals(ResetPoolStatus.Ready, launch.status)
        assertEquals(ResetTiming.AtLimit, launch.timing)
    }

    @Test
    fun `a grant behind the next one is queued, and a paused grant is paused`() = runTest {
        val pools = availability(fixture("claude/usage_cedar_ember.json"))!!.pools

        assertEquals(ResetPoolStatus.Queued, pools[1].status)
        assertEquals(emptyList(), pools[1].expiries)
        assertEquals(ResetPoolStatus.Paused, pools[2].status)
        assertEquals(ResetTiming.AnyTime, pools[2].timing)
    }

    @Test
    fun `the count reads as one now, plus the queued ones`() = runTest {
        val availability = availability(fixture("claude/usage_cedar_ember.json"))!!

        assertEquals(1, availability.availableNow)
        assertEquals(3, availability.queued)
    }

    @Test
    fun `the next grant that needs a limit and is not usable waits for a limit`() = runTest {
        val body =
            cedarEmber(
                """"cedar_ember":{"eligible":true,"next_grant_id":"g","grants":[
                {"id":"g","label":"G","resets_total":1,"resets_left":1,"clears":["seven_day"],
                "paused":false,"usable_now":false,"use_requires_limit":true}]}"""
            )

        assertEquals(ResetPoolStatus.WaitingForLimit, availability(body)!!.pools.single().status)
    }

    @Test
    fun `the next grant that works at any time but is not usable now is not usable yet`() =
        runTest {
            val body =
                cedarEmber(
                    """"cedar_ember":{"eligible":true,"next_grant_id":"g","grants":[
                    {"id":"g","label":"G","resets_total":1,"resets_left":1,"clears":["seven_day"],
                    "paused":false,"usable_now":false,"use_requires_limit":false}]}"""
                )

            assertEquals(ResetPoolStatus.NotUsableYet, availability(body)!!.pools.single().status)
        }

    @Test
    fun `a grant that has not started is not usable yet, even when it needs a limit`() = runTest {
        val body =
            cedarEmber(
                """"cedar_ember":{"eligible":true,"next_grant_id":"g","grants":[
                {"id":"g","label":"G","resets_total":1,"resets_left":1,"clears":["seven_day"],
                "starts_at":"2026-04-04T00:00:00Z","paused":false,"usable_now":true,
                "use_requires_limit":true}]}"""
            )

        assertEquals(ResetPoolStatus.NotUsableYet, availability(body)!!.pools.single().status)
    }

    @Test
    fun `an account the program does not cover has no resets`() = runTest {
        assertNull(availability(cedarEmber(""""cedar_ember":null""")))
        assertNull(availability("""{"five_hour":{"utilization":10.0}}"""))
        listOf("surface", "config_off", "no_grant", "cli_version", "unavailable").forEach {
            assertNull(
                availability(
                    cedarEmber(""""cedar_ember":{"eligible":false,"ineligible_reason":"$it"}""")
                ),
                it,
            )
        }
    }

    @Test
    fun `a plan or an account that cannot have resets says so`() = runTest {
        listOf("tier", "seat", "tenure").forEach {
            val availability =
                availability(
                    cedarEmber(""""cedar_ember":{"eligible":false,"ineligible_reason":"$it"}""")
                )!!
            assertTrue(availability.pools.isEmpty(), it)
            assertTrue(availability.ineligibleReason!!.isNotBlank(), it)
        }
    }

    @Test
    fun `logs the reason Claude gives for an account outside the program`() = runTest {
        listOf("cli_version", "surface", "tier").forEach {
            availability(
                cedarEmber(""""cedar_ember":{"eligible":false,"ineligible_reason":"$it"}""")
            )

            assertTrue(log.debugs.last().contains("ineligible $it"), log.debugs.last())
        }
        assertTrue(log.all.none { "test-access-token" in it })
    }

    @Test
    fun `logs a strange ineligible reason without its text`() = runTest {
        val reason = "user@example.com eyJhbGciOiJIUzI1NiJ9.e30.sig"
        availability(
            cedarEmber(""""cedar_ember":{"eligible":false,"ineligible_reason":"$reason"}""")
        )

        assertTrue(log.debugs.last().contains("ineligible other"), log.debugs.last())
        assertTrue(log.all.none { "example.com" in it || "eyJ" in it })
    }

    @Test
    fun `a failed or unreadable answer fails the read and is logged`() = runTest {
        server.enqueueStatus(401)
        server.enqueueJson(cedarEmber(""""cedar_ember":{"eligible":true,"grants":"nope"}"""))

        assertEquals(ResetRead.Failed, resets.read(credentials()))
        assertEquals(ResetRead.Failed, resets.read(credentials()))

        assertTrue(log.debugs.any { "401" in it && "/api/oauth/usage" in it })
        assertTrue(log.warns.any { "cedar_ember" in it })
        assertTrue(log.all.none { "test-access-token" in it })
    }
}
