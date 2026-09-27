package dev.sebastiano.headroom.ui.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.sebastiano.headroom.appdata.DemoModeQuotaRepository
import dev.sebastiano.headroom.appdata.ResetHistory
import dev.sebastiano.headroom.model.AccountState
import dev.sebastiano.headroom.model.AlertPreferences
import dev.sebastiano.headroom.model.QuotaRepository
import dev.sebastiano.headroom.model.ResetPolicy
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * State for the overview, the resets tab and the detail pane. It reads everything from the
 * [repository] and the [alertPreferences], and never talks to the network itself: [refresh] asks
 * the repository, which owns the sync.
 *
 * [tickInterval] re-reads the [clock] so countdowns stay current; null turns ticking off (tests).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val repository: QuotaRepository,
    private val alertPreferences: AlertPreferences,
    private val clock: () -> Instant,
    private val isDemo: StateFlow<Boolean>,
    /** True once the stored accounts have been read; see [DemoModeQuotaRepository.isLoaded]. */
    private val accountsLoaded: StateFlow<Boolean>,
    resetHistory: ResetHistory,
    tickInterval: Duration? = Duration.ofMinutes(1),
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val refreshing = MutableStateFlow(false)
    /** Saved, so the detail pane shows the same account after the process is recreated. */
    private val selectedId = savedStateHandle.getStateFlow<String?>(SELECTED_ACCOUNT_KEY, null)

    private val ticks: Flow<Instant> =
        if (tickInterval == null) {
            flowOf(Unit).map { clock() }
        } else {
            flow {
                while (true) {
                    emit(clock())
                    delay(tickInterval.toMillis())
                }
            }
        }

    private val alerts: Flow<AlertSwitches> =
        repository.accounts.flatMapLatest { accounts ->
            val switches = accounts.flatMap { state ->
                state.snapshot?.windows.orEmpty().filter(ResetPolicy::canAlert).map { window ->
                    alertPreferences.isEnabled(state.account.id, window).map { enabled ->
                        (state.account.id to window.id) to enabled
                    }
                }
            }
            if (switches.isEmpty()) flowOf(emptyMap()) else combine(switches) { it.toMap() }
        }

    private val pastResets: Flow<Map<String, List<Double>>> =
        repository.accounts.flatMapLatest { accounts ->
            val histories = accounts.mapNotNull { state ->
                state.primaryWindow?.let { window ->
                    resetHistory.usedAtReset(state.account.id, window.id).map {
                        state.account.id to it
                    }
                }
            }
            if (histories.isEmpty()) flowOf(emptyMap()) else combine(histories) { it.toMap() }
        }

    private val resetTracker = ResetTracker()

    /** The accounts, with the ids of those whose weekly window reset while the app was open. */
    private val accountsWithResets: Flow<Pair<List<AccountState>, Set<String>>> =
        repository.accounts.map { it to resetTracker.update(it) }

    private val environment =
        combine(isDemo, accountsLoaded, refreshing, ticks) { demo, loaded, busy, now ->
            Environment(demo, loaded, busy, now)
        }

    val state: StateFlow<HomeUiState> =
        combine(accountsWithResets, alerts, pastResets, environment) {
                (accounts, justReset),
                switches,
                history,
                env ->
                homeUiState(
                    accounts = accounts,
                    now = env.now,
                    alerts = switches,
                    pastResets = history,
                    isDemo = env.isDemo,
                    accountsLoaded = env.accountsLoaded,
                    isRefreshing = env.isRefreshing,
                    justReset = justReset,
                )
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), initialState())

    /** The selected account's detail. Without a selection it shows the first account. */
    val detail: StateFlow<DetailUiState?> =
        combine(state, selectedId) { home, id ->
                val account =
                    home.accounts.firstOrNull { it.id == id } ?: home.accounts.firstOrNull()
                account?.let { home.now to it }
            }
            .distinctUntilChanged()
            .flatMapLatest { selection ->
                if (selection == null) return@flatMapLatest flowOf(null)
                val (now, account) = selection
                val window = repository.accounts.value.primaryWindowOf(account.id)
                if (window == null) {
                    flowOf(DetailUiState(now, account, chart = null))
                } else {
                    repository.history(account.id, window.id).map { points ->
                        DetailUiState(now, account, chartSummary(window, points, now))
                    }
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), null)

    fun select(accountId: String) {
        savedStateHandle[SELECTED_ACCOUNT_KEY] = accountId
    }

    fun refresh() {
        if (refreshing.value) return
        refreshing.value = true
        viewModelScope.launch {
            try {
                repository.refresh()
            } finally {
                refreshing.value = false
            }
        }
    }

    fun setAlert(accountId: String, windowId: String, enabled: Boolean) {
        viewModelScope.launch { alertPreferences.setEnabled(accountId, windowId, enabled) }
    }

    private fun initialState(): HomeUiState =
        homeUiState(
            accounts = repository.accounts.value,
            now = clock(),
            alerts = emptyMap(),
            pastResets = emptyMap(),
            isDemo = isDemo.value,
            isRefreshing = false,
            accountsLoaded = accountsLoaded.value,
        )

    private fun List<AccountState>.primaryWindowOf(accountId: String) = firstOrNull {
        it.account.id == accountId
    }
        ?.primaryWindow

    private data class Environment(
        val isDemo: Boolean,
        val accountsLoaded: Boolean,
        val isRefreshing: Boolean,
        val now: Instant,
    )

    private companion object {
        const val STOP_TIMEOUT = 5_000L
        const val SELECTED_ACCOUNT_KEY = "selectedAccountId"
    }
}
