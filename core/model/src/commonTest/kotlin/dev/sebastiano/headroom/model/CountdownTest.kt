package dev.sebastiano.headroom.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class CountdownTest {
    @Test
    fun `under a day shows hours and zero-padded minutes`() {
        assertEquals("15h 28m", Countdown.format((15 * 60 + 28).minutes))
        assertEquals("0h 05m", Countdown.format(5.minutes))
    }

    @Test
    fun `a day or more shows days and hours`() {
        assertEquals("2d 18h", Countdown.format(66.hours + 28.minutes))
    }

    @Test
    fun `negative durations read as now`() {
        assertEquals("now", Countdown.format((-3).minutes))
    }
}
