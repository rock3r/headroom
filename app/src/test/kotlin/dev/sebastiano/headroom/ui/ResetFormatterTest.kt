package dev.sebastiano.headroom.ui

import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

class ResetFormatterTest {
    private val now = Instant.parse("2026-09-27T12:32:00Z")
    private val formatter = ResetFormatter(ZoneOffset.UTC, Locale.US, is24Hour = true)

    @Test
    fun `a reset within the week shows the weekday and time`() {
        assertEquals("Wed 07:00", formatter.short(Instant.parse("2026-09-30T07:00:00Z"), now))
    }

    @Test
    fun `a reset more than six days away shows the date`() {
        assertEquals("11 Oct", formatter.short(Instant.parse("2026-10-11T07:00:00Z"), now))
    }

    @Test
    fun `the long form has weekday, date and time`() {
        assertEquals("Wed 30 Sep, 07:00", formatter.long(Instant.parse("2026-09-30T07:00:00Z")))
    }

    @Test
    fun `an expiry date has the day, month and year in the local zone`() {
        assertEquals("5 Nov 2026", formatter.date(Instant.parse("2026-11-05T07:59:00Z")))
        val tokyo = ResetFormatter(java.time.ZoneId.of("Asia/Tokyo"), Locale.US, is24Hour = true)
        assertEquals("6 Nov 2026", tokyo.date(Instant.parse("2026-11-05T20:00:00Z")))
    }

    @Test
    fun `twelve hour clocks get am and pm`() {
        val twelve = ResetFormatter(ZoneOffset.UTC, Locale.US, is24Hour = false)
        assertEquals("Tue 7:00 PM", twelve.short(Instant.parse("2026-09-29T19:00:00Z"), now))
    }

    @Test
    fun `days and hours for the stats follow the clock style`() {
        val twelve = ResetFormatter(ZoneOffset.UTC, Locale.US, is24Hour = false)
        assertEquals("Wed 30 Sep", formatter.day(Instant.parse("2026-09-30T23:00:00Z")))
        assertEquals("Wed 30 Sep", formatter.day(java.time.LocalDate.parse("2026-09-30")))
        assertEquals("14:00", formatter.hour(14))
        assertEquals("2:00 PM", twelve.hour(14))
    }

    @Test
    fun `the countdown uses the model format`() {
        assertEquals("2d 18h", formatter.countdown(now, Instant.parse("2026-09-30T07:00:00Z")))
        assertEquals("1h 12m", formatter.countdown(now, Instant.parse("2026-09-27T13:44:00Z")))
    }

    @Test
    fun `the zone decides the local time`() {
        val rome = ResetFormatter(java.time.ZoneId.of("Europe/Rome"), Locale.US, is24Hour = true)
        assertEquals("Wed 09:00", rome.short(Instant.parse("2026-09-30T07:00:00Z"), now))
    }
}
