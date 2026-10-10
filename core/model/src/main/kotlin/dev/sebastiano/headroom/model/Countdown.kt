package dev.sebastiano.headroom.model

import kotlin.time.Duration

/** Formats the time left until a reset, for example "15h 28m" or "2d 18h". */
public object Countdown {
    private const val MINUTES_PER_HOUR = 60L
    private const val HOURS_PER_DAY = 24L

    public fun format(remaining: Duration): String {
        if (remaining.isNegative() || remaining == Duration.ZERO) return "now"
        val totalMinutes = remaining.inWholeMinutes
        val days = totalMinutes / (MINUTES_PER_HOUR * HOURS_PER_DAY)
        val hours = (totalMinutes / MINUTES_PER_HOUR) % HOURS_PER_DAY
        val minutes = totalMinutes % MINUTES_PER_HOUR
        return if (days > 0) {
            "${days}d ${hours}h"
        } else {
            "${hours}h ${minutes.toString().padStart(2, '0')}m"
        }
    }
}
