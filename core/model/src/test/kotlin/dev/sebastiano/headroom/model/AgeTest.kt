package dev.sebastiano.headroom.model

import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class AgeTest {
    private val now = Instant.parse("2026-09-27T12:32:00Z")

    private fun ago(duration: Duration) = Age.between(now.minus(duration), now)

    @Test
    fun `rounds to the nearest unit`() {
        assertEquals(Age.JustNow, ago(Duration.ofSeconds(29)))
        assertEquals(Age.Minutes(1), ago(Duration.ofSeconds(30)))
        assertEquals(Age.Minutes(45), ago(Duration.ofMinutes(45)))
        assertEquals(Age.Hours(1), ago(Duration.ofSeconds(59 * 60 + 40)))
        assertEquals(Age.Hours(2), ago(Duration.ofMinutes(118)))
        assertEquals(Age.Hours(1), ago(Duration.ofMinutes(89)))
        assertEquals(Age.Hours(23), ago(Duration.ofMinutes(23 * 60 + 29)))
        assertEquals(Age.Days(1), ago(Duration.ofMinutes(23 * 60 + 30)))
        assertEquals(Age.Days(3), ago(Duration.ofHours(70)))
    }

    @Test
    fun `a time in the future is just now`() {
        assertEquals(Age.JustNow, Age.between(now.plusSeconds(90), now))
    }
}
