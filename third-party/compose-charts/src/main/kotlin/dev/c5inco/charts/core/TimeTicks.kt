package dev.c5inco.charts.core

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.max

/**
 * Calendar-aware ticks and labels for time axes. A port of d3-scale's `scaleTime().ticks()`
 * interval choice and its multi-scale tick format, on java.time. Times are epoch milliseconds.
 */
object TimeTicks {
    private const val SECOND = 1_000L
    private const val MINUTE = 60 * SECOND
    private const val HOUR = 60 * MINUTE
    private const val DAY = 24 * HOUR

    internal enum class Unit(val chrono: ChronoUnit) { Second(ChronoUnit.SECONDS), Minute(ChronoUnit.MINUTES), Hour(ChronoUnit.HOURS), Day(ChronoUnit.DAYS), Week(ChronoUnit.WEEKS), Month(ChronoUnit.MONTHS), Year(ChronoUnit.YEARS) }

    internal data class Interval(val unit: Unit, val step: Int, val millis: Long)

    /** d3's tick intervals, smallest first. Steps are multiples of the unit's own calendar field. */
    private val intervals = listOf(
        Interval(Unit.Second, 1, SECOND), Interval(Unit.Second, 5, 5 * SECOND),
        Interval(Unit.Second, 15, 15 * SECOND), Interval(Unit.Second, 30, 30 * SECOND),
        Interval(Unit.Minute, 1, MINUTE), Interval(Unit.Minute, 5, 5 * MINUTE),
        Interval(Unit.Minute, 15, 15 * MINUTE), Interval(Unit.Minute, 30, 30 * MINUTE),
        Interval(Unit.Hour, 1, HOUR), Interval(Unit.Hour, 3, 3 * HOUR),
        Interval(Unit.Hour, 6, 6 * HOUR), Interval(Unit.Hour, 12, 12 * HOUR),
        Interval(Unit.Day, 1, DAY), Interval(Unit.Day, 2, 2 * DAY),
        Interval(Unit.Week, 1, 7 * DAY),
        Interval(Unit.Month, 1, 30 * DAY), Interval(Unit.Month, 3, 90 * DAY),
        Interval(Unit.Year, 1, 365 * DAY),
    )

    /** The interval whose length is closest (by ratio) to `span / count`. */
    internal fun interval(start: Double, stop: Double, count: Int): Interval? {
        val target = (stop - start) / max(1, count)
        val i = intervals.indexOfFirst { it.millis > target }
        return when {
            i == 0 -> null // finer than a second: plain numeric ticks
            i == -1 -> {
                val years = Ticks.tickStep(start / (365.0 * DAY), stop / (365.0 * DAY), count)
                Interval(Unit.Year, max(1, Math.round(years).toInt()), 365 * DAY)
            }
            target / intervals[i - 1].millis < intervals[i].millis / target -> intervals[i - 1]
            else -> intervals[i]
        }
    }

    fun ticks(start: Double, stop: Double, count: Int, zone: ZoneId): List<Double> {
        if (!(stop > start) || count <= 0) return if (start == stop) listOf(start) else emptyList()
        val interval = interval(start, stop, count) ?: return Ticks.ticks(start, stop, count)
        val unit = interval.unit
        // Floor to the unit, then walk one unit at a time and keep aligned boundaries. A walk keeps
        // alignment honest across month lengths and DST, and is bounded by count × step.
        var t = floor(Instant.ofEpochMilli(start.toLong()).atZone(zone), unit)
        val out = mutableListOf<Double>()
        while (true) {
            val ms = t.toInstant().toEpochMilli().toDouble()
            if (ms > stop) break
            if (ms >= start && aligned(t, unit, interval.step)) out += ms
            t = next(t, unit)
        }
        return out
    }

    private fun floor(t: ZonedDateTime, unit: Unit): ZonedDateTime = when (unit) {
        Unit.Second -> t.truncatedTo(ChronoUnit.SECONDS)
        Unit.Minute -> t.truncatedTo(ChronoUnit.MINUTES)
        Unit.Hour -> t.truncatedTo(ChronoUnit.HOURS)
        Unit.Day -> t.toLocalDate().atStartOfDay(t.zone)
        Unit.Week -> t.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay(t.zone)
        Unit.Month -> t.toLocalDate().withDayOfMonth(1).atStartOfDay(t.zone)
        Unit.Year -> t.toLocalDate().withDayOfYear(1).atStartOfDay(t.zone)
    }

    private fun next(t: ZonedDateTime, unit: Unit): ZonedDateTime {
        // A missing midnight resolves to (for example) 01:00. Reconstruct each calendar
        // boundary from its date so that this adjustment does not leak into following ticks.
        val date = t.toLocalDate()
        val nextDate = when (unit) {
            Unit.Day -> date.plusDays(1)
            Unit.Week -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusWeeks(1)
            Unit.Month -> date.withDayOfMonth(1).plusMonths(1)
            Unit.Year -> date.withDayOfYear(1).plusYears(1)
            else -> null
        }
        if (nextDate != null) return nextDate.atStartOfDay(t.zone)
        // Preserve both occurrences of an overlapping hour, but restore local boundaries after
        // non-hour DST shifts. Flooring can return the same instant during a half-hour overlap.
        var advanced = t.plus(1, unit.chrono)
        var boundary = floor(advanced, unit)
        while (!boundary.isAfter(t)) {
            advanced = advanced.plus(1, unit.chrono)
            boundary = floor(advanced, unit)
        }
        return boundary
    }

    private fun aligned(t: ZonedDateTime, unit: Unit, step: Int): Boolean = step == 1 || when (unit) {
        Unit.Second -> t.second % step == 0
        Unit.Minute -> t.minute % step == 0
        Unit.Hour -> t.hour % step == 0
        Unit.Day -> (t.dayOfMonth - 1) % step == 0
        Unit.Week -> true
        Unit.Month -> (t.monthValue - 1) % step == 0
        Unit.Year -> t.year % step == 0
    }

    /**
     * d3's multi-scale tick format: each label shows the coarsest field that changed at its tick, so
     * a day's ticks read `Oct 5, 06:00, 12:00, 18:00, Oct 6` and a year's read `2026, Feb, Mar`.
     */
    fun tickFormat(zone: ZoneId, locale: Locale = Locale.getDefault()): ValueFormat {
        val millis = DateTimeFormatter.ofPattern(".SSS", locale)
        val seconds = DateTimeFormatter.ofPattern(":ss", locale)
        val time = DateTimeFormatter.ofPattern("HH:mm", locale)
        val day = DateTimeFormatter.ofPattern("MMM d", locale)
        val month = DateTimeFormatter.ofPattern("MMM", locale)
        val year = DateTimeFormatter.ofPattern("yyyy", locale)
        return ValueFormat { value ->
            val t = Instant.ofEpochMilli(value.toLong()).atZone(zone)
            val f = when {
                t.nano != 0 -> millis
                t.second != 0 -> seconds
                t.minute != 0 || t.hour != 0 -> time
                t.dayOfMonth != 1 -> day
                t.monthValue != 1 -> month
                else -> year
            }
            f.format(t)
        }
    }

    /** A tooltip format with enough precision to tell neighbouring points apart. */
    fun pointFormat(spacing: Double, zone: ZoneId, locale: Locale = Locale.getDefault()): ValueFormat {
        val pattern = when {
            spacing < SECOND -> "MMM d, HH:mm:ss.SSS"
            spacing < MINUTE -> "MMM d, HH:mm:ss"
            spacing < DAY -> "EEE, MMM d · HH:mm"
            spacing < 28 * DAY -> "EEE, MMM d, yyyy"
            spacing < 365 * DAY -> "MMMM yyyy"
            else -> "yyyy"
        }
        val f = DateTimeFormatter.ofPattern(pattern, locale)
        return ValueFormat { f.format(Instant.ofEpochMilli(it.toLong()).atZone(zone)) }
    }
}
