package dev.sebastiano.headroom.model

import kotlin.math.sin
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** The real history keeps this long, so the demo does too. */
private val DEMO_RETENTION: Duration = 60.days

/** A limit hit in the demo: the demand was this much more than the limit, so usage stops at 100. */
private const val HIT_DEMAND = 1.15

/**
 * Plausible usage history for the demo accounts, for the Stats tab and the charts: hourly points
 * with most of the use on weekday working hours.
 */
public object DemoUsage {
    /**
     * Hourly usage of [window] for the past cycles, which peaked at [peaks] (oldest first), and for
     * the current cycle up to [now], where it ends at the window's usage, in [zone]. Each past
     * cycle ends with a point at its peak, a minute before it resets. Only the last
     * [DEMO_RETENTION] is kept.
     */
    public fun points(
        window: QuotaWindow,
        peaks: List<Double>,
        now: Instant,
        zone: TimeZone,
    ): List<UsagePoint> {
        val resetsAt = window.resetsAt ?: return emptyList()
        val length = window.length ?: return emptyList()
        val currentStart = resetsAt.minus(length)
        val points = mutableListOf<UsagePoint>()
        peaks.forEachIndexed { index, peak ->
            val start = currentStart - length * (peaks.size - index)
            val end = start.plus(length)
            val demand = if (peak >= MAX_PERCENT) MAX_PERCENT * HIT_DEMAND else peak
            points += cycle(start, end, zone) { share -> minOf(MAX_PERCENT, demand * share) }
            points += UsagePoint(end - SECONDS_PER_MINUTE.seconds, peak)
        }
        if (now > currentStart) {
            points += cycle(currentStart, now, zone) { share -> window.usedPercent * share }
            points += UsagePoint(now, window.usedPercent)
        }
        val cutoff = now.minus(DEMO_RETENTION)
        return points.filter { it.at >= cutoff }
    }
}

/**
 * One point per hour from [start] to before [end]. [usage] turns the share of the cycle's activity
 * that happened so far, from 0 to 1, into the usage at that point.
 */
private fun cycle(
    start: Instant,
    end: Instant,
    zone: TimeZone,
    usage: (Double) -> Double,
): List<UsagePoint> {
    val hours = (end - start).inWholeHours
    if (hours <= 0) return emptyList()
    val weights = (0 until hours).map { activity(start.plus(it.hours), zone) }
    val total = weights.sum()
    var sofar = 0.0
    return (0 until hours).map { hour ->
        val point = UsagePoint(start.plus(hour.hours), usage(sofar / total))
        sofar += weights[hour.toInt()]
        point
    }
}

/** How busy the hour starting at [at] is: mostly weekday working hours, a little in the evening. */
private fun activity(at: Instant, zone: TimeZone): Double {
    val local = at.toLocalDateTime(zone)
    val weekend = local.dayOfWeek == DayOfWeek.SATURDAY || local.dayOfWeek == DayOfWeek.SUNDAY
    val base =
        when (local.hour) {
            in WORK_MORNING -> if (weekend) WEEKEND_DAY else MORNING
            in WORK_AFTERNOON -> if (weekend) WEEKEND_DAY else AFTERNOON
            in EVENING -> if (weekend) WEEKEND_DAY else EVENING_WEIGHT
            else -> NIGHT
        }
    // Some days are busier than others; the sine keeps it the same on every run.
    val day = local.date.toEpochDays().toDouble()
    return base * (1.0 + DAY_WOBBLE * sin(day * DAY_WOBBLE_FREQUENCY))
}

private const val SECONDS_PER_MINUTE = 60L
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
