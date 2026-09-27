package dev.sebastiano.headroom.model

import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals

class CountdownTest {
    @Test
    fun `under a day shows hours and zero-padded minutes`() {
        assertEquals("15h 28m", Countdown.format(Duration.ofMinutes(15 * 60 + 28)))
        assertEquals("0h 05m", Countdown.format(Duration.ofMinutes(5)))
    }

    @Test
    fun `a day or more shows days and hours`() {
        assertEquals("2d 18h", Countdown.format(Duration.ofHours(66).plusMinutes(28)))
    }

    @Test
    fun `negative durations read as now`() {
        assertEquals("now", Countdown.format(Duration.ofMinutes(-3)))
    }
}
