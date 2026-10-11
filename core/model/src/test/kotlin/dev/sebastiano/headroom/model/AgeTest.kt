package dev.sebastiano.headroom.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class AgeTest {
    private val now = Instant.parse("2026-09-27T12:32:00Z")

    private fun ago(duration: Duration) = Age.between(now.minus(duration), now)

    @Test
    fun `rounds to the nearest unit`() {
        assertEquals(Age.JustNow, ago(29.seconds))
        assertEquals(Age.Minutes(1), ago(30.seconds))
        assertEquals(Age.Minutes(45), ago(45.minutes))
        assertEquals(Age.Hours(1), ago((59 * 60 + 40).seconds))
        assertEquals(Age.Hours(2), ago(118.minutes))
        assertEquals(Age.Hours(1), ago(89.minutes))
        assertEquals(Age.Hours(23), ago((23 * 60 + 29).minutes))
        assertEquals(Age.Days(1), ago((23 * 60 + 30).minutes))
        assertEquals(Age.Days(3), ago(70.hours))
    }

    @Test
    fun `a time in the future is just now`() {
        assertEquals(Age.JustNow, Age.between(now + 90.seconds, now))
    }
}
