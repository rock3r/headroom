package dev.sebastiano.headroom.shared

import dev.sebastiano.headroom.data.account.SignInManager
import dev.sebastiano.headroom.model.AlertPreferences
import dev.sebastiano.headroom.model.chartSummary
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** What the user can do with an account: rename, reorder, sign out, alerts, and its chart. */
@OptIn(ExperimentalCoroutinesApi::class)
public class HeadroomAccounts
internal constructor(
    private val sources: Sources,
    private val signInManager: SignInManager,
    private val alerts: AlertPreferences,
    private val scope: CoroutineScope,
    private val clock: () -> Instant,
) {
    /** Signs the account out: its tokens and its history go. */
    public fun remove(accountId: String) {
        scope.launch { signInManager.signOut(accountId) }
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
        scope.launch { alerts.setEnabled(accountId, windowId, enabled) }
    }

    /**
     * Calls [onChange] with the pace chart of the window, as its history grows, or null when the
     * window has no reset time or length, until [Watch.cancel].
     */
    public fun watchChart(
        accountId: String,
        windowId: String,
        onChange: (ChartUi?) -> Unit,
    ): Watch {
        val chart =
            sources.accounts
                .map { accounts ->
                    accounts
                        .firstOrNull { it.account.id == accountId }
                        ?.snapshot
                        ?.windows
                        ?.firstOrNull { it.id == windowId }
                }
                .distinctUntilChanged()
                .flatMapLatest { window ->
                    if (window == null) flowOf(null)
                    else
                        combine(flowOf(window), sources.history(accountId, window)) { w, points ->
                            chartSummary(w, points, clock())?.let { StatsMapping.chart(w.id, it) }
                        }
                }
        return scope.watch(chart, onChange)
    }
}
