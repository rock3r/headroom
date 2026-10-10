package dev.sebastiano.headroom.ui

import dev.sebastiano.headroom.model.Countdown
import java.time.DayOfWeek
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
class ResetFormatter(private val zone: ZoneId, locale: Locale, is24Hour: Boolean) {
    private val time = if (is24Hour) "HH:mm" else "h:mm a"
    private val weekdayTime = DateTimeFormatter.ofPattern("EEE $time", locale).withZone(zone)
    private val dayMonth = DateTimeFormatter.ofPattern("d MMM", locale).withZone(zone)
    private val full = DateTimeFormatter.ofPattern("EEE d MMM, $time", locale).withZone(zone)
    private val weekdayDate = DateTimeFormatter.ofPattern("EEE d MMM", locale)
    private val dayMonthYear = DateTimeFormatter.ofPattern("d MMM yyyy", locale).withZone(zone)
    private val timeOfDay = DateTimeFormatter.ofPattern(time, locale)

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

    private companion object {
        val NEAR: Duration = 6.days
    }
}
