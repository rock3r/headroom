package dev.sebastiano.headroom.appdata

import dev.sebastiano.headroom.model.QuotaRepository
import dev.sebastiano.headroom.model.QuotaWindow
import dev.sebastiano.headroom.model.UsagePoint
import java.time.DayOfWeek
import java.time.ZoneId
import kotlin.math.sin
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.time.toJavaInstant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

/** The recorded usage of an account's window, oldest point first, for the Stats tab. */
fun interface UsageHistory {
    fun points(accountId: String, window: QuotaWindow): Flow<List<UsagePoint>>
}

/** The usage history that the data layer records on every sync. */
class RepositoryUsageHistory(private val repository: QuotaRepository) : UsageHistory {
    override fun points(accountId: String, window: QuotaWindow): Flow<List<UsagePoint>> =
        repository.history(accountId, window.id)
}

/**
 * Plausible history for the demo accounts: one cycle for each of the [resets] of the window, then
 * the current one, with most of the use on weekday working hours in [zone].
 */
class DemoUsageHistory(
    private val resets: ResetHistory,
    private val clock: () -> Instant,
    private val zone: ZoneId,
) : UsageHistory {
    override fun points(accountId: String, window: QuotaWindow): Flow<List<UsagePoint>> =
        resets.usedAtReset(accountId, window.id).map { peaks ->
            demoUsage(window, peaks, clock(), zone)
        }
}

/** Follows demo mode: demo history while the demo accounts show, recorded history otherwise. */
@OptIn(ExperimentalCoroutinesApi::class)
class DemoAwareUsageHistory(
    private val isDemo: StateFlow<Boolean>,
    private val real: UsageHistory,
    private val demo: UsageHistory,
) : UsageHistory {
    override fun points(accountId: String, window: QuotaWindow): Flow<List<UsagePoint>> =
        isDemo.flatMapLatest { demoMode ->
            (if (demoMode) demo else real).points(accountId, window)
        }
}

/** The real history keeps this long, so the demo does too. */
private val DEMO_RETENTION: Duration = 60.days

private const val LIMIT = 100.0

/** A limit hit in the demo: the demand was this much more than the limit, so usage stops at 100. */
private const val HIT_DEMAND = 1.15

/**
 * Hourly usage of [window] for the past cycles, which peaked at [peaks] (oldest first), and for the
 * current cycle up to [now], where it ends at the window's usage. Each past cycle ends with a point
 * at its peak, a minute before it resets. Only the last [DEMO_RETENTION] is kept.
 */
internal fun demoUsage(
    window: QuotaWindow,
    peaks: List<Double>,
    now: Instant,
    zone: ZoneId,
): List<UsagePoint> {
    val resetsAt = window.resetsAt ?: return emptyList()
    val length = window.length ?: return emptyList()
    val currentStart = resetsAt.minus(length)
    val points = mutableListOf<UsagePoint>()
    peaks.forEachIndexed { index, peak ->
        val start = currentStart - length * (peaks.size - index)
        val end = start.plus(length)
        val demand = if (peak >= LIMIT) LIMIT * HIT_DEMAND else peak
        points += cycle(start, end, zone) { share -> minOf(LIMIT, demand * share) }
        points += UsagePoint(end - SECONDS_PER_MINUTE.seconds, peak)
    }
    if (now > currentStart) {
        points += cycle(currentStart, now, zone) { share -> window.usedPercent * share }
        points += UsagePoint(now, window.usedPercent)
    }
    val cutoff = now.minus(DEMO_RETENTION)
    return points.filter { it.at >= cutoff }
}

/**
 * One point per hour from [start] to before [end]. [usage] turns the share of the cycle's activity
 * that happened so far, from 0 to 1, into the usage at that point.
 */
private fun cycle(
    start: Instant,
    end: Instant,
    zone: ZoneId,
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
private fun activity(at: Instant, zone: ZoneId): Double {
    val local = at.toJavaInstant().atZone(zone)
    val weekend = local.dayOfWeek == DayOfWeek.SATURDAY || local.dayOfWeek == DayOfWeek.SUNDAY
    val base =
        when (local.hour) {
            in WORK_MORNING -> if (weekend) WEEKEND_DAY else MORNING
            in WORK_AFTERNOON -> if (weekend) WEEKEND_DAY else AFTERNOON
            in EVENING -> if (weekend) WEEKEND_DAY else EVENING_WEIGHT
            else -> NIGHT
        }
    // Some days are busier than others; the sine keeps it the same on every run.
    val day = local.toLocalDate().toEpochDay().toDouble()
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
