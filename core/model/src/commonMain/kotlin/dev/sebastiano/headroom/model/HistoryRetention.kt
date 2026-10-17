package dev.sebastiano.headroom.model

import dev.sebastiano.headroom.model.stats.ResetPeaks
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * How long usage history is kept, and how older points are thinned out so a year of syncs stays
 * small on disk.
 */
public enum class HistoryRetention(
    /** The oldest point kept at any resolution. Anything older is deleted. */
    public val maxAge: Duration
) {
    /**
     * A year of history: every point for [FULL_RESOLUTION_SPAN], then one per hour up to 31 days,
     * one per three hours up to 92 days, and one per twelve hours up to 365 days.
     */
    LongTerm(LONG_TERM_DAYS.days),

    /** Sixty days at full resolution, and nothing older. */
    ShortTerm(SHORT_TERM_DAYS.days);

    /**
     * The points of one window (oldest first) to keep at [now]. Running it again at the same [now]
     * changes nothing.
     */
    public fun compact(points: List<UsagePoint>, now: Instant): List<UsagePoint> {
        if (points.isEmpty()) return emptyList()
        val kept = keptMask(points, now)
        val result = ArrayList<UsagePoint>(kept.count { it })
        for (index in points.indices) {
            if (kept[index]) result += points[index]
        }
        return result
    }

    /** The points of one window (oldest first) that [compact] drops at [now]. */
    public fun toDelete(points: List<UsagePoint>, now: Instant): List<UsagePoint> {
        if (points.isEmpty()) return emptyList()
        val kept = keptMask(points, now)
        val result = ArrayList<UsagePoint>(kept.count { !it })
        for (index in points.indices) {
            if (!kept[index]) result += points[index]
        }
        return result
    }

    private fun keptMask(points: List<UsagePoint>, now: Instant): BooleanArray {
        val kept = BooleanArray(points.size)
        val cutoff = now.minus(maxAge)
        when (this) {
            ShortTerm -> {
                for (index in points.indices) {
                    if (points[index].at >= cutoff) kept[index] = true
                }
            }
            LongTerm -> markLongTermKept(points, cutoff, now, kept)
        }
        return kept
    }

    public companion object {
        /** Points up to this age stay at full resolution in [LongTerm] mode. */
        public val FULL_RESOLUTION_SPAN: Duration = FULL_RESOLUTION_DAYS.days

        /** The retention mode for the user's [keepLongHistory] setting. */
        public fun of(keepLongHistory: Boolean): HistoryRetention =
            if (keepLongHistory) LongTerm else ShortTerm
    }
}

private const val FULL_RESOLUTION_DAYS = 7L
private const val ONE_HOUR_TIER_DAYS = 31L
private const val THREE_HOUR_TIER_DAYS = 92L
private const val LONG_TERM_DAYS = 365L
private const val SHORT_TERM_DAYS = 60L

private const val ONE_HOUR_BUCKET_HOURS = 1L
private const val THREE_HOUR_BUCKET_HOURS = 3L
private const val TWELVE_HOUR_BUCKET_HOURS = 12L

private const val MIN_RESET_ADVANCE_HOURS = 1L
private const val WINDOW_FRACTION_DIVISOR = 24

private val ONE_HOUR_TIER_MAX: Duration = ONE_HOUR_TIER_DAYS.days
private val THREE_HOUR_TIER_MAX: Duration = THREE_HOUR_TIER_DAYS.days

private val ONE_HOUR_BUCKET_MS: Long = ONE_HOUR_BUCKET_HOURS.hours.inWholeMilliseconds
private val THREE_HOUR_BUCKET_MS: Long = THREE_HOUR_BUCKET_HOURS.hours.inWholeMilliseconds
private val TWELVE_HOUR_BUCKET_MS: Long = TWELVE_HOUR_BUCKET_HOURS.hours.inWholeMilliseconds

/** Detects when two usage samples straddle a quota reset, for history compaction and charts. */
public object ResetBoundary {
    /**
     * Minimum forward shift of [UsagePoint.resetsAt] that counts as a new reset period when the
     * window length is unknown, or when 1/24 of the window length is shorter than an hour.
     */
    public val MIN_RESET_ADVANCE: Duration = MIN_RESET_ADVANCE_HOURS.hours

    /**
     * True when [next] belongs to a new reset period after [previous]: either [UsagePoint.resetsAt]
     * advanced by more than the jitter tolerance (`maxOf(1.hours, windowLength / 24)` when
     * [windowLength] is known, or [MIN_RESET_ADVANCE] otherwise), or usage dropped by at least
     * [ResetPeaks.MIN_DROP_POINTS]. Earlier or equal [UsagePoint.resetsAt] values are clock jitter,
     * never a reset.
     */
    public fun isReset(
        previous: UsagePoint,
        next: UsagePoint,
        windowLength: Duration? = null,
    ): Boolean {
        val previousReset = previous.resetsAt
        val nextReset = next.resetsAt
        val tolerance =
            if (windowLength != null && windowLength > Duration.ZERO) {
                maxOf(MIN_RESET_ADVANCE, windowLength / WINDOW_FRACTION_DIVISOR)
            } else {
                MIN_RESET_ADVANCE
            }
        val resetAdvanced =
            previousReset != null && nextReset != null && nextReset - previousReset > tolerance
        val usageDropped = previous.usedPercent - next.usedPercent >= ResetPeaks.MIN_DROP_POINTS
        return resetAdvanced || usageDropped
    }
}

/**
 * Single-pass compaction over [points] (oldest first). Splits into reset periods where
 * [ResetBoundary.isReset] detects a reset from the running period state, and marks each period's
 * first sample, its peak sample (the first sample to reach the peak, matching [ResetPeaks.resets]),
 * every sample up to [HistoryRetention.FULL_RESOLUTION_SPAN] old, and the last sample of each
 * epoch-aligned bucket.
 */
private fun markLongTermKept(
    points: List<UsagePoint>,
    cutoff: Instant,
    now: Instant,
    kept: BooleanArray,
) {
    val state = PeriodState()
    for (index in points.indices) {
        val point = points[index]
        if (point.at < cutoff) continue
        if (state.isBoundary(point)) state.flush(kept)
        state.record(index, point, now, kept)
    }
    state.flush(kept)
}

private class PeriodState {
    private var active = false
    private var periodResetsAt: Instant? = null
    private var periodPeak = Double.NEGATIVE_INFINITY
    private var peakIndex = -1
    private var lastAt: Instant = Instant.DISTANT_PAST
    private var lastBucketMs = -1L
    private var lastBucketIndex = Long.MIN_VALUE
    private var lastBucketPointIndex = -1

    fun isBoundary(point: UsagePoint): Boolean {
        if (!active) return false
        val previous =
            UsagePoint(
                at = lastAt,
                usedPercent = periodPeak,
                resetsAt = periodResetsAt,
            )
        return ResetBoundary.isReset(previous, point)
    }

    fun record(index: Int, point: UsagePoint, now: Instant, kept: BooleanArray) {
        if (!active) {
            active = true
            kept[index] = true
            peakIndex = index
            periodPeak = point.usedPercent
        } else if (point.usedPercent > periodPeak) {
            periodPeak = point.usedPercent
            peakIndex = index
        }
        lastAt = point.at
        if (periodResetsAt == null && point.resetsAt != null) {
            periodResetsAt = point.resetsAt
            kept[index] = true
        }
        val bucketMs = bucketSpanMs(now - point.at)
        if (bucketMs == null) {
            if (lastBucketPointIndex >= 0) {
                kept[lastBucketPointIndex] = true
                lastBucketPointIndex = -1
                lastBucketMs = -1L
            }
            kept[index] = true
        } else {
            val bucketIndex = point.at.toEpochMilliseconds().floorDiv(bucketMs)
            if (
                lastBucketPointIndex >= 0 &&
                    (bucketMs != lastBucketMs || bucketIndex != lastBucketIndex)
            ) {
                kept[lastBucketPointIndex] = true
            }
            lastBucketMs = bucketMs
            lastBucketIndex = bucketIndex
            lastBucketPointIndex = index
        }
    }

    fun flush(kept: BooleanArray) {
        if (!active) return
        if (peakIndex >= 0) kept[peakIndex] = true
        if (lastBucketPointIndex >= 0) kept[lastBucketPointIndex] = true
        active = false
        periodResetsAt = null
        periodPeak = Double.NEGATIVE_INFINITY
        peakIndex = -1
        lastBucketMs = -1L
        lastBucketIndex = Long.MIN_VALUE
        lastBucketPointIndex = -1
    }
}

private fun bucketSpanMs(age: Duration): Long? =
    when {
        age <= HistoryRetention.FULL_RESOLUTION_SPAN -> null
        age <= ONE_HOUR_TIER_MAX -> ONE_HOUR_BUCKET_MS
        age <= THREE_HOUR_TIER_MAX -> THREE_HOUR_BUCKET_MS
        else -> TWELVE_HOUR_BUCKET_MS
    }
