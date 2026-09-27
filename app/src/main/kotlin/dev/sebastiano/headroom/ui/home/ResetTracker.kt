package dev.sebastiano.headroom.ui.home

import dev.sebastiano.headroom.model.AccountState
import dev.sebastiano.headroom.model.QuotaWindow
import dev.sebastiano.headroom.model.WindowKind
import java.time.Duration

/**
 * Notices weekly resets that happen while the app is open, so the overview can mark the account
 * "Just reset" for the rest of the session and play the one-off drain animation.
 *
 * A reset uses the same rule as the data layer's reset check: usage dropped by at least
 * [MIN_DROP_POINTS], or the reset time moved forward by at least [MIN_RESET_SHIFT].
 */
class ResetTracker {
    private val lastSeen = mutableMapOf<String, QuotaWindow>()
    private val justReset = mutableSetOf<String>()

    /** Records [accounts] and returns the ids of the accounts that reset during this session. */
    fun update(accounts: List<AccountState>): Set<String> {
        accounts.forEach { state ->
            val window =
                state.primaryWindow?.takeIf { it.kind == WindowKind.Weekly } ?: return@forEach
            val id = state.account.id
            val before = lastSeen[id]
            if (before != null && before.id == window.id && hasReset(before, window)) {
                justReset += id
            }
            lastSeen[id] = window
        }
        return justReset.toSet()
    }

    private fun hasReset(before: QuotaWindow, after: QuotaWindow): Boolean {
        val dropped = before.usedPercent - after.usedPercent >= MIN_DROP_POINTS
        val oldReset = before.resetsAt
        val newReset = after.resetsAt
        val moved =
            oldReset != null &&
                newReset != null &&
                Duration.between(oldReset, newReset) >= MIN_RESET_SHIFT
        return dropped || moved
    }

    private companion object {
        const val MIN_DROP_POINTS = 5.0
        val MIN_RESET_SHIFT: Duration = Duration.ofDays(5)
    }
}
