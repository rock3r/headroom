package dev.sebastiano.headroom.ui.stats

import dev.sebastiano.headroom.appdata.ResetPeaks
import dev.sebastiano.headroom.model.UsagePoint
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Longer than this between two syncs, the history cannot say when the use happened, so the hourly
 * stats leave it out. The totals still count it.
 */
private val MAX_GAP: Duration = Duration.ofHours(MAX_GAP_HOURS)

private const val MAX_GAP_HOURS = 12L

/**
 * Percentage points of the limit used between two consecutive points. After a reset, all of the new
 * usage counts, because it started again from zero. A small drop is noise and burns nothing.
 */
internal fun burned(from: UsagePoint, to: UsagePoint): Double {
    val change = to.usedPercent - from.usedPercent
    return when {
        change >= 0.0 -> change
        -change >= ResetPeaks.MIN_DROP_POINTS -> to.usedPercent
        else -> 0.0
    }
}

/** Percentage points used over the whole history, oldest point first. */
internal fun totalBurned(points: List<UsagePoint>): Double = points.zipWithNext(::burned).sum()

/**
 * Percentage points used in each local hour, keyed by the start of the hour. The use between two
 * syncs is spread evenly over the time between them. Gaps longer than [MAX_GAP] are left out.
 */
internal fun hourlyBurn(points: List<UsagePoint>, zone: ZoneId): Map<LocalDateTime, Double> {
    val hours = mutableMapOf<LocalDateTime, Double>()
    points.zipWithNext { from, to ->
        val amount = burned(from, to)
        val gap = Duration.between(from.at, to.at)
        if (amount > 0.0 && !gap.isNegative && gap <= MAX_GAP) {
            spread(from, to, amount, zone) { hour, share -> hours.merge(hour, share, Double::plus) }
        }
    }
    return hours
}

/** Hands [amount] out over the local hours between [from] and [to], in proportion to the time. */
private fun spread(
    from: UsagePoint,
    to: UsagePoint,
    amount: Double,
    zone: ZoneId,
    add: (LocalDateTime, Double) -> Unit,
) {
    val gap = Duration.between(from.at, to.at)
    if (gap.isZero) {
        add(to.at.atZone(zone).truncatedTo(ChronoUnit.HOURS).toLocalDateTime(), amount)
        return
    }
    var cursor = from.at
    while (cursor < to.at) {
        val hour = cursor.atZone(zone).truncatedTo(ChronoUnit.HOURS)
        val next = minOf(hour.plusHours(1).toInstant(), to.at)
        add(
            hour.toLocalDateTime(),
            amount * Duration.between(cursor, next).toMillis() / gap.toMillis(),
        )
        cursor = next
    }
}
