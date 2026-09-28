package dev.sebastiano.headroom.appdata

import dev.sebastiano.headroom.model.DemoData
import dev.sebastiano.headroom.model.FakeQuotaRepository
import dev.sebastiano.headroom.model.QuotaWindow
import dev.sebastiano.headroom.model.UsagePoint
import dev.sebastiano.headroom.model.WindowKind
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest

class UsageHistoryTest {
    private val now = Instant.parse("2026-09-27T12:32:00Z")

    private fun weekly(used: Double) =
        QuotaWindow(
            id = "weekly",
            label = "Weekly",
            kind = WindowKind.Weekly,
            usedPercent = used,
            resetsAt = now.plus(Duration.ofDays(3)),
            length = Duration.ofDays(7),
        )

    @Test
    fun `demo history resets at the given peaks and ends at the current usage`() {
        val peaks = listOf(82.0, 95.0, 100.0, 88.0, 100.0)
        val points = demoUsage(weekly(71.0), peaks, now, ZoneOffset.UTC)
        assertEquals(peaks, ResetPeaks.usedAtResets(points))
        assertEquals(UsagePoint(now, 71.0), points.last())
        assertEquals(points.sortedBy { it.at }, points)
        assertTrue(points.all { it.usedPercent in 0.0..100.0 })
    }

    @Test
    fun `demo history stays inside the retention period`() {
        val peaks = List(20) { 50.0 }
        val points = demoUsage(weekly(10.0), peaks, now, ZoneOffset.UTC)
        assertTrue(points.first().at >= now.minus(Duration.ofDays(60)))
        assertEquals(List(8) { 50.0 }, ResetPeaks.usedAtResets(points))
    }

    @Test
    fun `usage mostly happens in working hours on weekdays`() {
        val points = demoUsage(weekly(40.0), listOf(80.0, 70.0), now, ZoneOffset.UTC)
        val daytime =
            points.zipWithNext().sumOf { (a, b) ->
                val hour = b.at.atZone(ZoneOffset.UTC).hour
                if (hour in 9..18) (b.usedPercent - a.usedPercent).coerceAtLeast(0.0) else 0.0
            }
        val total =
            points.zipWithNext().sumOf { (a, b) ->
                (b.usedPercent - a.usedPercent).coerceAtLeast(0.0)
            }
        assertTrue(daytime / total > 0.6, "daytime share was ${daytime / total}")
    }

    @Test
    fun `a window without a known start has no demo history`() {
        val open = weekly(40.0).copy(resetsAt = null)
        assertEquals(emptyList(), demoUsage(open, listOf(80.0), now, ZoneOffset.UTC))
    }

    @Test
    fun `the demo history follows the demo reset history`() = runTest {
        val window = DemoData.accounts(now).first().primaryWindow!!
        val history = DemoUsageHistory(DemoResetHistory, { now }, ZoneOffset.UTC)
        val points = history.points("demo-claude", window).first()
        assertEquals(listOf(82.0, 95.0, 100.0, 88.0, 100.0), ResetPeaks.usedAtResets(points))
    }

    @Test
    fun `the real history comes from the repository`() = runTest {
        val repository = FakeQuotaRepository({ now })
        val window = repository.accounts.value.first().primaryWindow!!
        val points = RepositoryUsageHistory(repository).points("demo-claude", window).first()
        assertEquals(repository.history("demo-claude", window.id).first(), points)
    }

    @Test
    fun `demo mode picks the demo history`() = runTest {
        val demoMode = MutableStateFlow(true)
        val real = UsageHistory { _, _ -> flowOf(listOf(UsagePoint(now, 1.0))) }
        val demo = UsageHistory { _, _ -> flowOf(listOf(UsagePoint(now, 2.0))) }
        val history = DemoAwareUsageHistory(demoMode, real, demo)
        assertEquals(2.0, history.points("a", weekly(0.0)).first().single().usedPercent)
        demoMode.value = false
        assertEquals(1.0, history.points("a", weekly(0.0)).first().single().usedPercent)
    }
}
