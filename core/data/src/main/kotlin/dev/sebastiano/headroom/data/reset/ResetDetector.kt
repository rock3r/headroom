package dev.sebastiano.headroom.data.reset

import dev.sebastiano.headroom.model.QuotaWindow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

/**
 * Decides whether a window really reset, by comparing what the app knew before the reset time with
 * a fresh fetch taken after it. The app never alerts on the scheduled time alone.
 */
internal object ResetDetector {
    /** A drop smaller than this is treated as rounding noise, not a reset. */
    private const val MIN_DROP_POINTS = 5.0

    /** A new reset time this much later than the old one means a new window started. */
    private val MIN_RESET_SHIFT: Duration = 5.days

    fun hasReset(before: QuotaWindow, after: QuotaWindow): Boolean {
        val dropped = before.usedPercent - after.usedPercent >= MIN_DROP_POINTS
        val oldReset = before.resetsAt
        val newReset = after.resetsAt
        val moved = oldReset != null && newReset != null && (newReset - oldReset) >= MIN_RESET_SHIFT
        return dropped || moved
    }
}
