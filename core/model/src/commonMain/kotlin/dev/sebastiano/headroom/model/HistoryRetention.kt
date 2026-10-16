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
        return points.filterIndexed { index, _ -> kept[index] }
    }

    /** The points of one window (oldest first) that [compact] drops at [now]. */
    public fun toDelete(points: List<UsagePoint>, now: Instant): List<UsagePoint> {
        if (points.isEmpty()) return emptyList()
        val kept = keptMask(points, now)
        return points.filterIndexed { index, _ -> !kept[index] }
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
            LongTerm -> {
                val withinMaxAge = points.indices.filter { points[it].at >= cutoff }
                for (period in splitPeriods(points, withinMaxAge)) {
                    markPeriodKept(points, period, now, kept)
                }
            }
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

private val ONE_HOUR_TIER_MAX: Duration = ONE_HOUR_TIER_DAYS.days
private val THREE_HOUR_TIER_MAX: Duration = THREE_HOUR_TIER_DAYS.days

private val ONE_HOUR_BUCKET_MS: Long = ONE_HOUR_BUCKET_HOURS.hours.inWholeMilliseconds
private val THREE_HOUR_BUCKET_MS: Long = THREE_HOUR_BUCKET_HOURS.hours.inWholeMilliseconds
private val TWELVE_HOUR_BUCKET_MS: Long = TWELVE_HOUR_BUCKET_HOURS.hours.inWholeMilliseconds

private data class Bucket(val spanMs: Long, val index: Long)

/**
 * Splits [indices] into reset periods so a bucket never straddles a reset. A new period starts
 * where [UsagePoint.resetsAt] changes, or, when [UsagePoint.resetsAt] is missing, where usage drops
 * by at least [ResetPeaks.MIN_DROP_POINTS].
 */
private fun splitPeriods(points: List<UsagePoint>, indices: List<Int>): List<List<Int>> {
    if (indices.isEmpty()) return emptyList()
    val periods = mutableListOf<List<Int>>()
    val current = mutableListOf<Int>()
    var periodResetsAt: Instant? = null
    var periodPeak = Double.NEGATIVE_INFINITY
    for (index in indices) {
        val point = points[index]
        val isBoundary =
            current.isNotEmpty() &&
                if (point.resetsAt != null && periodResetsAt != null) {
                    point.resetsAt != periodResetsAt
                } else {
                    periodPeak - point.usedPercent >= ResetPeaks.MIN_DROP_POINTS
                }
        if (isBoundary) {
            periods += current.toList()
            current.clear()
            periodResetsAt = null
            periodPeak = Double.NEGATIVE_INFINITY
        }
        current += index
        if (point.resetsAt != null) periodResetsAt = point.resetsAt
        if (point.usedPercent > periodPeak) periodPeak = point.usedPercent
    }
    if (current.isNotEmpty()) periods += current.toList()
    return periods
}

/**
 * Marks which points of [period] to keep: its first sample, its peak sample (latest on a tie),
 * every sample up to [HistoryRetention.FULL_RESOLUTION_SPAN] old, and the last sample of each
 * epoch-aligned bucket for older samples.
 */
private fun markPeriodKept(
    points: List<UsagePoint>,
    period: List<Int>,
    now: Instant,
    kept: BooleanArray,
) {
    kept[period.first()] = true
    var peakIndex = period.first()
    val lastByBucket = mutableMapOf<Bucket, Int>()
    for (index in period) {
        val point = points[index]
        if (point.usedPercent >= points[peakIndex].usedPercent) {
            peakIndex = index
        }
        val bucketMs = bucketSpanMs(now - point.at)
        if (bucketMs == null) {
            kept[index] = true
        } else {
            val epochMs = point.at.toEpochMilliseconds()
            lastByBucket[Bucket(bucketMs, epochMs.floorDiv(bucketMs))] = index
        }
    }
    kept[peakIndex] = true
    for (index in lastByBucket.values) {
        kept[index] = true
    }
}

private fun bucketSpanMs(age: Duration): Long? =
    when {
        age <= HistoryRetention.FULL_RESOLUTION_SPAN -> null
        age <= ONE_HOUR_TIER_MAX -> ONE_HOUR_BUCKET_MS
        age <= THREE_HOUR_TIER_MAX -> THREE_HOUR_BUCKET_MS
        else -> TWELVE_HOUR_BUCKET_MS
    }
