package dev.sebastiano.headroom.widget.testing

import dev.sebastiano.headroom.model.AccountState
import dev.sebastiano.headroom.model.ResetAvailability
import dev.sebastiano.headroom.model.ResetPool
import dev.sebastiano.headroom.model.ResetPoolStatus
import dev.sebastiano.headroom.model.ResetScope

/** Two Codex resets that can be used now. */
internal val TwoCodexResets: ResetAvailability =
    ResetAvailability(listOf(ResetPool("codex", "Resets", available = 2, ResetScope.Unknown)))

/** Gives the demo account [accountId] these [resets] in its snapshot. */
internal fun List<AccountState>.withResets(
    accountId: String = "demo-codex",
    resets: ResetAvailability = TwoCodexResets,
): List<AccountState> = map { state ->
    if (state.account.id == accountId) {
        state.copy(snapshot = state.snapshot?.copy(resets = resets))
    } else {
        state
    }
}

/** A pool of [available] resets in [status]. */
internal fun pool(available: Int, status: ResetPoolStatus): ResetPool =
    ResetPool("pool-$status", "Resets", available, ResetScope.Unknown, status = status)
