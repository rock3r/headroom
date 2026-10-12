package dev.sebastiano.headroom.shared

import dev.sebastiano.headroom.model.AccountState
import dev.sebastiano.headroom.model.AlertPreferences
import dev.sebastiano.headroom.model.QuotaWindow
import dev.sebastiano.headroom.model.ResetPolicy
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

/** Which reset alerts are on, by account and window id. A window not listed follows its default. */
internal class AlertStates(private val on: Map<Pair<String, String>, Boolean>) {
    fun isOn(accountId: String, window: QuotaWindow): Boolean =
        ResetPolicy.canAlert(window) &&
            (on[accountId to window.id] ?: ResetPolicy.alertsByDefault(window))

    companion object {
        /** Every window as its default: weekly alerts on, monthly off. */
        val Defaults: AlertStates = AlertStates(emptyMap())

        /** The alert switches of every window of [accounts] that can alert, as they change. */
        @OptIn(ExperimentalCoroutinesApi::class)
        fun of(
            accounts: Flow<List<AccountState>>,
            preferences: AlertPreferences,
        ): Flow<AlertStates> = accounts.flatMapLatest { states ->
            val windows = states.flatMap { state ->
                state.snapshot?.windows.orEmpty().filter(ResetPolicy::canAlert).map {
                    state.account.id to it
                }
            }
            if (windows.isEmpty()) {
                flowOf(Defaults)
            } else {
                combine(
                    windows.map { (accountId, window) -> preferences.isEnabled(accountId, window) }
                ) { enabled ->
                    AlertStates(
                        windows.zip(enabled.toList()).associate { (key, on) ->
                            (key.first to key.second.id) to on
                        }
                    )
                }
            }
        }
    }
}
