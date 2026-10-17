package dev.sebastiano.headroom.model

import dev.sebastiano.headroom.model.stats.ResetPeaks
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlin.time.measureTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class DemoDataTest {
    private val now = Instant.parse("2026-09-27T12:32:00Z")

    @Test
    fun `demo data has one account per showcased provider`() {
        val providers = DemoData.accounts(now).map { it.account.provider }
        assertEquals(
            listOf(Provider.Claude, Provider.Codex, Provider.Grok, Provider.Copilot),
            providers,
        )
    }

    @Test
    fun `the long demo list adds one account for every other provider`() {
        val accounts = DemoData.manyAccounts(now)

        assertEquals(DemoData.accounts(now), accounts.take(4))
        assertEquals(Provider.entries.toSet(), accounts.map { it.account.provider }.toSet())
        assertEquals(accounts.size, accounts.map { it.account.id }.toSet().size)
        assertTrue(accounts.all { it.primaryWindow != null })
    }

    @Test
    fun `the expired sign-in scenario has Claude two hours stale and the others fresh`() {
        val accounts = DemoData.accountsWithExpiredSignIn(now)

        assertEquals(DemoData.accounts(now).map { it.account }, accounts.map { it.account })
        val claude = accounts.first()
        assertTrue(claude.isSignInExpired)
        assertEquals(now.minus(2.hours), claude.snapshot?.fetchedAt)
        assertEquals(DemoData.accounts(now).drop(1), accounts.drop(1))
    }

    @Test
    fun `the soonest weekly reset in demo data is Grok`() {
        val next = NextReset.find(DemoData.accounts(now), now)
        assertEquals(Provider.Grok, next?.account?.provider)
        assertEquals("15h 28m", Countdown.format(next!!.window.resetsAt!! - now))
    }

    @Test
    fun `Claude and Grok need attention in demo data Codex does not`() {
        val byProvider = DemoData.accounts(now).associateBy { it.account.provider }
        assertTrue(
            byProvider.getValue(Provider.Claude).primaryWindow!!.let {
                Pace.needsAttention(it, now)
            }
        )
        assertTrue(
            byProvider.getValue(Provider.Grok).primaryWindow!!.let { Pace.needsAttention(it, now) }
        )
        assertEquals(
            false,
            byProvider.getValue(Provider.Codex).primaryWindow!!.let {
                Pace.needsAttention(it, now)
            },
        )
    }

    @Test
    fun `fake repository refresh bumps usage and marks the sync time`() = runTest {
        val repo = FakeQuotaRepository(clock = { now })
        val before =
            repo.accounts.value.first { it.account.provider == Provider.Claude }.primaryWindow!!
        repo.refresh()
        val after = repo.accounts.first().first { it.account.provider == Provider.Claude }
        assertTrue(after.primaryWindow!!.usedPercent > before.usedPercent)
        assertEquals(now, after.snapshot!!.fetchedAt)
    }

    @Test
    fun `long-term demo usage covers a year shaped by compaction and short-term covers sixty days`() {
        val claude = DemoData.accounts(now).first { it.account.provider == Provider.Claude }
        val window = claude.primaryWindow!!
        val peaks = DemoData.resetPeaks(claude.account.id, window.id)

        val longTerm = DemoUsage.points(window, peaks, now, TimeZone.UTC, HistoryRetention.LongTerm)
        val shortTerm =
            DemoUsage.points(window, peaks, now, TimeZone.UTC, HistoryRetention.ShortTerm)

        assertTrue(longTerm.first().at >= now.minus(365.days))
        assertTrue(longTerm.first().at <= now.minus(364.days))
        assertEquals(52, ResetPeaks.usedAtResets(longTerm).size)
        assertEquals(peaks, ResetPeaks.usedAtResets(longTerm).takeLast(peaks.size))
        // Already compacted: running compaction again changes nothing.
        assertEquals(longTerm, HistoryRetention.LongTerm.compact(longTerm, now))

        assertTrue(shortTerm.first().at >= now.minus(60.days))
        assertTrue(shortTerm.first().at <= now.minus(59.days))
        assertEquals(8, ResetPeaks.usedAtResets(shortTerm).size)
        assertEquals(peaks, ResetPeaks.usedAtResets(shortTerm).takeLast(peaks.size))
    }

    @Test
    fun `generating a year of compacted demo history for all demo accounts stays bounded`() {
        val accounts = DemoData.accounts(now)
        var totalPoints = 0
        val elapsed = measureTime {
            accounts.forEach { state ->
                val window = state.primaryWindow!!
                val points =
                    DemoUsage.points(
                        window,
                        DemoData.resetPeaks(state.account.id, window.id),
                        now,
                        TimeZone.UTC,
                        HistoryRetention.LongTerm,
                    )
                assertTrue(points.isNotEmpty())
                totalPoints += points.size
            }
        }
        println(
            "Demo history generation for ${accounts.size} accounts ($totalPoints points): $elapsed"
        )
        assertEquals(7_475, totalPoints)
    }

    @Test
    fun `demo usage weights follow local wall-clock hours across spring and autumn DST transitions`() {
        val rome = TimeZone.of("Europe/Rome")
        // Spring forward in Europe/Rome: Sunday 2026-03-29 (02:00 CET -> 03:00 CEST).
        val springReset = Instant.parse("2026-04-02T12:00:00Z")
        // Fall back in Europe/Rome: Sunday 2026-10-25 (03:00 CEST -> 02:00 CET).
        val autumnReset = Instant.parse("2026-10-29T12:00:00Z")

        for (resetAt in listOf(springReset, autumnReset)) {
            val window =
                QuotaWindow(
                    id = "seven_day",
                    label = "Weekly",
                    kind = WindowKind.Weekly,
                    usedPercent = 80.0,
                    resetsAt = resetAt,
                    length = 7.days,
                )
            val points =
                DemoUsage.points(
                    window = window,
                    peaks = listOf(85.0),
                    now = resetAt,
                    zone = rome,
                    retention = HistoryRetention.ShortTerm,
                )
            val currentCycleStart = resetAt.minus(7.days)
            val cyclePoints = points.filter { it.at >= currentCycleStart && it.at < resetAt }
            val deltasByStart =
                cyclePoints.zipWithNext().map { (from, to) ->
                    from.at.toLocalDateTime(rome) to (to.usedPercent - from.usedPercent)
                }
            // Check the Monday after the Sunday DST transition.
            val transitionDate = currentCycleStart.plus(4.days).toLocalDateTime(rome).date
            val mondayDeltas =
                deltasByStart
                    .filter { (local, _) -> local.date == transitionDate }
                    .associate { (local, delta) -> local.hour to delta }

            for (nightHour in (0..8) + 23) {
                assertEquals(
                    0.0,
                    mondayDeltas.getValue(nightHour),
                    1e-9,
                    "Expected no burn at local hour $nightHour on $transitionDate after DST",
                )
            }
            val morningDelta = mondayDeltas.getValue(9)
            val afternoonDelta = mondayDeltas.getValue(13)
            assertTrue(
                morningDelta > 0.0,
                "Expected positive burn at 09:00 local time on $transitionDate",
            )
            assertEquals(1.4, afternoonDelta / morningDelta, 1e-6)
        }
    }
}
