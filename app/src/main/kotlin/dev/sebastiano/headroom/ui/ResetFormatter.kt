package dev.sebastiano.headroom.ui

import dev.c5inco.charts.core.ValueFormat
import dev.sebastiano.headroom.model.Countdown
import java.time.DayOfWeek
import java.time.Instant as JavaInstant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlin.time.toJavaInstant

/**
 * Formats reset times in the user's zone, language and clock style. Short labels fit a bar row
 * ("Tue 07:00", or "11 Oct" when the reset is more than six days away); long labels name the day in
 * full ("Tue 30 Sep, 07:00").
 */
class ResetFormatter(val zone: ZoneId, val locale: Locale, val is24Hour: Boolean) {
    private val time = if (is24Hour) "HH:mm" else "h:mm a"
    private val weekdayTime = DateTimeFormatter.ofPattern("EEE $time", locale).withZone(zone)
    private val dayMonth = DateTimeFormatter.ofPattern("d MMM", locale).withZone(zone)
    private val full = DateTimeFormatter.ofPattern("EEE d MMM, $time", locale).withZone(zone)
    private val weekdayDate = DateTimeFormatter.ofPattern("EEE d MMM", locale)
    private val dayMonthYear = DateTimeFormatter.ofPattern("d MMM yyyy", locale).withZone(zone)
    private val timeOfDay = DateTimeFormatter.ofPattern(time, locale)
    private val chartMillis = DateTimeFormatter.ofPattern(".SSS", locale)
    private val chartSeconds = DateTimeFormatter.ofPattern(":ss", locale)
    private val chartTime = DateTimeFormatter.ofPattern(time, locale)
    private val chartHour12 = DateTimeFormatter.ofPattern("h a", locale)
    private val chartMonth = DateTimeFormatter.ofPattern("MMM", locale)
    private val chartYear = DateTimeFormatter.ofPattern("yyyy", locale)

    fun short(at: Instant, now: Instant): String =
        if (at - now > NEAR) dayMonth.format(at.toJavaInstant())
        else weekdayTime.format(at.toJavaInstant())

    fun long(at: Instant): String = full.format(at.toJavaInstant())

    /** A date with its year, for an expiry that can be months away: "5 Nov 2026". */
    fun date(at: Instant): String = dayMonthYear.format(at.toJavaInstant())

    fun countdown(now: Instant, at: Instant): String = Countdown.format(at - now)

    /** The local day of [at], with its weekday: "Wed 30 Sep". */
    fun day(at: Instant): String = day(at.toJavaInstant().atZone(zone).toLocalDate())

    fun day(date: LocalDate): String = weekdayDate.format(date)

    /** The start of an hour of the day, in the user's clock style: "14:00" or "2:00 PM". */
    fun hour(hour: Int): String = timeOfDay.format(LocalTime.of(hour, 0))

    /** The local day of the week of [at], for chart labels. */
    fun dayOfWeek(at: Instant): DayOfWeek = at.toJavaInstant().atZone(zone).dayOfWeek

    /**
     * Multi-scale x-axis tick formatter in [zone], [locale] and [is24Hour]: each tick prints the
     * coarsest calendar field that changed.
     */
    fun chartTickFormat(): ValueFormat = ValueFormat { epochMillis ->
        val zdt = JavaInstant.ofEpochMilli(epochMillis.toLong()).atZone(zone)
        when {
            zdt.nano != 0 -> chartMillis.format(zdt)
            zdt.second != 0 -> chartSeconds.format(zdt)
            zdt.minute != 0 -> chartTime.format(zdt)
            zdt.hour != 0 -> if (is24Hour) chartTime.format(zdt) else chartHour12.format(zdt)
            zdt.dayOfMonth != 1 -> dayMonth.format(zdt)
            zdt.monthValue != 1 -> chartMonth.format(zdt)
            else -> chartYear.format(zdt)
        }
    }

    /**
     * Formats a scrubbed point's timestamp in [zone], [locale] and [is24Hour] for chart tooltips.
     */
    fun chartPointFormat(): ValueFormat = ValueFormat { epochMillis ->
        long(Instant.fromEpochMilliseconds(epochMillis.toLong()))
    }

    private companion object {
        val NEAR: Duration = 6.days
    }
}
