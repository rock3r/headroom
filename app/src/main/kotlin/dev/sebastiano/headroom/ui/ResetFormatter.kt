package dev.sebastiano.headroom.ui

import dev.sebastiano.headroom.model.Countdown
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

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

    fun short(at: Instant, now: Instant): String =
        if (Duration.between(now, at) > NEAR) dayMonth.format(at) else weekdayTime.format(at)

    fun long(at: Instant): String = full.format(at)

    fun countdown(now: Instant, at: Instant): String = Countdown.format(Duration.between(now, at))

    /** The local day of the week of [at], for chart labels. */
    fun dayOfWeek(at: Instant): DayOfWeek = at.atZone(zone).dayOfWeek

    private companion object {
        val NEAR: Duration = Duration.ofDays(6)
    }
}
