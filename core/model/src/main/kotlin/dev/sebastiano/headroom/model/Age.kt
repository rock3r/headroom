package dev.sebastiano.headroom.model

import java.time.Duration
import java.time.Instant
import kotlin.math.roundToLong

/**
 * How long ago something happened, in the largest unit that fits, rounded to the nearest one: 1 h
 * 58 min is 2 hours. The UI words it with plurals, for example "2 hours ago".
 */
public sealed interface Age {
    public data object JustNow : Age

    public data class Minutes(val count: Int) : Age

    public data class Hours(val count: Int) : Age

    public data class Days(val count: Int) : Age

    public companion object {
        private const val SECONDS_PER_MINUTE = 60.0
        private const val MINUTES_PER_HOUR = 60
        private const val HOURS_PER_DAY = 24

        /** The age of [then] at [now]. A time after [now] is [JustNow]. */
        public fun between(then: Instant, now: Instant): Age {
            val seconds = Duration.between(then, now).seconds.coerceAtLeast(0)
            val minutes = (seconds / SECONDS_PER_MINUTE).roundToLong().toInt()
            val hours = (minutes / MINUTES_PER_HOUR.toDouble()).roundToLong().toInt()
            val days = (hours / HOURS_PER_DAY.toDouble()).roundToLong().toInt()
            return when {
                minutes < 1 -> JustNow
                minutes < MINUTES_PER_HOUR -> Minutes(minutes)
                hours < HOURS_PER_DAY -> Hours(hours)
                else -> Days(days)
            }
        }
    }
}
