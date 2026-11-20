package dev.sebastiano.headroom.model

import kotlin.time.Duration
import kotlin.time.Instant

/**
 * True when a quota window is a long reset window (weekly or monthly) that can be plotted on the
 * usage history chart. Excludes session, daily, credit, unlimited and informational windows.
 */
public val QuotaWindow.isChartableHistoryWindow: Boolean
    get() =
        (kind == WindowKind.Weekly || kind == WindowKind.Monthly) &&
            resetsAt != null &&
            length?.let { it > Duration.ZERO } == true &&
            !isUnlimited &&
            !isInformational

/**
 * Finds every reset instant in [points] (ordered oldest first) using [ResetBoundary.isReset], so
 * the chart's reset markers agree with history compaction and ignore relative countdown jitter.
 */
public fun detectResets(
    points: List<UsagePoint>,
    windowLength: Duration? = null,
): List<Instant> {
    if (points.size < 2) return emptyList()
    val resets = mutableListOf<Instant>()
    var periodResetsAt: Instant? = points.first().resetsAt
    var periodPeak: Double = points.first().usedPercent
    var lastAt: Instant = points.first().at
    for (index in 1 until points.size) {
        val point = points[index]
        val previous =
            UsagePoint(
                at = lastAt,
                usedPercent = periodPeak,
                resetsAt = periodResetsAt,
            )
        if (ResetBoundary.isReset(previous, point, windowLength)) {
            resets += point.at
            periodResetsAt = point.resetsAt
            periodPeak = point.usedPercent
        } else {
            if (periodResetsAt == null && point.resetsAt != null) {
                periodResetsAt = point.resetsAt
            }
            if (point.usedPercent > periodPeak) periodPeak = point.usedPercent
        }
        lastAt = point.at
    }
    return resets
}

/**
 * The stored [raw] history of [window] up to [now], oldest first with one point per instant, plus
 * the window's current usage at [dataFrom] (or [now]) when no stored sample has it yet, so the
 * history line always ends at the usage the rest of the screen shows.
 */
public fun prepareHistoryPoints(
    window: QuotaWindow,
    raw: List<UsagePoint>,
    now: Instant,
    dataFrom: Instant?,
): List<UsagePoint> {
    val resetsAt = window.resetsAt ?: return emptyList()
    val length = window.length ?: return emptyList()
    val start = resetsAt.minus(length)
    val sorted = raw.filter { it.at <= now }.sortedBy { it.at }
    val deduped = ArrayList<UsagePoint>(sorted.size + 1)
    for (point in sorted) {
        if (deduped.isNotEmpty() && deduped.last().at == point.at) {
            deduped[deduped.lastIndex] = point
        } else {
            deduped += point
        }
    }
    val sampleAt = dataFrom ?: now
    val hasCurrentPeriodSample =
        deduped.isNotEmpty() &&
            deduped.last().at >= start &&
            (dataFrom == null || deduped.last().at >= dataFrom) &&
            deduped.last().usedPercent == window.usedPercent
    if (
        !hasCurrentPeriodSample &&
            sampleAt in start..resetsAt &&
            (deduped.isEmpty() || deduped.last().at < sampleAt)
    ) {
        deduped += UsagePoint(at = sampleAt, usedPercent = window.usedPercent, resetsAt = resetsAt)
    }
    return deduped
}
