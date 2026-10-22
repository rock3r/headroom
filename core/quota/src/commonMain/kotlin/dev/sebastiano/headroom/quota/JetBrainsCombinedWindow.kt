package dev.sebastiano.headroom.quota

import dev.sebastiano.headroom.model.Pace
import dev.sebastiano.headroom.model.QuotaWindow
import kotlin.time.Duration
import kotlin.time.Instant

/** The id of the window that pools the credits of every JetBrains license and seat. */
internal const val COMBINED_WINDOW_ID = "jb:combined"

/** What the app shows for the window that pools every JetBrains license and seat. */
internal const val COMBINED_WINDOW_LABEL = "Combined"

/**
 * Puts a window that pools the credits of [windows] in front of them, when there is more than one.
 *
 * Its used share is all the credits used over all the credits available, so each license and seat
 * counts as much as it holds: a small license that ran out does not make the account look used up
 * while a bigger one still has credits. It resets when the soonest window resets, with that
 * window's kind, because that is when the share next changes.
 *
 * Its pace must be the credit-weighted pace of the windows, not the soonest one's: licenses renew
 * on different days. [Pace] reads the pace from the reset and the length, so the length is the one
 * that makes [Pace.expectedPercent] at [now] equal to that weighted pace (see [pacedLength]). The
 * fetcher builds the window again on every sync, so it stays close between syncs.
 *
 * The pooled windows stay, below it, marked [QuotaWindow.isSecondary] so that the combined window
 * leads the account. It has no chat binding: a chat is billed to one license or seat.
 */
internal fun withCombinedWindow(windows: List<QuotaWindow>, now: Instant): List<QuotaWindow> {
    val pooled = windows.filter { (it.limitAmount ?: 0.0) > 0.0 && it.usedAmount != null }
    if (pooled.size < 2) return windows
    val used = pooled.sumOf { it.usedAmount ?: 0.0 }
    val limit = pooled.sumOf { it.limitAmount ?: 0.0 }
    val soonest = pooled.minWith(compareBy(nullsLast()) { it.resetsAt })
    val combined =
        QuotaWindow(
            id = COMBINED_WINDOW_ID,
            label = COMBINED_WINDOW_LABEL,
            kind = soonest.kind,
            usedPercent = (used / limit * MAX_PERCENT).coerceIn(MIN_PERCENT, MAX_PERCENT),
            resetsAt = soonest.resetsAt,
            length = pacedLength(pooled, limit, soonest, now) ?: soonest.length,
            usedAmount = used,
            limitAmount = limit,
            amountUnit = soonest.amountUnit,
        )
    return listOf(combined) + windows.map { it.copy(isSecondary = true) }
}

/**
 * The length that gives a window resetting with [soonest] the credit-weighted pace of [pooled] at
 * [now]. Null when a window has no pace, or the soonest reset has passed, so there is none to give.
 */
private fun pacedLength(
    pooled: List<QuotaWindow>,
    limit: Double,
    soonest: QuotaWindow,
    now: Instant,
): Duration? {
    val untilReset = soonest.resetsAt?.minus(now)?.takeIf { it > Duration.ZERO } ?: return null
    var weighted = 0.0
    for (window in pooled) {
        val expected = Pace.expectedPercent(window, now) ?: return null
        weighted += expected * (window.limitAmount ?: 0.0) / limit
    }
    // Pace is (elapsed / length), and elapsed is length minus the time left until the reset.
    val leftShare = 1.0 - weighted / MAX_PERCENT
    return if (leftShare > 0.0) untilReset / leftShare else null
}
