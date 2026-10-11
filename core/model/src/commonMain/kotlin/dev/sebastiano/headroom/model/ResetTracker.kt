package dev.sebastiano.headroom.model

import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

/**
 * A reset the app saw happen while it was open, for the reset confetti. [fromNextReset] is true
 * when the next reset card was counting down to it, so the confetti bursts from that card rather
 * than from the account's own card. [windowId] is the window that reset.
 */
public data class ResetBurst(
    val accountId: String,
    val fromNextReset: Boolean,
    val windowId: String? = null,
)

/**
 * Notices weekly resets that happen while the app is open, so the overview can mark the account
 * "Just reset" for the rest of the session and play the one-off drain animation.
 *
 * A reset uses the same rule as the data layer's reset check: usage dropped by at least
 * [MIN_DROP_POINTS], or the reset time moved forward by at least [MIN_RESET_SHIFT].
 *
 * A reset also counts as live, for the confetti in [lastLiveResets], only when the data from before
 * it was fetched at or after [sessionStart]. On a cold start the stored data comes first, fetched
 * before the app opened, so a reset that the first refresh finds happened while the app was closed.
 */
public class ResetTracker(private val sessionStart: Instant = Instant.DISTANT_PAST) {
    private val lastSeen = mutableMapOf<String, Seen>()
    private val justReset = mutableSetOf<String>()
    private var previous: List<AccountState> = emptyList()

    /** The live resets that the last [update] found, in the accounts' order. */
    public var lastLiveResets: List<ResetBurst> = emptyList()
        private set

    /** Records [accounts] and returns the ids of the accounts that reset during this session. */
    public fun update(accounts: List<AccountState>): Set<String> {
        val live = mutableListOf<ResetBurst>()
        accounts.forEach { state ->
            val window =
                state.primaryWindow?.takeIf { it.kind == WindowKind.Weekly } ?: return@forEach
            val id = state.account.id
            val before = lastSeen[id]
            if (
                before != null && before.window.id == window.id && hasReset(before.window, window)
            ) {
                justReset += id
                if (before.fetchedAt?.let { it >= sessionStart } == true) {
                    val seenAt = state.snapshot?.fetchedAt
                    live +=
                        ResetBurst(
                            id,
                            fromNextReset = wasNextReset(id, before.window, seenAt),
                            windowId = window.id,
                        )
                }
            }
            lastSeen[id] = Seen(window, state.snapshot?.fetchedAt)
        }
        previous = accounts
        lastLiveResets = live
        return justReset.toSet()
    }

    /**
     * True when the next reset card was counting down to [window] until it reset. The reset
     * happened at its reset time, or earlier when the data that shows it was fetched earlier
     * ([seenAt]): providers sometimes reset early.
     */
    private fun wasNextReset(accountId: String, window: QuotaWindow, seenAt: Instant?): Boolean {
        val resetsAt = window.resetsAt ?: return false
        val resetAt = if (seenAt != null && seenAt < resetsAt) seenAt else resetsAt
        return NextReset.find(previous, resetAt - 1.milliseconds)?.account?.id == accountId
    }

    private fun hasReset(before: QuotaWindow, after: QuotaWindow): Boolean {
        val dropped = before.usedPercent - after.usedPercent >= MIN_DROP_POINTS
        val oldReset = before.resetsAt
        val newReset = after.resetsAt
        val moved = oldReset != null && newReset != null && (newReset - oldReset) >= MIN_RESET_SHIFT
        return dropped || moved
    }

    /** The primary window as last seen, and when its data was fetched. */
    private data class Seen(val window: QuotaWindow, val fetchedAt: Instant?)

    private companion object {
        const val MIN_DROP_POINTS = 5.0
        val MIN_RESET_SHIFT: Duration = 5.days
    }
}
