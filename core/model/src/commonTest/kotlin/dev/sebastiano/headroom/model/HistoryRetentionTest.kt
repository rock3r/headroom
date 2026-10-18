package dev.sebastiano.headroom.model

import dev.sebastiano.headroom.model.stats.ResetPeaks
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class HistoryRetentionTest {
    private val now = Instant.parse("2026-10-01T12:00:00Z")
    private val defaultReset = now.plus(3.days)

    private fun point(
        at: Instant,
        usedPercent: Double,
        resetsAt: Instant? = defaultReset,
    ): UsagePoint = UsagePoint(at = at, usedPercent = usedPercent, resetsAt = resetsAt)

    @Test
    fun `points up to seven days old stay at full resolution`() {
        val start = now.minus(7.days)
        val points =
            (0..28).map { step ->
                point(start.plus((step * 15).minutes), usedPercent = step.toDouble())
            } + point(now, usedPercent = 50.0)

        val kept = HistoryRetention.LongTerm.compact(points, now)

        assertEquals(points, kept)
        assertEquals(emptyList(), HistoryRetention.LongTerm.toDelete(points, now))
    }

    @Test
    fun `points between seven and thirty-one days old keep the last sample of each hour bucket`() {
        // 10 days ago, aligned to an hour boundary in UTC.
        val hourStart = Instant.parse("2026-09-21T08:00:00Z")
        val p0 = point(hourStart.minus(1.hours), 5.0)
        val h1a = point(hourStart.plus(5.minutes), 10.0)
        val h1b = point(hourStart.plus(25.minutes), 15.0)
        val h1c = point(hourStart.plus(55.minutes), 20.0)
        val h2a = point(hourStart.plus(65.minutes), 25.0)
        val h2b = point(hourStart.plus(110.minutes), 30.0)
        val points = listOf(p0, h1a, h1b, h1c, h2a, h2b)

        val kept = HistoryRetention.LongTerm.compact(points, now)

        assertEquals(listOf(p0, h1c, h2b), kept)
        assertEquals(listOf(h1a, h1b, h2a), HistoryRetention.LongTerm.toDelete(points, now))
    }

    @Test
    fun `points between thirty-one and ninety-two days old keep one sample per three-hour bucket`() {
        // 45 days ago, aligned to a 3-hour epoch boundary (06:00 UTC).
        val bucketStart = Instant.parse("2026-08-17T06:00:00Z")
        val first = point(bucketStart.minus(3.hours), 5.0)
        val b1a = point(bucketStart.plus(10.minutes), 10.0)
        val b1b = point(bucketStart.plus(1.hours), 20.0)
        val b1c = point(bucketStart.plus(2.hours + 45.minutes), 30.0)
        val b2a = point(bucketStart.plus(3.hours + 15.minutes), 35.0)
        val b2b = point(bucketStart.plus(5.hours + 50.minutes), 40.0)
        val points = listOf(first, b1a, b1b, b1c, b2a, b2b)

        val kept = HistoryRetention.LongTerm.compact(points, now)

        assertEquals(listOf(first, b1c, b2b), kept)
    }

    @Test
    fun `points between ninety-two and 365 days old keep one sample per twelve-hour bucket`() {
        // 150 days ago, aligned to a 12-hour epoch boundary (00:00 UTC).
        val bucketStart = Instant.parse("2026-05-04T00:00:00Z")
        val first = point(bucketStart.minus(12.hours), 5.0)
        val b1a = point(bucketStart.plus(1.hours), 10.0)
        val b1b = point(bucketStart.plus(6.hours), 20.0)
        val b1c = point(bucketStart.plus(11.hours + 30.minutes), 30.0)
        val b2a = point(bucketStart.plus(12.hours + 20.minutes), 40.0)
        val b2b = point(bucketStart.plus(23.hours), 50.0)
        val points = listOf(first, b1a, b1b, b1c, b2a, b2b)

        val kept = HistoryRetention.LongTerm.compact(points, now)

        assertEquals(listOf(first, b1c, b2b), kept)
    }

    @Test
    fun `points older than 365 days are deleted in long-term mode`() {
        val tooOld = point(now.minus(365.days).minus(1.milliseconds), 90.0)
        val exactBoundary = point(now.minus(365.days), 10.0)
        val recent = point(now.minus(1.hours), 20.0)
        val points = listOf(tooOld, exactBoundary, recent)

        val kept = HistoryRetention.LongTerm.compact(points, now)

        assertEquals(listOf(exactBoundary, recent), kept)
        assertEquals(listOf(tooOld), HistoryRetention.LongTerm.toDelete(points, now))
    }

    @Test
    fun `exact tier boundaries belong to the finer tier`() {
        val at7d = point(now.minus(7.days), 10.0)
        val at7dPlus = point(now.minus(7.days) + 10.minutes, 12.0)
        val at31d = point(now.minus(31.days), 20.0)
        val at92d = point(now.minus(92.days), 30.0)
        val at365d = point(now.minus(365.days), 40.0)
        val points = listOf(at365d, at92d, at31d, at7d, at7dPlus)

        val kept = HistoryRetention.LongTerm.compact(points, now)

        assertEquals(points, kept)
    }

    @Test
    fun `short-term mode keeps sixty days at full resolution and deletes older points`() {
        val tooOld = point(now.minus(60.days).minus(1.milliseconds), 80.0)
        val at60d = point(now.minus(60.days), 10.0)
        // Multiple points in the same hour 30 days ago: all stay in short-term mode.
        val day30a = point(now.minus(30.days), 20.0)
        val day30b = point(now.minus(30.days).plus(15.minutes), 25.0)
        val day30c = point(now.minus(30.days).plus(30.minutes), 30.0)
        val points = listOf(tooOld, at60d, day30a, day30b, day30c)

        val kept = HistoryRetention.ShortTerm.compact(points, now)

        assertEquals(listOf(at60d, day30a, day30b, day30c), kept)
        assertEquals(listOf(tooOld), HistoryRetention.ShortTerm.toDelete(points, now))
    }

    @Test
    fun `a bucket never straddles a reset when resetsAt changes`() {
        // Two periods inside the same 1-hour bucket 14 days ago, with a small drop (3% -> 0%)
        // that only resetsAt reveals as a reset.
        val hourStart = Instant.parse("2026-09-17T10:00:00Z")
        val reset1 = hourStart.plus(30.minutes)
        val reset2 = reset1.plus(7.days)
        val p1First = point(hourStart.plus(5.minutes), 1.0, resetsAt = reset1)
        val p1Mid = point(hourStart.plus(15.minutes), 2.0, resetsAt = reset1)
        val p1Last = point(hourStart.plus(25.minutes), 3.0, resetsAt = reset1)
        val p2First = point(hourStart.plus(35.minutes), 0.0, resetsAt = reset2)
        val p2Mid = point(hourStart.plus(45.minutes), 4.0, resetsAt = reset2)
        val p2Last = point(hourStart.plus(55.minutes), 8.0, resetsAt = reset2)
        val points = listOf(p1First, p1Mid, p1Last, p2First, p2Mid, p2Last)

        val kept = HistoryRetention.LongTerm.compact(points, now)

        // Period 1 keeps its first (p1First) and last/peak (p1Last).
        // Period 2 keeps its first (p2First) and last/peak (p2Last).
        assertEquals(listOf(p1First, p1Last, p2First, p2Last), kept)
    }

    @Test
    fun `legacy points without resetsAt split periods on a drop of at least MIN_DROP_POINTS`() {
        // Inside the same 3-hour bucket 45 days ago.
        val bucketStart = Instant.parse("2026-08-17T06:00:00Z")
        val p1First = point(bucketStart.plus(10.minutes), 20.0, resetsAt = null)
        // A small wobble (< MIN_DROP_POINTS) is not a reset.
        val p1Wobble = point(bucketStart.plus(30.minutes), 17.0, resetsAt = null)
        val p1Peak = point(bucketStart.plus(50.minutes), 85.0, resetsAt = null)
        // A drop of at least MIN_DROP_POINTS starts a new period.
        val p2First = point(bucketStart.plus(70.minutes), 2.0, resetsAt = null)
        val p2Mid = point(bucketStart.plus(90.minutes), 15.0, resetsAt = null)
        val p2Last = point(bucketStart.plus(110.minutes), 30.0, resetsAt = null)
        val points = listOf(p1First, p1Wobble, p1Peak, p2First, p2Mid, p2Last)

        val kept = HistoryRetention.LongTerm.compact(points, now)

        assertEquals(listOf(p1First, p1Peak, p2First, p2Last), kept)
        assertEquals(listOf(85.0), ResetPeaks.usedAtResets(kept))
    }

    @Test
    fun `each period keeps its first sample and its peak sample even mid-bucket`() {
        // Inside a 12-hour bucket 180 days ago: peak is reached mid-bucket before a small wobble,
        // and the period continues into the next bucket at a lower value.
        val bucketStart = Instant.parse("2026-04-04T00:00:00Z")
        val first = point(bucketStart.plus(1.hours), 10.0)
        val earlyTiePeak = point(bucketStart.plus(3.hours), 92.0)
        val lateTiePeak = point(bucketStart.plus(5.hours), 92.0)
        val bucketLast = point(bucketStart.plus(11.hours), 90.0)
        val nextReset = point(bucketStart.plus(13.hours), 0.0, resetsAt = defaultReset.plus(7.days))
        val points = listOf(first, earlyTiePeak, lateTiePeak, bucketLast, nextReset)

        val kept = HistoryRetention.LongTerm.compact(points, now)

        // Keeps first, the first tied peak (earlyTiePeak), and the bucket's last sample.
        assertEquals(listOf(first, earlyTiePeak, bucketLast, nextReset), kept)
        assertEquals(ResetPeaks.resets(points), ResetPeaks.resets(kept))
    }

    @Test
    fun `a multi-day plateau peak older than seven days keeps the time the peak was first reached`() {
        // 20 days ago (1-hour buckets): usage climbs to 94% on day 1 and plateaus for two more
        // days before the weekly reset. The period's last sample is also on the plateau.
        val periodStart = Instant.parse("2026-09-08T09:00:00Z")
        val resetAt = periodStart.plus(7.days)
        val first = point(periodStart, 15.0, resetsAt = resetAt)
        val midClimb = point(periodStart.plus(20.minutes), 55.0, resetsAt = resetAt)
        // Reaches 94% early in an hour bucket that also has a later 94% sample.
        val plateauFirst = point(periodStart.plus(1.days + 10.minutes), 94.0, resetsAt = resetAt)
        val plateauSameHour = point(periodStart.plus(1.days + 45.minutes), 94.0, resetsAt = resetAt)
        val plateauDay2 = point(periodStart.plus(2.days + 3.hours), 94.0, resetsAt = resetAt)
        val periodLast = point(periodStart.plus(3.days + 6.hours), 94.0, resetsAt = resetAt)
        val nextPeriodFirst =
            point(periodStart.plus(7.days + 5.minutes), 0.0, resetsAt = resetAt.plus(7.days))
        val points =
            listOf(
                first,
                midClimb,
                plateauFirst,
                plateauSameHour,
                plateauDay2,
                periodLast,
                nextPeriodFirst,
            )

        val kept = HistoryRetention.LongTerm.compact(points, now)

        // Both the first time the plateau was reached and the period's last sample stay.
        assertTrue(plateauFirst in kept)
        assertTrue(periodLast in kept)
        assertEquals(ResetPeaks.resets(points), ResetPeaks.resets(kept))
        assertEquals(listOf(plateauFirst), ResetPeaks.resets(kept))
    }

    @Test
    fun `an early redeem mid-period with unchanged resetsAt splits periods and keeps both peaks`() {
        // Inside the same 3-hour bucket 45 days ago: the user hits 96%, redeems a reset credit
        // while resetsAt stays the same, and climbs to 64% before the scheduled weekly reset.
        val bucketStart = Instant.parse("2026-08-17T06:00:00Z")
        val sameReset = bucketStart.plus(5.days)
        val nextReset = sameReset.plus(7.days)
        val beforeFirst = point(bucketStart.plus(10.minutes), 20.0, resetsAt = sameReset)
        val beforePeak = point(bucketStart.plus(40.minutes), 96.0, resetsAt = sameReset)
        val afterRedeemFirst = point(bucketStart.plus(60.minutes), 0.0, resetsAt = sameReset)
        val afterRedeemMid = point(bucketStart.plus(100.minutes), 35.0, resetsAt = sameReset)
        val afterRedeemPeak = point(bucketStart.plus(150.minutes), 64.0, resetsAt = sameReset)
        val nextWeekFirst = point(bucketStart.plus(4.hours), 0.0, resetsAt = nextReset)
        val points =
            listOf(
                beforeFirst,
                beforePeak,
                afterRedeemFirst,
                afterRedeemMid,
                afterRedeemPeak,
                nextWeekFirst,
            )

        val kept = HistoryRetention.LongTerm.compact(points, now)
        val twice = HistoryRetention.LongTerm.compact(kept, now)

        assertEquals(
            listOf(beforeFirst, beforePeak, afterRedeemFirst, afterRedeemPeak, nextWeekFirst),
            kept,
        )
        assertEquals(kept, twice)
        assertEquals(listOf(96.0, 64.0), ResetPeaks.usedAtResets(kept))
    }

    @Test
    fun `compaction is idempotent across all tiers and period boundaries`() {
        val start = now.minus(380.days)
        val points = mutableListOf<UsagePoint>()
        var cursor = start
        var cycle = 0
        var redeemedInCycle = false
        var used = 0.0
        while (cursor <= now) {
            val age = now - cursor
            // Mix legacy points (null resetsAt) in the first half and points with resetsAt in the
            // second half, including mid-cycle redeems where resetsAt does not advance.
            val resetsAt = if (age > 180.days) null else start.plus(((cycle + 1) * 7).days)
            points += UsagePoint(at = cursor, usedPercent = used, resetsAt = resetsAt)
            cursor += 30.minutes
            used += 1.5
            if (cycle % 5 == 2 && !redeemedInCycle && used >= 80.0) {
                used = 0.0
                redeemedInCycle = true
            } else if (used >= 95.0) {
                used = 0.0
                cycle++
                redeemedInCycle = false
            }
        }

        val once = HistoryRetention.LongTerm.compact(points, now)
        val twice = HistoryRetention.LongTerm.compact(once, now)

        assertEquals(once, twice)
        assertTrue(HistoryRetention.LongTerm.toDelete(once, now).isEmpty())
        assertEquals(
            ResetPeaks.resets(points.filter { it.at >= now.minus(365.days) }),
            ResetPeaks.resets(once),
        )
    }

    @Test
    fun `jittering relative resetsAt across a year compacts like stable resetsAt and stays idempotent`() {
        val start = now.minus(365.days)
        val stablePoints = mutableListOf<UsagePoint>()
        val jitteredPoints = mutableListOf<UsagePoint>()
        val jitterOffsetsSeconds = listOf(-4L, 3L, -1L, 5L, -3L, 2L, 0L, -5L, 4L)
        var cursor = start
        var step = 0
        while (cursor <= now) {
            val week = (cursor - start).inWholeDays / 7
            val inWeekHours = ((cursor - start) - (week * 7).days).inWholeMinutes / 60.0
            val used = (inWeekHours / (7 * 24) * 88.0).coerceAtMost(88.0)
            val stableReset = start.plus(((week + 1) * 7).days)
            val jitter = jitterOffsetsSeconds[step % jitterOffsetsSeconds.size].seconds
            stablePoints += UsagePoint(at = cursor, usedPercent = used, resetsAt = stableReset)
            jitteredPoints +=
                UsagePoint(at = cursor, usedPercent = used, resetsAt = stableReset + jitter)
            cursor += 15.minutes
            step++
        }

        val stableKept = HistoryRetention.LongTerm.compact(stablePoints, now)
        val jitteredKept = HistoryRetention.LongTerm.compact(jitteredPoints, now)
        val jitteredTwice = HistoryRetention.LongTerm.compact(jitteredKept, now)

        assertEquals(
            stableKept.map { it.at to it.usedPercent },
            jitteredKept.map { it.at to it.usedPercent },
        )
        assertEquals(jitteredKept, jitteredTwice)
    }

    @Test
    fun `ResetBoundary ignores forward and backward jitter and detects real advances or drops`() {
        val baseReset = now.plus(4.days)
        val previous = point(now.minus(15.minutes), usedPercent = 42.0, resetsAt = baseReset)

        // Small forward jitter, exact match, and backward shift are not resets.
        assertFalse(
            ResetBoundary.isReset(
                previous,
                point(now, usedPercent = 43.0, resetsAt = baseReset.plus(5.seconds)),
            )
        )
        assertFalse(
            ResetBoundary.isReset(
                previous,
                point(now, usedPercent = 43.0, resetsAt = baseReset.plus(1.hours)),
            )
        )
        assertFalse(
            ResetBoundary.isReset(
                previous,
                point(now, usedPercent = 43.0, resetsAt = baseReset.minus(2.hours)),
            )
        )

        // Advancing resetsAt beyond 1 hour (or 1/24 of windowLength when longer) is a reset.
        assertTrue(
            ResetBoundary.isReset(
                previous,
                point(now, usedPercent = 43.0, resetsAt = baseReset.plus(1.hours + 1.seconds)),
            )
        )
        assertFalse(
            ResetBoundary.isReset(
                previous,
                point(now, usedPercent = 43.0, resetsAt = baseReset.plus(6.hours)),
                windowLength = 7.days,
            )
        )
        assertTrue(
            ResetBoundary.isReset(
                previous,
                point(now, usedPercent = 43.0, resetsAt = baseReset.plus(7.days)),
                windowLength = 7.days,
            )
        )

        // A drop of at least MIN_DROP_POINTS is a reset even without a resetsAt advance.
        assertTrue(
            ResetBoundary.isReset(
                previous,
                point(now, usedPercent = 37.0, resetsAt = baseReset.minus(3.seconds)),
            )
        )
    }
}
