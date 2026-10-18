package dev.sebastiano.headroom.ui.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.sebastiano.headroom.appdata.DemoModeQuotaRepository
import dev.sebastiano.headroom.appdata.RepositoryUsageHistory
import dev.sebastiano.headroom.appdata.ResetHistory
import dev.sebastiano.headroom.appdata.UsageHistory
import dev.sebastiano.headroom.model.AccountState
import dev.sebastiano.headroom.model.AlertPreferences
import dev.sebastiano.headroom.model.OverviewSort
import dev.sebastiano.headroom.model.PromptDismissals
import dev.sebastiano.headroom.model.QuotaDisplay
import dev.sebastiano.headroom.model.QuotaRepository
import dev.sebastiano.headroom.model.ResetBurst
import dev.sebastiano.headroom.model.ResetPolicy
import dev.sebastiano.headroom.model.ResetTracker
import dev.sebastiano.headroom.ui.detail.buildUsageHistoryModel
import dev.sebastiano.headroom.ui.detail.isChartableHistoryWindow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
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
import kotlinx.coroutines.flow.update
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
    /** The prompts the user dismissed, so a dismissed Cursor prompt does not come back. */
    private val promptDismissals: PromptDismissals = PromptDismissals.None,
    /** True once the stored accounts have been read; see [DemoModeQuotaRepository.isLoaded]. */
    private val accountsLoaded: StateFlow<Boolean>,
    resetHistory: ResetHistory,
    tickInterval: Duration? = 1.minutes,
    private val savedStateHandle: SavedStateHandle,
    /** Whether the screens show how much of each limit is used or how much is left. */
    quotaDisplay: Flow<QuotaDisplay> = flowOf(QuotaDisplay.Used),
    /** How the overview orders the account cards. */
    overviewSort: Flow<OverviewSort> = flowOf(OverviewSort.YourOrder),
    /** Stores a new overview sort; [overviewSort] then emits it. */
    private val saveOverviewSort: suspend (OverviewSort) -> Unit = {},
    /** Recorded or demo usage history for each long window of the selected account. */
    usageHistory: UsageHistory = RepositoryUsageHistory(repository),
    /** Whether long-term history (1 year) is enabled in settings. */
    keepLongHistory: Flow<Boolean> = flowOf(true),
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
                    delay(tickInterval.inWholeMilliseconds)
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

    private val pastResets: Flow<ResetHistories> =
        repository.accounts.flatMapLatest { accounts ->
            // The Resets tab charts every window that can alert; the primary one always comes too.
            val histories = accounts.flatMap { state ->
                val windows =
                    state.snapshot?.windows.orEmpty().filter(ResetPolicy::canAlert) +
                        listOfNotNull(state.primaryWindow)
                windows
                    .distinctBy { it.id }
                    .map { window ->
                        resetHistory.usedAtReset(state.account.id, window.id).map {
                            (state.account.id to window.id) to it
                        }
                    }
            }
            if (histories.isEmpty()) flowOf(emptyMap()) else combine(histories) { it.toMap() }
        }

    private val resetTracker = ResetTracker(sessionStart = clock())

    /** Resets seen live that still wait for their confetti; see [onResetBurstShown]. */
    private val resetBursts = MutableStateFlow<List<ResetBurst>>(emptyList())

    /** The accounts, with the ids of those whose weekly window reset while the app was open. */
    private val accountsWithResets: Flow<Pair<List<AccountState>, Set<String>>> =
        repository.accounts.map { accounts ->
            val justReset = resetTracker.update(accounts)
            val live = resetTracker.lastLiveResets
            if (live.isNotEmpty()) resetBursts.update { it + live }
            accounts to justReset
        }

    private val choices: Flow<Pair<QuotaDisplay, OverviewSort>> =
        combine(quotaDisplay, overviewSort) { display, sort -> display to sort }

    /** The accounts whose Cursor prompt the user dismissed, as it changes. */
    private val cursorDismissed: Flow<Set<String>> =
        repository.accounts.flatMapLatest { accounts ->
            val dismissals = accounts.map { state ->
                promptDismissals.cursorDismissed(state.account.id).map { state.account.id to it }
            }
            if (dismissals.isEmpty()) flowOf(emptySet())
            else
                combine(dismissals) { pairs -> pairs.filter { it.second }.map { it.first }.toSet() }
        }

    private val environment =
        combine(
            isDemo,
            accountsLoaded,
            refreshing,
            ticks,
            combine(choices, cursorDismissed) { a, b -> a to b },
        ) { demo, loaded, busy, now, (choice, dismissed) ->
            Environment(demo, loaded, busy, now, choice.first, choice.second, dismissed)
        }

    val state: StateFlow<HomeUiState> =
        combine(accountsWithResets, alerts, pastResets, environment, resetBursts) {
                (accounts, justReset),
                switches,
                history,
                env,
                bursts ->
                homeUiState(
                        accounts = accounts,
                        now = env.now,
                        alerts = switches,
                        pastResets = history,
                        isDemo = env.isDemo,
                        accountsLoaded = env.accountsLoaded,
                        isRefreshing = env.isRefreshing,
                        justReset = justReset,
                        cursorDismissed = env.cursorDismissed,
                    )
                    .let { home ->
                        home.copy(
                            display = env.display,
                            accounts = home.accounts.sortedFor(env.sort),
                            overviewSort = env.sort,
                            accountsInYourOrder = home.accounts,
                            resetBursts = bursts,
                        )
                    }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), initialState())

    /** Which long windows the user toggled off per account id. Empty means all are shown. */
    private val hiddenChartWindows = MutableStateFlow<Map<String, Set<String>>>(emptyMap())

    private val selectedAccountWindows: Flow<AccountWindowsSelection?> =
        combine(
                repository.accounts,
                selectedId,
                state.map { home -> home.accounts.map { it.id } }.distinctUntilChanged(),
            ) { accounts, id, orderedIds ->
                val targetId = id ?: orderedIds.firstOrNull()
                val accountState =
                    accounts.firstOrNull { it.account.id == targetId } ?: accounts.firstOrNull()
                accountState?.let {
                    val primaryId = it.primaryWindow?.id
                    val options =
                        it.snapshot
                            ?.windows
                            .orEmpty()
                            .filter { w -> w.isChartableHistoryWindow }
                            .sortedByDescending { w -> w.id == primaryId }
                    AccountWindowsSelection(
                        accountId = it.account.id,
                        primaryWindowId = primaryId,
                        options = options,
                    )
                }
            }
            .distinctUntilChanged()

    private val selectedAccountHistories:
        Flow<
            Pair<
                AccountWindowsSelection?,
                Map<String, List<dev.sebastiano.headroom.model.UsagePoint>>,
            >
        > =
        selectedAccountWindows.flatMapLatest { selection ->
            if (selection == null || selection.options.isEmpty()) {
                flowOf(selection to emptyMap())
            } else {
                combine(
                    selection.options.map { window ->
                        usageHistory.points(selection.accountId, window).map { window.id to it }
                    }
                ) { pairs ->
                    selection to pairs.toMap()
                }
            }
        }

    /** The selected account's detail. Without a selection it shows the first account. */
    val detail: StateFlow<DetailUiState?> =
        combine(state, hiddenChartWindows, keepLongHistory, selectedAccountHistories) {
                home,
                hiddenByAccount,
                longHistory,
                (selection, histories) ->
                if (selection == null) return@combine null
                val account =
                    home.accounts.firstOrNull { it.id == selection.accountId }
                        ?: home.accounts.firstOrNull()
                        ?: return@combine null
                val options = selection.options
                val chartWindows = options.mapNotNull { option ->
                    account.windows.firstOrNull { it.id == option.id }
                }
                if (options.isEmpty()) {
                    DetailUiState(home.now, account, chart = null, display = home.display)
                } else {
                    val allIds = options.map { it.id }.toSet()
                    val hiddenIds = hiddenByAccount[account.id].orEmpty()
                    val selectedIds = (allIds - hiddenIds).ifEmpty { setOf(options.first().id) }
                    val historyModel =
                        buildUsageHistoryModel(
                            accountId = account.id,
                            windows = options,
                            primaryWindowId = selection.primaryWindowId,
                            histories = histories,
                            selectedWindowIds = selectedIds,
                            now = account.asOf(home.now),
                            keepLongHistory = longHistory,
                            dataFrom = account.dataFrom,
                        )
                    val primaryShown = historyModel?.primaryShownWindow
                    DetailUiState(
                        now = home.now,
                        account = account,
                        chart = primaryShown?.summary,
                        chartWindows = chartWindows,
                        chartWindowId = primaryShown?.id,
                        display = home.display,
                        history = historyModel,
                    )
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), null)

    /**
     * Toggles [windowId] on or off in the selected account's usage history chart, keeping at least
     * one long window selected.
     */
    fun toggleChartWindow(windowId: String) {
        val current = detail.value ?: return
        val accountId = current.account.id
        val allIds = current.history?.windows?.map { it.id }?.toSet() ?: return
        if (windowId !in allIds) return
        hiddenChartWindows.update { byAccount ->
            val currentHidden = byAccount[accountId].orEmpty().intersect(allIds)
            val currentSelected = (allIds - currentHidden).ifEmpty { setOf(allIds.first()) }
            val nextHidden =
                if (windowId in currentSelected) {
                    if (currentSelected.size <= 1) currentHidden else currentHidden + windowId
                } else {
                    currentHidden - windowId
                }
            byAccount + (accountId to nextHidden)
        }
    }

    /** Alias for [toggleChartWindow]. */
    fun selectChartWindow(windowId: String) {
        toggleChartWindow(windowId)
    }

    fun select(accountId: String) {
        savedStateHandle[SELECTED_ACCOUNT_KEY] = accountId
    }

    /**
     * Orders the overview's cards by [sort]. Without a selection the detail shows the top card, so
     * the account it shows now is selected first: sorting must not swap the detail for another.
     */
    fun setOverviewSort(sort: OverviewSort) {
        if (selectedId.value == null) {
            state.value.accounts.firstOrNull()?.let { select(it.id) }
        }
        viewModelScope.launch { saveOverviewSort(sort) }
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

    /**
     * The confetti for [accountId]'s reset has played, or was skipped: it is not asked for again.
     */
    fun onResetBurstShown(accountId: String) {
        resetBursts.update { bursts -> bursts.filterNot { it.accountId == accountId } }
    }

    fun setAlert(accountId: String, windowId: String, enabled: Boolean) {
        viewModelScope.launch { alertPreferences.setEnabled(accountId, windowId, enabled) }
    }

    /** The user does not want to connect Cursor to [accountId]; the prompt does not come back. */
    fun dismissCursorPrompt(accountId: String) {
        viewModelScope.launch { promptDismissals.dismissCursor(accountId) }
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
            .copy(sortLoaded = false)

    private data class Environment(
        val isDemo: Boolean,
        val accountsLoaded: Boolean,
        val isRefreshing: Boolean,
        val now: Instant,
        val display: QuotaDisplay,
        val sort: OverviewSort,
        val cursorDismissed: Set<String> = emptySet(),
    )

    private data class AccountWindowsSelection(
        val accountId: String,
        val primaryWindowId: String?,
        val options: List<dev.sebastiano.headroom.model.QuotaWindow>,
    )

    private companion object {
        const val STOP_TIMEOUT = 5_000L
        const val SELECTED_ACCOUNT_KEY = "selectedAccountId"
    }
}
