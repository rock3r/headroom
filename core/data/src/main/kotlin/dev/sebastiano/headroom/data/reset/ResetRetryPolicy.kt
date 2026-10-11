package dev.sebastiano.headroom.data.reset

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** How long to wait before checking again when a reset has not shown up yet. */
internal object ResetRetryPolicy {
    private val DELAYS: List<Duration> =
        listOf(
            // The first check runs seconds after the reset, so a slow provider gets a quick retry.
            30.seconds,
            2.minutes,
            5.minutes,
            15.minutes,
            60.minutes,
        )

    /** The delay after the given failed attempt (1-based), or null to stop trying. */
    fun delayAfterAttempt(attempt: Int): Duration? = DELAYS.getOrNull(attempt - 1)
}
