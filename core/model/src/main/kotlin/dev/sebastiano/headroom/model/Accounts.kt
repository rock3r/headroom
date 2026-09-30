package dev.sebastiano.headroom.model

import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** A signed-in subscription. One provider can have several accounts. */
public data class Account(
    val id: String,
    val provider: Provider,
    /** What the provider calls the account, for example an email address or a login. */
    val label: String,
    /** The name the user gave the account, or null when they did not name it. */
    val nickname: String? = null,
) {
    /** The name to show: the user's name for the account, or the provider's name. */
    val name: String
        get() = nickname ?: provider.displayName
}

/** The latest known state of one account, as shown in the UI and the widgets. */
public data class AccountState(
    val account: Account,
    val snapshot: QuotaSnapshot?,
    val lastError: QuotaErrorKind? = null,
    val isRefreshing: Boolean = false,
) {
    /**
     * The weekly window if there is one, otherwise the longest window. The UI leads with it. Never
     * a credit or an unknown window ([QuotaWindow.isInformational]).
     */
    val primaryWindow: QuotaWindow?
        get() {
            val windows =
                snapshot?.windows.orEmpty().filterNot { it.isUnlimited || it.isInformational }
            return windows.firstOrNull { it.kind == WindowKind.Weekly }
                ?: windows.firstOrNull { it.kind == WindowKind.Monthly }
                ?: windows.maxByOrNull { it.length?.toMillis() ?: 0L }
        }

    /** The session (5-hour and similar) window, when the provider has one. */
    val sessionWindow: QuotaWindow?
        get() =
            snapshot?.windows?.firstOrNull { it.kind == WindowKind.Session && !it.isInformational }
}

/** One point of a window's usage history, for the pace chart. */
public data class UsagePoint(val at: Instant, val usedPercent: Double)

/** The single source of quota data for the app and the widgets. */
public interface QuotaRepository {
    public val accounts: StateFlow<List<AccountState>>

    /** Fetches fresh data for one account, or for all accounts when [accountId] is null. */
    public suspend fun refresh(accountId: String? = null)

    public fun history(accountId: String, windowId: String): Flow<List<UsagePoint>>

    /**
     * The committed state, read directly from storage. Use it right after [refresh] when the result
     * must reflect that refresh: [accounts] may publish it a moment later.
     */
    public suspend fun current(): List<AccountState> = accounts.value
}

/** Per-window reset alert switches. */
public interface AlertPreferences {
    public fun isEnabled(accountId: String, window: QuotaWindow): Flow<Boolean>

    public suspend fun setEnabled(accountId: String, windowId: String, enabled: Boolean)
}

/** The next reset the user cares about: the soonest weekly one, else the soonest of any kind. */
public data class NextReset(val account: Account, val window: QuotaWindow) {
    public companion object {
        public fun find(accounts: List<AccountState>, now: Instant): NextReset? {
            val candidates = accounts.flatMap { state ->
                state.snapshot?.windows.orEmpty().mapNotNull { window ->
                    val resetsAt = window.resetsAt
                    if (resetsAt != null && resetsAt.isAfter(now) && ResetPolicy.canAlert(window)) {
                        Candidate(NextReset(state.account, window), resetsAt)
                    } else {
                        null
                    }
                }
            }
            val soonestWeekly =
                candidates
                    .filter { it.reset.window.kind == WindowKind.Weekly }
                    .minByOrNull { it.at }
            return (soonestWeekly ?: candidates.minByOrNull { it.at })?.reset
        }

        private data class Candidate(val reset: NextReset, val at: Instant)
    }
}
