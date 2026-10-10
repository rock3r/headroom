package dev.sebastiano.headroom.model

import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

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
    /** The unit of [usedAmount] and [limitAmount], for example `credits` or `USD`. */
    val amountUnit: String? = null,
    /**
     * When a [WindowKind.Credit] expires. A credit does not reset, so its [resetsAt] is null and
     * this holds the date the provider reports instead.
     */
    val expiresAt: Instant? = null,
    /**
     * False when the provider sent a window the app does not know. The app shows it, with an
     * explanation, but it is [isInformational].
     */
    val isRecognised: Boolean = true,
    /**
     * Opaque identity a later chat caller needs to bill this window. JetBrains license and seat
     * windows set it; other providers leave it null. It is not a token and must not be logged.
     */
    val binding: String? = null,
) {
    val remainingPercent: Double
        get() = (MAX_PERCENT - usedPercent).coerceIn(0.0, MAX_PERCENT)

    /**
     * True for credits and for windows the app does not recognise. The app shows them for
     * information only: they never alert, never count as the next reset, never drive the pace and
     * are never an account's primary window.
     */
    val isInformational: Boolean
        get() = kind == WindowKind.Credit || !isRecognised
}

internal const val MAX_PERCENT = 100.0

/** How long a window lasts. Alerts and the pace maths depend on it. */
public enum class WindowKind {
    Session,
    Daily,
    Weekly,
    Monthly,
    Other,
    /**
     * A one-time amount, such as a promotional credit. It expires on [QuotaWindow.expiresAt] and
     * never resets. [fromLength] never returns it.
     */
    Credit;

    public companion object {
        private val SESSION_MAX = 12.hours
        private val DAILY_MAX = 2.days
        private val WEEKLY_MAX = 10.days
        private val MONTHLY_MAX = 35.days

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
