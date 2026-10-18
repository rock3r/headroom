package dev.sebastiano.headroom.model.stats

import dev.sebastiano.headroom.model.UsagePoint
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Longer than this between two syncs, the history cannot say when the use happened, so the hourly
 * stats leave it out. The totals still count it.
 */
private val MAX_GAP: Duration = MAX_GAP_HOURS.hours

private const val MAX_GAP_HOURS = 12L

/**
 * Percentage points of the limit used between two consecutive points. After a reset, all of the new
 * usage counts, because it started again from zero. A small drop is noise and burns nothing.
 */
public fun burned(from: UsagePoint, to: UsagePoint): Double {
    val change = to.usedPercent - from.usedPercent
    return when {
        change >= 0.0 -> change
        -change >= ResetPeaks.MIN_DROP_POINTS -> to.usedPercent
        else -> 0.0
    }
}

/** Percentage points used over the whole history, oldest point first. */
public fun totalBurned(points: List<UsagePoint>): Double = points.zipWithNext(::burned).sum()

/**
 * Percentage points used in each local hour, keyed by the start of the hour. The use between two
 * syncs is spread evenly over the time between them. Gaps longer than [MAX_GAP] are left out.
 */
public fun hourlyBurn(points: List<UsagePoint>, zone: TimeZone): Map<LocalDateTime, Double> {
    val hours = mutableMapOf<LocalDateTime, Double>()
    points.zipWithNext { from, to ->
        val amount = burned(from, to)
        val gap = to.at - from.at
        if (amount > 0.0 && !gap.isNegative() && gap <= MAX_GAP) {
            spread(from, to, amount, zone) { hour, share ->
                hours[hour] = (hours[hour] ?: 0.0) + share
            }
        }
    }
    return hours
}

/** Hands [amount] out over the local hours between [from] and [to], in proportion to the time. */
private fun spread(
    from: UsagePoint,
    to: UsagePoint,
    amount: Double,
    zone: TimeZone,
    add: (LocalDateTime, Double) -> Unit,
) {
    val gap = to.at - from.at
    if (gap == Duration.ZERO) {
        add(hourStart(to.at, zone), amount)
        return
    }
    var cursor = from.at
    while (cursor < to.at) {
        val next = minOf(nextHourStart(cursor, zone), to.at)
        add(
            hourStart(cursor, zone),
            amount * (next - cursor).inWholeMilliseconds / gap.inWholeMilliseconds,
        )
        cursor = next
    }
}

/**
 * When the local hour after the one [at] falls in starts, an hour on from the start of [at]'s hour.
 * Worked out from [at] itself rather than from its local time, which in the hour repeated when
 * clocks go back could be either occurrence.
 */
private fun nextHourStart(at: Instant, zone: TimeZone): Instant {
    val local = at.toLocalDateTime(zone)
    val intoHour = local.minute.minutes + local.second.seconds + local.nanosecond.nanoseconds
    return at - intoHour + 1.hours
}

/** The local start of the hour [at] falls in. */
private fun hourStart(at: Instant, zone: TimeZone): LocalDateTime {
    val local = at.toLocalDateTime(zone)
    return LocalDateTime(local.date, LocalTime(local.hour, 0))
}
