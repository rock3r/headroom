package dev.sebastiano.headroom.ui.stats

import dev.sebastiano.headroom.model.Account
import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.QuotaWindow
import dev.sebastiano.headroom.model.UsagePoint
import dev.sebastiano.headroom.model.WindowKind
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StatsMathsTest {
    /** A Monday, 00:00 UTC. */
    private val monday = Instant.parse("2026-08-31T00:00:00Z")
    private val now = monday.plus(Duration.ofDays(21))

    private fun source(
        id: String,
        provider: Provider,
        vararg points: Pair<Long, Double>,
        used: Double = points.lastOrNull()?.second ?: 0.0,
        nickname: String? = null,
    ) =
        StatsSource(
            account = Account(id, provider, "sam", nickname),
            window =
                QuotaWindow(
                    id = "weekly",
                    label = "Weekly",
                    kind = WindowKind.Weekly,
                    usedPercent = used,
                    resetsAt = now.plus(Duration.ofDays(1)),
                    length = Duration.ofDays(7),
                ),
            points =
                points.map { (hours, value) ->
                    UsagePoint(monday.plusSeconds(hours * 3600), value)
                },
        )

    private fun account(id: String, provider: Provider, name: String = provider.displayName) =
        StatAccount(id, provider, name, WindowKind.Weekly)

    // Three weeks of Claude: 80% then a reset, 100% then a reset, then 30% so far.
    private val claude =
        source(
            "c",
            Provider.Claude,
            0L to 0.0,
            24L to 40.0,
            150L to 80.0,
            170L to 2.0,
            200L to 60.0,
            320L to 100.0,
            340L to 1.0,
            400L to 30.0,
        )

    // Codex: 50% then a reset, then 10%.
    private val codex =
        source("x", Provider.Codex, 10L to 0.0, 100L to 50.0, 180L to 0.0, 190L to 10.0)

    @Test
    fun `with no history every stat is empty`() {
        val empty = source("c", Provider.Claude)
        val stats = stats(listOf(empty), now, ZoneOffset.UTC)
        assertNull(stats.coverage)
        assertNull(stats.resets)
        assertEquals(emptyList<ProviderShare>(), stats.shares)
        assertNull(stats.heatmap)
        assertNull(stats.closestCall)
        assertNull(stats.leftOver)
        assertNull(stats.biggestDay)
        assertEquals(listOf(0), stats.sparklines.map { it.points.size })
    }

    @Test
    fun `with no accounts every stat is empty`() {
        assertEquals(Stats(), stats(emptyList(), now, ZoneOffset.UTC))
    }

    @Test
    fun `coverage counts whole days since the oldest point`() {
        assertEquals(Coverage(days = 21), coverage(listOf(claude, codex), now))
        assertEquals(
            Coverage(days = 0),
            coverage(listOf(source("c", Provider.Claude, 500L to 3.0)), now),
        )
    }

    @Test
    fun `past resets come from every account, oldest first`() {
        val resets = pastResets(listOf(claude, codex))
        assertEquals(listOf(50.0, 80.0, 100.0), resets.map { it.peak })
        assertEquals(listOf("x", "c", "c"), resets.map { it.account.id })
        assertEquals(monday.plusSeconds(150 * 3600L), resets[1].peakAt)
    }

    @Test
    fun `the score counts clean resets and the streak since the last hit`() {
        val score = resetScore(pastResets(listOf(claude, codex)))
        assertEquals(
            ResetScore(total = 3, clean = 2, streak = 0, timeline = listOf(false, false, true)),
            score,
        )
        assertEquals(1, score?.hits)
    }

    @Test
    fun `the streak counts every clean reset after the last hit`() {
        val resets =
            listOf(95.0, 100.0, 40.0, 70.0).mapIndexed { index, peak ->
                PastReset(account("c", Provider.Claude), peak, monday.plusSeconds(index * 3600L))
            }
        assertEquals(2, resetScore(resets)?.streak)
        assertEquals(3, resetScore(resets.filterNot { it.hitLimit })?.streak)
    }

    @Test
    fun `no resets means no score`() {
        assertNull(resetScore(emptyList()))
    }

    @Test
    fun `the closest call is the highest peak that did not hit the limit`() {
        val call = closestCall(pastResets(listOf(claude, codex)))
        assertEquals(80.0, call?.peak)
        assertEquals("c", call?.account?.id)
    }

    @Test
    fun `a tie for the closest call goes to the most recent`() {
        val older = PastReset(account("a", Provider.Grok), 97.0, monday)
        val newer = PastReset(account("b", Provider.Claude), 97.0, monday.plusSeconds(60))
        assertEquals(newer, closestCall(listOf(older, newer)))
    }

    @Test
    fun `with only limit hits there is no closest call`() {
        val hit = PastReset(account("a", Provider.Grok), 100.0, monday)
        assertNull(closestCall(listOf(hit)))
        assertNull(closestCall(emptyList()))
    }

    @Test
    fun `left over averages what was left at each reset, per account too`() {
        val left = leftOver(pastResets(listOf(claude, codex)))
        // Left at the resets: Codex 50, Claude 20 and 0.
        assertEquals(70.0 / 3, left?.averageLeft)
        assertEquals(3, left?.resets)
        assertEquals(
            listOf(
                AccountLeftOver(account("x", Provider.Codex), 50.0, 1),
                AccountLeftOver(account("c", Provider.Claude), 10.0, 2),
            ),
            left?.accounts,
        )
    }

    @Test
    fun `usage above the limit leaves nothing, not less than nothing`() {
        val over = PastReset(account("a", Provider.Grok), 104.0, monday)
        assertEquals(0.0, leftOver(listOf(over))?.averageLeft)
        assertNull(leftOver(emptyList()))
    }

    @Test
    fun `shares split all the burned quota by provider, biggest first`() {
        val secondClaude = source("c2", Provider.Claude, 0L to 10.0, 5L to 30.0, nickname = "Work")
        val shares = shares(listOf(claude, codex, secondClaude))
        // Claude: 80 + 2 + 58 + 40 + 1 + 29 = 210, plus 20 on the second account. Codex: 50 + 10.
        assertEquals(listOf(Provider.Claude, Provider.Codex), shares.map { it.provider })
        assertEquals(listOf(230.0, 60.0), shares.map { it.points })
        assertEquals(1.0, shares.sumOf { it.fraction }, 1e-9)
        assertEquals(230.0 / 290.0, shares.first().fraction, 1e-9)
    }

    @Test
    fun `a provider that burned nothing has no share`() {
        val idle = source("g", Provider.Grok, 0L to 20.0, 10L to 20.0)
        assertEquals(listOf(Provider.Codex), shares(listOf(codex, idle)).map { it.provider })
        assertEquals(emptyList<ProviderShare>(), shares(listOf(idle)))
    }

    @Test
    fun `the heatmap needs a week of history`() {
        val short = source("c", Provider.Claude, 0L to 0.0, 2L to 30.0, 100L to 60.0)
        assertNull(heatmap(listOf(short), ZoneOffset.UTC))
    }

    @Test
    fun `the heatmap puts the use in the hour of the week it happened`() {
        // Monday 09:00 to 10:00 burns 10, and the next Tuesday 14:00 to 16:00 burns 30.
        val week = source("c", Provider.Claude, 9L to 0.0, 10L to 10.0, 206L to 10.0, 208L to 40.0)
        val map = requireNotNull(heatmap(listOf(week), ZoneOffset.UTC))
        assertEquals(10.0, map.at(DayOfWeek.MONDAY, 9))
        assertEquals(15.0, map.at(DayOfWeek.TUESDAY, 14))
        assertEquals(15.0, map.at(DayOfWeek.TUESDAY, 15))
        assertEquals(40.0, map.total)
        assertEquals(15.0, map.max)
        assertEquals(DayOfWeek.TUESDAY to 14, map.busiest)
        assertEquals(DayOfWeek.TUESDAY, map.busiestDay)
        assertEquals(30.0, map.dayTotal(DayOfWeek.TUESDAY))
    }

    @Test
    fun `a week of history with no use has no heatmap`() {
        val idle = source("c", Provider.Claude, 0L to 20.0, 200L to 20.0)
        assertNull(heatmap(listOf(idle), ZoneOffset.UTC))
    }

    @Test
    fun `the biggest day is the most one limit burned in a local day`() {
        // Claude burns 20 on Monday and 15 + 10 on Tuesday. Codex burns 50 in one gap of 90
        // hours, too long to place in a day, and 10 on the second Monday.
        val busy =
            source(
                "c",
                Provider.Claude,
                8L to 0.0,
                10L to 20.0,
                33L to 20.0,
                35L to 35.0,
                40L to 45.0,
            )
        val day = requireNotNull(biggestDay(listOf(busy, codex), ZoneOffset.UTC))
        assertEquals("c", day.account.id)
        assertEquals(LocalDate.parse("2026-09-01"), day.date)
        assertEquals(25.0, day.points, 1e-9)
    }

    @Test
    fun `no use means no biggest day`() {
        assertNull(biggestDay(listOf(source("c", Provider.Claude, 0L to 5.0)), ZoneOffset.UTC))
    }

    @Test
    fun `sparklines keep the last seven days of each account`() {
        val lines = sparklines(listOf(claude, codex), now)
        assertEquals(listOf("c", "x"), lines.map { it.account.id })
        val start = now.minus(Duration.ofDays(7))
        assertTrue(lines.all { line -> line.points.all { !it.at.isBefore(start) } })
        // The span starts at hour 336: Claude's points at hours 340 and 400 are in, Codex has none.
        assertEquals(listOf(1.0, 30.0), lines.first().points.map { it.usedPercent })
        assertEquals(emptyList<UsagePoint>(), lines.last().points)
        assertEquals(30.0, lines.first().current)
        assertEquals(start, lines.first().start)
        assertEquals(now, lines.first().end)
    }

    @Test
    fun `the name the user gave the account is the one shown`() {
        val named = source("c", Provider.Claude, 0L to 10.0, 5L to 20.0, nickname = "Work")
        assertEquals("Work", sparklines(listOf(named), now).single().account.name)
    }

    @Test
    fun `runs group the timeline into clean and hit stretches`() {
        assertEquals(
            listOf(false to 2, true to 1, false to 1, true to 2),
            runs(listOf(false, false, true, false, true, true)),
        )
        assertEquals(emptyList<Pair<Boolean, Int>>(), runs(emptyList()))
    }
}
