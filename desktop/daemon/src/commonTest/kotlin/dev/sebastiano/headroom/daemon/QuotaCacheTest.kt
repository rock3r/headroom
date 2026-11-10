package dev.sebastiano.headroom.daemon

import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.QuotaErrorKind
import dev.sebastiano.headroom.model.QuotaResult
import dev.sebastiano.headroom.model.QuotaSnapshot
import dev.sebastiano.headroom.model.QuotaWindow
import dev.sebastiano.headroom.model.WindowKind
import dev.sebastiano.headroom.quota.ProviderCredentials
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest

class QuotaCacheTest {
    private val now = Instant.parse("2026-11-10T12:00:00Z")

    @Test
    fun aFailedRefreshKeepsTheLastNumbersAndDoesNotInventResets() = runTest {
        var calls = 0
        val source = QuotaSource { account ->
            calls += 1
            if (calls == 1) {
                QuotaResult.Success(snapshot(account, usedPercent = 40.0, resetsAt = now + 2.days))
            } else {
                QuotaResult.Failure(QuotaErrorKind.Network, "offline")
            }
        }
        val cache = QuotaCache(source)
        val account = DaemonAccount(Provider.Claude, "acct-1", ProviderCredentials("token"))

        val first = cache.refresh(listOf(account)).single()
        val second = cache.refresh(listOf(account)).single()
        val report = cache.report(now)

        assertEquals(40.0, first.snapshot?.windows?.single()?.usedPercent)
        assertEquals(40.0, second.snapshot?.windows?.single()?.usedPercent)
        assertEquals(QuotaErrorKind.Network, second.error)
        assertTrue(report.contains("\"freshness\":\"Stale\""))
        assertFalse(report.contains("token"))
        assertFalse(report.contains("redeem"))
    }

    @Test
    fun hookReadsTheCacheAndFailsOpenOnGarbage() = runTest {
        val source = QuotaSource { account ->
            QuotaResult.Success(snapshot(account, usedPercent = 96.0, resetsAt = now + 1.days))
        }
        val cache = QuotaCache(source)
        cache.refresh(
            listOf(DaemonAccount(Provider.Claude, "acct-1", ProviderCredentials("token")))
        )
        val responder =
            HookResponder(readings = { kotlinx.coroutines.runBlocking { cache.current() } })

        val first =
            responder.respond(
                AgentHook.ClaudeCode,
                """{"hook_event_name":"UserPromptSubmit"}""",
                now,
            )
        val second =
            responder.respond(AgentHook.Codex, """{"hook_event_name":"UserPromptSubmit"}""", now)
        val garbage = responder.respond(AgentHook.ClaudeCode, "not json", now)

        assertTrue(first.contains("additionalContext"))
        assertEquals("{}", second)
        assertEquals("{}", garbage)
    }

    private fun snapshot(
        account: DaemonAccount,
        usedPercent: Double,
        resetsAt: Instant,
    ): QuotaSnapshot =
        QuotaSnapshot(
            provider = account.provider,
            accountId = account.accountId,
            planLabel = "secret-plan",
            windows =
                listOf(
                    QuotaWindow(
                        id = "seven_day",
                        label = "Weekly",
                        kind = WindowKind.Weekly,
                        usedPercent = usedPercent,
                        resetsAt = resetsAt,
                        length = 7.days,
                    )
                ),
            fetchedAt = now,
        )
}
