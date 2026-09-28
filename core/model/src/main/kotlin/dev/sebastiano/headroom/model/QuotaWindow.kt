package dev.sebastiano.headroom.model

import java.time.Duration
import java.time.Instant

/** One usage limit of a subscription, such as "weekly, all models" or "5-hour session". */
public data class QuotaWindow(
    /** Stable identifier from the provider, for example `seven_day` or `five_hour`. */
    val id: String,
    val label: String,
    val kind: WindowKind,
    /** Share of the window already used, from 0 to 100. */
    val usedPercent: Double,
    val resetsAt: Instant?,
    val length: Duration?,
    /** Optional grouping shown above related windows, for example a model family. */
    val group: String? = null,
    val isUnlimited: Boolean = false,
    /** How much of the window is used, in [amountUnit], for providers that report amounts. */
    val usedAmount: Double? = null,
    /** The window's limit, in [amountUnit], for providers that report amounts. */
    val limitAmount: Double? = null,
    /** The unit of [usedAmount] and [limitAmount], for example `credits`. */
    val amountUnit: String? = null,
) {
    val remainingPercent: Double
        get() = (MAX_PERCENT - usedPercent).coerceIn(0.0, MAX_PERCENT)
}

internal const val MAX_PERCENT = 100.0

/** How long a window lasts. Alerts and the pace maths depend on it. */
public enum class WindowKind {
    Session,
    Daily,
    Weekly,
    Monthly,
    Other;

    public companion object {
        private val SESSION_MAX = Duration.ofHours(12)
        private val DAILY_MAX = Duration.ofDays(2)
        private val WEEKLY_MAX = Duration.ofDays(10)
        private val MONTHLY_MAX = Duration.ofDays(35)

        public fun fromLength(length: Duration?): WindowKind =
            when {
                length == null -> Other
                length <= SESSION_MAX -> Session
                length <= DAILY_MAX -> Daily
                length <= WEEKLY_MAX -> Weekly
                length <= MONTHLY_MAX -> Monthly
                else -> Other
            }
    }
}
