package dev.sebastiano.headroom.model

import java.time.Duration
import java.time.Instant

public enum class PaceStatus {
    Under,
    On,
    Over,
}

/**
 * Compares actual usage with "even pace": the share of the window that has elapsed. A window that
 * is 50% through and 60% used is 10 points over pace.
 */
public object Pace {
    /** Points above or below pace that still count as "on pace". */
    public const val TOLERANCE_POINTS: Double = 5.0

    /** A window this full needs attention whatever the pace. */
    public const val NEARLY_FULL_PERCENT: Double = 85.0

    public fun expectedPercent(window: QuotaWindow, now: Instant): Double? {
        val resetsAt = window.resetsAt ?: return null
        val length = window.length ?: return null
        if (length.isZero || length.isNegative) return null
        val start = resetsAt.minus(length)
        val elapsed = Duration.between(start, now).toMillis().toDouble()
        return (elapsed / length.toMillis() * MAX_PERCENT).coerceIn(0.0, MAX_PERCENT)
    }

    /** Signed difference from even pace, in percentage points. Positive means over pace. */
    public fun delta(window: QuotaWindow, now: Instant): Double? =
        expectedPercent(window, now)?.let { window.usedPercent - it }

    public fun status(window: QuotaWindow, now: Instant): PaceStatus {
        val delta = delta(window, now) ?: return PaceStatus.On
        return when {
            delta > TOLERANCE_POINTS -> PaceStatus.Over
            delta < -TOLERANCE_POINTS -> PaceStatus.Under
            else -> PaceStatus.On
        }
    }

    /** True when the window is over pace or nearly used up. The UI marks these as wavy. */
    public fun needsAttention(window: QuotaWindow, now: Instant): Boolean =
        window.usedPercent >= NEARLY_FULL_PERCENT || status(window, now) == PaceStatus.Over

    /**
     * When the window reaches 100% if usage continues at the average rate so far, or null when that
     * happens only after the reset (or cannot be computed).
     */
    public fun projectedLimitAt(window: QuotaWindow, now: Instant): Instant? {
        val resetsAt = window.resetsAt ?: return null
        val length = window.length ?: return null
        val start = resetsAt.minus(length)
        val elapsedMillis = Duration.between(start, now).toMillis()
        if (elapsedMillis <= 0 || window.usedPercent <= 0.0) return null
        val millisToFull = elapsedMillis / window.usedPercent * MAX_PERCENT
        val hit = start.plusMillis(millisToFull.toLong())
        return hit.takeIf { it.isBefore(resetsAt) }
    }
}
