package dev.sebastiano.headroom.shared

import dev.sebastiano.headroom.data.account.SignInManager
import dev.sebastiano.headroom.model.AlertPreferences
import kotlin.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.io.IOException

/** What the user can do with an account: rename, reorder, sign out, alerts, and its chart. */
@OptIn(ExperimentalCoroutinesApi::class)
public class HeadroomAccounts
internal constructor(
    private val sources: Sources,
    private val signInManager: SignInManager,
    private val alerts: AlertPreferences,
    private val scope: CoroutineScope,
    private val clock: () -> Instant,
    private val compute: CoroutineDispatcher,
) {
    /** Signs the account out: its tokens and its history go. */
    public fun remove(accountId: String) {
        scope.launch {
            try {
                signInManager.signOut(accountId)
            } catch (ignored: IOException) {
                // The keychain refused to forget the sign-in: the account stays, to remove again.
            }
        }
    }

    /** Names the account. A blank or null [nickname] removes the name. */
    public fun rename(accountId: String, nickname: String?) {
        scope.launch { sources.real.renameAccount(accountId, nickname) }
    }

    /** Puts the accounts in the order of [orderedIds], for "your order". */
    public fun reorder(orderedIds: List<String>) {
        scope.launch { sources.real.reorderAccounts(orderedIds) }
    }

    /** Turns the reset alert of a window on or off. */
    public fun setAlert(accountId: String, windowId: String, enabled: Boolean) {
        scope.launch { setAlertAndWait(accountId, windowId, enabled) }
    }

    /** Returns after storage finishes, for notification actions whose handler must await it. */
    @Throws(IOException::class, CancellationException::class)
    public suspend fun setAlertAndWait(accountId: String, windowId: String, enabled: Boolean) {
        alerts.setEnabled(accountId, windowId, enabled)
    }

    /**
     * Calls [onChange] with the usage history chart of the account's weekly and monthly windows, as
     * their history grows, or null when it has none, until [Watch.cancel]. The chart is built off
     * the main thread, and only when the account or a stored history changes.
     */
    public fun watchHistory(accountId: String, onChange: (HistoryChartUi?) -> Unit): Watch {
        val account =
            sources.accounts
                .map { accounts -> accounts.firstOrNull { it.account.id == accountId } }
                .distinctUntilChanged()
        val windows =
            combine(account, sources.isDemo) { state, demo ->
                    state?.let(HistoryMapping::chartableWindows).orEmpty() to demo
                }
                // A sync that only moves usage keeps the windows, so their stored histories stay
                // collected. The demo history is drawn from the window itself, so it follows it.
                .distinctUntilChanged { (old, wasDemo), (new, demo) ->
                    !demo && !wasDemo && old.map { it.id } == new.map { it.id }
                }
        val histories = windows.flatMapLatest { (windows, _) ->
            if (windows.isEmpty()) flowOf(emptyMap())
            else
                combine(
                    windows.map { window ->
                        sources.history(accountId, window).map { window.id to it }
                    }
                ) { pairs ->
                    pairs.toMap()
                }
        }
        val chart =
            combine(account, histories, sources.keepLongHistory) { state, points, keepLong ->
                    state?.let { HistoryMapping.history(it, points, clock(), keepLong) }
                }
                // Comparing whole histories is work too, so it stays off the main thread.
                .distinctUntilChanged()
                .flowOn(compute)
        return scope.watch(chart, onChange)
    }
}
