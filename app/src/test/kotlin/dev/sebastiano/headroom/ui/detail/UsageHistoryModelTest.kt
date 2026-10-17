package dev.sebastiano.headroom.ui.detail

import dev.c5inco.charts.core.Gaps
import dev.c5inco.charts.core.LineMark
import dev.c5inco.charts.core.LinePattern
import dev.c5inco.charts.core.RuleMark
import dev.c5inco.charts.core.TimeAxis
import dev.c5inco.charts.core.XBandMark
import dev.c5inco.charts.core.XRuleMark
import dev.sebastiano.headroom.model.QuotaWindow
import dev.sebastiano.headroom.model.UsagePoint
import dev.sebastiano.headroom.model.WindowKind
import dev.sebastiano.headroom.ui.ResetFormatter
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class UsageHistoryModelTest {
    private val now = Instant.parse("2026-09-27T12:32:00Z")
    private val weeklyReset = now.plus(3.days)
    private val weeklyStart = weeklyReset.minus(7.days)

    private fun weeklyWindow(
        id: String = "seven_day",
        label: String = "Weekly · all models",
        usedPercent: Double = 71.0,
        resetsAt: Instant = weeklyReset,
    ) =
        QuotaWindow(
            id = id,
            label = label,
            kind = WindowKind.Weekly,
            usedPercent = usedPercent,
            resetsAt = resetsAt,
            length = 7.days,
        )

    private fun monthlyWindow(
        id: String = "monthly",
        label: String = "Monthly",
        usedPercent: Double = 45.0,
        resetsAt: Instant = now.plus(10.days),
    ) =
        QuotaWindow(
            id = id,
            label = label,
            kind = WindowKind.Monthly,
            usedPercent = usedPercent,
            resetsAt = resetsAt,
            length = 30.days,
        )

    @Test
    fun `detectResets finds resets from resetsAt changes even when usage drop is small`() {
        val r1 = Instant.parse("2026-09-16T00:00:00Z")
        val r2 = r1.plus(7.days)
        val t0 = r1.minus(2.hours)
        val t1 = r1.minus(1.hours)
        val t2 = r1
        val points =
            listOf(
                UsagePoint(t0, usedPercent = 1.0, resetsAt = r1),
                UsagePoint(t1, usedPercent = 3.0, resetsAt = r1),
                // Only a 3% drop (< MIN_DROP_POINTS), but resetsAt advanced to r2.
                UsagePoint(t2, usedPercent = 0.0, resetsAt = r2),
                UsagePoint(t2.plus(1.hours), usedPercent = 10.0, resetsAt = r2),
            )

        assertEquals(listOf(t2), detectResets(points))
    }

    @Test
    fun `detectResets finds early redeemed resets where usage drops but resetsAt stays the same`() {
        val r1 = weeklyReset
        val t0 = weeklyStart.plus(1.days)
        val t1 = weeklyStart.plus(2.days)
        val tRedeem = weeklyStart.plus(2.days + 1.hours)
        val points =
            listOf(
                UsagePoint(t0, usedPercent = 40.0, resetsAt = r1),
                UsagePoint(t1, usedPercent = 85.0, resetsAt = r1),
                // Early redeemed reset keeps the same resetsAt, but drops by 85 points.
                UsagePoint(tRedeem, usedPercent = 0.0, resetsAt = r1),
                UsagePoint(tRedeem.plus(2.hours), usedPercent = 12.0, resetsAt = r1),
            )

        assertEquals(listOf(tRedeem), detectResets(points))
    }

    @Test
    fun `detectResets falls back to MIN_DROP_POINTS for legacy points without resetsAt`() {
        val t0 = now.minus(14.days)
        val tWobble = t0.plus(1.days)
        val tPeak = t0.plus(6.days)
        val tReset = t0.plus(7.days)
        val points =
            listOf(
                UsagePoint(t0, usedPercent = 20.0, resetsAt = null),
                // Small wobble (< 5 points) is not a reset.
                UsagePoint(tWobble, usedPercent = 17.0, resetsAt = null),
                UsagePoint(tPeak, usedPercent = 82.0, resetsAt = null),
                UsagePoint(tReset, usedPercent = 0.0, resetsAt = null),
                UsagePoint(tReset.plus(1.days), usedPercent = 25.0, resetsAt = null),
            )

        assertEquals(listOf(tReset), detectResets(points))
    }

    @Test
    fun `buildUsageHistoryModel filters out session daily credit and informational windows`() {
        val weekly = weeklyWindow()
        val opus = weeklyWindow(id = "seven_day_opus", label = "Weekly · Opus", usedPercent = 52.0)
        val monthly = monthlyWindow()
        val session =
            QuotaWindow(
                id = "five_hour",
                label = "Session",
                kind = WindowKind.Session,
                usedPercent = 30.0,
                resetsAt = now.plus(2.hours),
                length = 5.hours,
            )
        val daily =
            QuotaWindow(
                id = "daily",
                label = "Daily",
                kind = WindowKind.Daily,
                usedPercent = 15.0,
                resetsAt = now.plus(12.hours),
                length = 1.days,
            )
        val credit =
            QuotaWindow(
                id = "credit",
                label = "Credit",
                kind = WindowKind.Credit,
                usedPercent = 40.0,
                resetsAt = null,
                length = null,
                expiresAt = now.plus(30.days),
            )
        val unknown =
            QuotaWindow(
                id = "mystery",
                label = "Mystery",
                kind = WindowKind.Weekly,
                usedPercent = 10.0,
                resetsAt = weeklyReset,
                length = 7.days,
                isRecognised = false,
            )

        val model =
            assertNotNull(
                buildUsageHistoryModel(
                    accountId = "acc-1",
                    windows = listOf(session, daily, credit, unknown, opus, weekly, monthly),
                    primaryWindowId = "seven_day",
                    histories = emptyMap(),
                    selectedWindowIds = null,
                    now = now,
                )
            )

        // Primary window leads, followed by the remaining long windows in order.
        assertEquals(listOf("seven_day", "seven_day_opus", "monthly"), model.windows.map { it.id })
        assertTrue(model.windows.first().isPrimary)
        assertEquals(setOf("seven_day", "seven_day_opus", "monthly"), model.selectedWindowIds)
    }

    @Test
    fun `buildUsageHistoryModel returns null when account has no chartable long windows`() {
        val session =
            QuotaWindow(
                id = "five_hour",
                label = "Session",
                kind = WindowKind.Session,
                usedPercent = 30.0,
                resetsAt = now.plus(2.hours),
                length = 5.hours,
            )
        assertNull(
            buildUsageHistoryModel(
                accountId = "acc-1",
                windows = listOf(session),
                primaryWindowId = null,
                histories = emptyMap(),
                selectedWindowIds = null,
                now = now,
            )
        )
    }

    @Test
    fun `availableRanges includes only ranges supported by history and gates 1Y on keepLongHistory`() {
        val window = weeklyWindow()

        fun modelWithOldest(oldest: Instant, keepLongHistory: Boolean = true): UsageHistoryModel =
            requireNotNull(
                buildUsageHistoryModel(
                    accountId = "acc-1",
                    windows = listOf(window),
                    primaryWindowId = window.id,
                    histories =
                        mapOf(
                            window.id to
                                listOf(
                                    UsagePoint(oldest, 10.0, weeklyReset),
                                    UsagePoint(now, 71.0, weeklyReset),
                                )
                        ),
                    selectedWindowIds = null,
                    now = now,
                    keepLongHistory = keepLongHistory,
                )
            )

        // Short history inside the current period: only Period is available.
        assertEquals(
            listOf(HistoryRange.Period),
            modelWithOldest(now.minus(2.hours)).availableRanges,
        )

        // Two weeks of history: Period and 1M.
        assertEquals(
            listOf(HistoryRange.Period, HistoryRange.OneMonth),
            modelWithOldest(now.minus(14.days)).availableRanges,
        )

        // Sixty days of history: Period, 1M and 3M.
        assertEquals(
            listOf(HistoryRange.Period, HistoryRange.OneMonth, HistoryRange.ThreeMonths),
            modelWithOldest(now.minus(60.days), keepLongHistory = false).availableRanges,
        )

        // A full year of history with keepLongHistory on: Period, 1M, 3M and 1Y.
        assertEquals(
            listOf(
                HistoryRange.Period,
                HistoryRange.OneMonth,
                HistoryRange.ThreeMonths,
                HistoryRange.OneYear,
            ),
            modelWithOldest(now.minus(364.days), keepLongHistory = true).availableRanges,
        )

        // A full year of history with keepLongHistory off: 1Y is omitted.
        assertEquals(
            listOf(HistoryRange.Period, HistoryRange.OneMonth, HistoryRange.ThreeMonths),
            modelWithOldest(now.minus(364.days), keepLongHistory = false).availableRanges,
        )
    }

    @Test
    fun `availableRanges and pan bounds stay stable when hiding the window with longer history`() {
        val primary = weeklyWindow(id = "seven_day", label = "Weekly · all models")
        val opus = weeklyWindow(id = "seven_day_opus", label = "Weekly · Opus", usedPercent = 52.0)
        val histories =
            mapOf(
                primary.id to
                    listOf(
                        UsagePoint(now.minus(364.days), 10.0, weeklyReset.minus(357.days)),
                        UsagePoint(now, 71.0, weeklyReset),
                    ),
                opus.id to listOf(UsagePoint(now.minus(2.hours), 52.0, weeklyReset)),
            )

        val bothShown =
            assertNotNull(
                buildUsageHistoryModel(
                    accountId = "acc-1",
                    windows = listOf(primary, opus),
                    primaryWindowId = primary.id,
                    histories = histories,
                    selectedWindowIds = setOf(primary.id, opus.id),
                    now = now,
                )
            )
        val opusOnly =
            assertNotNull(
                buildUsageHistoryModel(
                    accountId = "acc-1",
                    windows = listOf(primary, opus),
                    primaryWindowId = primary.id,
                    histories = histories,
                    selectedWindowIds = setOf(opus.id),
                    now = now,
                )
            )

        assertEquals(bothShown.availableRanges, opusOnly.availableRanges)
        assertEquals(
            listOf(
                HistoryRange.Period,
                HistoryRange.OneMonth,
                HistoryRange.ThreeMonths,
                HistoryRange.OneYear,
            ),
            opusOnly.availableRanges,
        )
        assertEquals(bothShown.minBound, opusOnly.minBound)
        assertEquals(bothShown.maxBound, opusOnly.maxBound)
    }

    @Test
    fun `reset rules use their series colour slot and thin out when closer than 6 dp apart`() {
        val weekly = weeklyWindow(id = "seven_day", label = "Weekly")
        val points = mutableListOf<UsagePoint>()
        for (week in 52 downTo 1) {
            val end = weeklyStart.minus(((week - 1) * 7).days)
            val start = end.minus(7.days)
            points += UsagePoint(start, 0.0, resetsAt = end)
            points += UsagePoint(end.minus(1.minutes), 80.0, resetsAt = end)
        }
        points += UsagePoint(weeklyStart, 0.0, resetsAt = weeklyReset)
        points += UsagePoint(now, 71.0, resetsAt = weeklyReset)

        val model =
            assertNotNull(
                buildUsageHistoryModel(
                    accountId = "acc-1",
                    windows = listOf(weekly),
                    primaryWindowId = weekly.id,
                    histories = mapOf(weekly.id to points),
                    selectedWindowIds = setOf(weekly.id),
                    now = now,
                )
            )
        val formatter = ResetFormatter(ZoneOffset.UTC, Locale.UK, is24Hour = true)
        val monthSpec =
            buildUsageHistoryChartSpec(
                model = model,
                formatter = formatter,
                limitLabel = "limit",
                percentFormat = { "$it%" },
                description = "Usage chart",
                resetStride = 1,
            )
        val yearSpec =
            buildUsageHistoryChartSpec(
                model = model,
                formatter = formatter,
                limitLabel = "limit",
                percentFormat = { "$it%" },
                description = "Usage chart",
                resetStride = 4,
            )

        val monthRules = monthSpec.marks.filterIsInstance<XRuleMark>()
        val yearRules = yearSpec.marks.filterIsInstance<XRuleMark>()
        assertEquals(52, monthRules.size)
        assertTrue(monthRules.all { it.color == 0 && it.pattern == LinePattern.Dashed })
        assertTrue(yearRules.size in 12..14, "Expected thinned reset rules, got ${yearRules.size}")
        assertEquals(monthRules.last().x, yearRules.last().x)
    }

    @Test
    fun `buildUsageHistoryChartSpec merges timestamps and adds pace projection band and rules`() {
        val weekly = weeklyWindow(id = "seven_day", label = "Weekly", usedPercent = 71.0)
        val monthly = monthlyWindow(id = "monthly", label = "Monthly", usedPercent = 30.0)
        val prevReset = weeklyStart
        val tPast1 = prevReset.minus(2.days)
        val tPastPeak = prevReset.minus(1.minutes)
        val tWeeklyMid = weeklyStart.plus(1.days)
        val tMonthlyMid = weeklyStart.plus(2.days)
        val weeklyPoints =
            listOf(
                UsagePoint(tPast1, 40.0, resetsAt = prevReset),
                UsagePoint(tPastPeak, 90.0, resetsAt = prevReset),
                UsagePoint(prevReset, 0.0, resetsAt = weeklyReset),
                UsagePoint(tWeeklyMid, 35.0, resetsAt = weeklyReset),
                UsagePoint(now, 71.0, resetsAt = weeklyReset),
            )
        val monthlyPoints =
            listOf(
                UsagePoint(tMonthlyMid, 18.0, resetsAt = monthly.resetsAt),
                UsagePoint(now, 30.0, resetsAt = monthly.resetsAt),
            )

        val model =
            assertNotNull(
                buildUsageHistoryModel(
                    accountId = "acc-1",
                    windows = listOf(weekly, monthly),
                    primaryWindowId = weekly.id,
                    histories = mapOf(weekly.id to weeklyPoints, monthly.id to monthlyPoints),
                    selectedWindowIds = setOf(weekly.id, monthly.id),
                    now = now,
                )
            )
        val formatter = ResetFormatter(ZoneOffset.UTC, Locale.UK, is24Hour = true)
        val spec =
            buildUsageHistoryChartSpec(
                model = model,
                formatter = formatter,
                limitLabel = "limit",
                percentFormat = { "$it%" },
                description = "Usage chart",
            )

        val axis = spec.x as TimeAxis
        // Merged timestamps are strictly ascending with no duplicates.
        val millis = axis.times.map { it.toEpochMilli() }
        assertEquals(millis.sorted().distinct(), millis)
        assertTrue(tWeeklyMid.toEpochMilliseconds() in millis)
        assertTrue(tMonthlyMid.toEpochMilliseconds() in millis)
        assertTrue(weeklyStart.toEpochMilliseconds() in millis)
        assertTrue(weeklyReset.toEpochMilliseconds() in millis)

        // Usage LineMark has both series with Gaps.Connect, aligned to the merged time axis.
        val lineMarks = spec.marks.filterIsInstance<LineMark>()
        val usageMark = lineMarks.first { it.pattern == LinePattern.Solid }
        assertEquals(Gaps.Connect, usageMark.gaps)
        assertEquals(listOf("Weekly", "Monthly"), usageMark.series.map { it.name })
        val weeklyMidIndex = millis.indexOf(tWeeklyMid.toEpochMilliseconds())
        val monthlyMidIndex = millis.indexOf(tMonthlyMid.toEpochMilliseconds())
        assertEquals(35.0, usageMark.series[0].values[weeklyMidIndex])
        assertTrue(usageMark.series[0].values[monthlyMidIndex].isNaN())
        assertTrue(usageMark.series[1].values[weeklyMidIndex].isNaN())
        assertEquals(18.0, usageMark.series[1].values[monthlyMidIndex])

        // Dashed pace lines and dotted projection lines exist only inside current periods.
        val paceMarks = lineMarks.filter { it.pattern == LinePattern.Dashed }
        assertEquals(2, paceMarks.size)
        val projectionMarks = lineMarks.filter { it.pattern == LinePattern.Dotted }
        assertEquals(2, projectionMarks.size)
        val beforeCurrentPeriodIdx = millis.indexOf(tPast1.toEpochMilliseconds())
        paceMarks.forEach { mark ->
            assertTrue(mark.series.single().values[beforeCurrentPeriodIdx].isNaN())
        }
        projectionMarks.forEach { mark ->
            assertTrue(mark.series.single().values[beforeCurrentPeriodIdx].isNaN())
        }

        // XBandMark shades the primary window's current period; XRuleMark marks the reset; RuleMark
        // marks 100%.
        val band = spec.marks.filterIsInstance<XBandMark>().single()
        assertEquals(weeklyStart.toEpochMilliseconds().toDouble(), band.from)
        assertEquals(weeklyReset.toEpochMilliseconds().toDouble(), band.to)
        val xRules = spec.marks.filterIsInstance<XRuleMark>()
        assertEquals(listOf(prevReset.toEpochMilliseconds().toDouble()), xRules.map { it.x })
        val limitRule = spec.marks.filterIsInstance<RuleMark>().single()
        assertEquals(100.0, limitRule.y)
        assertEquals("limit", limitRule.label)
    }

    @Test
    fun `time axis tick formatter respects locale time zone and 12 or 24 hour setting`() {
        val sampleInstant = Instant.parse("2026-09-27T14:00:00Z")
        val epochMs = sampleInstant.toEpochMilliseconds().toDouble()

        val utc24 = ResetFormatter(ZoneOffset.UTC, Locale.UK, is24Hour = true).chartTickFormat()
        val utc12 = ResetFormatter(ZoneOffset.UTC, Locale.US, is24Hour = false).chartTickFormat()
        val tokyo24 =
            ResetFormatter(ZoneId.of("Asia/Tokyo"), Locale.UK, is24Hour = true).chartTickFormat()

        assertEquals("14:00", utc24.format(epochMs))
        assertTrue(
            utc12.format(epochMs).contains("2") &&
                utc12.format(epochMs).contains("PM", ignoreCase = true)
        )
        assertEquals("23:00", tokyo24.format(epochMs))

        val midnightOct5 = Instant.parse("2026-10-05T00:00:00Z").toEpochMilliseconds().toDouble()
        val ukDay =
            ResetFormatter(ZoneOffset.UTC, Locale.UK, is24Hour = true)
                .chartTickFormat()
                .format(midnightOct5)
        val usDay =
            ResetFormatter(ZoneOffset.UTC, Locale.US, is24Hour = true)
                .chartTickFormat()
                .format(midnightOct5)
        assertTrue(ukDay.contains("Oct") && ukDay.contains("5"))
        assertTrue(usDay.contains("Oct") && usDay.contains("5"))
    }
}
