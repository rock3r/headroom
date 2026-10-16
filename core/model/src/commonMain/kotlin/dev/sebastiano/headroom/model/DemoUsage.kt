package dev.sebastiano.headroom.model

import kotlin.math.sin
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.toLocalDateTime

/** A limit hit in the demo: the demand was this much more than the limit, so usage stops at 100. */
private const val HIT_DEMAND = 1.15

/**
 * Plausible usage history for the demo accounts, for the Stats tab and the charts: hourly points
 * with most of the use on weekday working hours, thinned out by [HistoryRetention] like real synced
 * history.
 */
public object DemoUsage {
    /**
     * Usage of [window] for past cycles that repeat [peaks] (oldest first, ending with [peaks]
     * right before the current cycle) back to [retention]'s horizon, and for the current cycle up
     * to [now], where it ends at the window's usage, in [zone]. Each past cycle ends with a point
     * at its peak, a minute before it resets. The result is compacted with [retention].
     */
    public fun points(
        window: QuotaWindow,
        peaks: List<Double>,
        now: Instant,
        zone: TimeZone,
        retention: HistoryRetention = HistoryRetention.LongTerm,
    ): List<UsagePoint> {
        val resetsAt = window.resetsAt ?: return emptyList()
        val length = window.length ?: return emptyList()
        if (length <= Duration.ZERO) return emptyList()
        val currentStart = resetsAt.minus(length)
        val cutoff = now.minus(retention.maxAge)
        val pastCycles = pastCycleCount(currentStart, cutoff, length, peaks.isNotEmpty())
        val estimatedHours =
            ((now - cutoff).inWholeHours.toInt() + 1).coerceAtLeast(MIN_POINT_CAPACITY)
        val points = ArrayList<UsagePoint>(estimatedHours)
        for (stepsBack in pastCycles downTo 1) {
            val peak = peaks[(peaks.size - 1 - (stepsBack - 1)).mod(peaks.size)]
            val start = currentStart - length * stepsBack
            val end = start.plus(length)
            val demand = if (peak >= MAX_PERCENT) MAX_PERCENT * HIT_DEMAND else peak
            appendCycle(start, end, end, zone, points) { share ->
                minOf(MAX_PERCENT, demand * share)
            }
            points += UsagePoint(end - SECONDS_PER_MINUTE.seconds, peak, resetsAt = end)
        }
        if (now > currentStart) {
            appendCycle(currentStart, now, resetsAt, zone, points) { share ->
                window.usedPercent * share
            }
            points += UsagePoint(now, window.usedPercent, resetsAt = resetsAt)
        }
        return retention.compact(points, now)
    }
}

private fun pastCycleCount(
    currentStart: Instant,
    cutoff: Instant,
    length: Duration,
    hasPeaks: Boolean,
): Int {
    if (!hasPeaks) return 0
    val spanMs = (currentStart - SECONDS_PER_MINUTE.seconds - cutoff).inWholeMilliseconds
    if (spanMs < 0) return 0
    return (spanMs / length.inWholeMilliseconds).toInt() + 1
}

/**
 * Appends one point per hour from [start] to before [end] into [out]. [usage] turns the share of
 * the cycle's activity that happened so far, from 0 to 1, into the usage at that point.
 */
private inline fun appendCycle(
    start: Instant,
    end: Instant,
    resetsAt: Instant,
    zone: TimeZone,
    out: MutableList<UsagePoint>,
    usage: (Double) -> Double,
) {
    val hours = (end - start).inWholeHours.toInt()
    if (hours <= 0) return
    val weights = DoubleArray(hours)
    var total = 0.0
    val firstLocal = start.toLocalDateTime(zone)
    var epochDay = firstLocal.date.toEpochDays()
    var dayOfWeek = firstLocal.dayOfWeek
    var localHour = firstLocal.hour
    var dayFactor = 1.0 + DAY_WOBBLE * sin(epochDay.toDouble() * DAY_WOBBLE_FREQUENCY)
    for (hour in 0 until hours) {
        val weight = baseActivity(dayOfWeek, localHour) * dayFactor
        weights[hour] = weight
        total += weight
        localHour++
        if (localHour == HOURS_PER_DAY) {
            localHour = 0
            epochDay++
            dayOfWeek = DayOfWeek(dayOfWeek.isoDayNumber % DAYS_PER_WEEK + 1)
            dayFactor = 1.0 + DAY_WOBBLE * sin(epochDay.toDouble() * DAY_WOBBLE_FREQUENCY)
        }
    }
    val startEpochSeconds = start.epochSeconds
    val startNanoseconds = start.nanosecondsOfSecond
    var sofar = 0.0
    for (hour in 0 until hours) {
        val at =
            Instant.fromEpochSeconds(
                startEpochSeconds + hour * SECONDS_PER_HOUR,
                startNanoseconds,
            )
        out += UsagePoint(at, usage(sofar / total), resetsAt = resetsAt)
        sofar += weights[hour]
    }
}

/** How busy [hour] on [dayOfWeek] is: mostly weekday working hours, a little in the evening. */
private fun baseActivity(dayOfWeek: DayOfWeek, hour: Int): Double {
    val weekend = dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY
    return when (hour) {
        in WORK_MORNING -> if (weekend) WEEKEND_DAY else MORNING
        in WORK_AFTERNOON -> if (weekend) WEEKEND_DAY else AFTERNOON
        in EVENING -> if (weekend) WEEKEND_DAY else EVENING_WEIGHT
        else -> NIGHT
    }
}

private const val MIN_POINT_CAPACITY = 16
private const val HOURS_PER_DAY = 24
private const val DAYS_PER_WEEK = 7
private const val SECONDS_PER_MINUTE = 60L
private const val SECONDS_PER_HOUR = 3_600L
private val WORK_MORNING = 9..12
private val WORK_AFTERNOON = 13..18
private val EVENING = 19..22
private const val MORNING = 1.0
private const val AFTERNOON = 1.4
private const val EVENING_WEIGHT = 0.35
private const val WEEKEND_DAY = 0.25
private const val NIGHT = 0.0
private const val DAY_WOBBLE = 0.4
private const val DAY_WOBBLE_FREQUENCY = 2.3
