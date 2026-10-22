package dev.sebastiano.headroom.quota

import dev.sebastiano.headroom.model.QuotaWindow

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
 * window's kind and length, because that is when the share next changes.
 *
 * The pooled windows stay, below it, marked [QuotaWindow.isSecondary] so that the combined window
 * leads the account. It has no chat binding: a chat is billed to one license or seat.
 */
internal fun withCombinedWindow(windows: List<QuotaWindow>): List<QuotaWindow> {
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
            length = soonest.length,
            usedAmount = used,
            limitAmount = limit,
            amountUnit = soonest.amountUnit,
        )
    return listOf(combined) + windows.map { it.copy(isSecondary = true) }
}
