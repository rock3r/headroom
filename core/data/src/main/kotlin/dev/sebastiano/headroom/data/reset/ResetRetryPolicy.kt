package dev.sebastiano.headroom.data.reset

import java.time.Duration

/** How long to wait before checking again when a reset has not shown up yet. */
internal object ResetRetryPolicy {
    private val DELAYS: List<Duration> =
        listOf(
            // The first check runs seconds after the reset, so a slow provider gets a quick retry.
            Duration.ofSeconds(30),
            Duration.ofMinutes(2),
            Duration.ofMinutes(5),
            Duration.ofMinutes(15),
            Duration.ofMinutes(60),
        )

    /** The delay after the given failed attempt (1-based), or null to stop trying. */
    fun delayAfterAttempt(attempt: Int): Duration? = DELAYS.getOrNull(attempt - 1)
}
