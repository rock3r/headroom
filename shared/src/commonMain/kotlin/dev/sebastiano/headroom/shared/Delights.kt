package dev.sebastiano.headroom.shared

import dev.sebastiano.headroom.model.AccountState
import dev.sebastiano.headroom.model.FreshDataTrigger
import dev.sebastiano.headroom.model.ResetTracker
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/** A moment the app celebrates, as the Android delights. */
public sealed interface DelightUi {
    /** A refresh brought new numbers: the refresh shimmer. */
    public data object Shimmer : DelightUi

    /**
     * A weekly limit of [accountId] reset while the app was open: the reset confetti. It bursts
     * from the next reset card when [fromNextReset], otherwise from the account's card.
     */
    public data class Burst(val accountId: String, val fromNextReset: Boolean) : DelightUi
}

/** The accounts as the overview shows them, with the ones that reset during this session. */
internal data class TrackedAccounts(val accounts: List<AccountState>, val justReset: Set<String>)

/**
 * Follows the accounts once for the whole app, as the Android home screen does with its
 * [ResetTracker] and [FreshDataTrigger], so every screen agrees on what just reset and each delight
 * plays once.
 */
internal class Delights(
    accounts: Flow<List<AccountState>>,
    sessionStart: Instant,
    scope: CoroutineScope,
) {
    private val tracker = ResetTracker(sessionStart)
    private val fresh = FreshDataTrigger()
    private val state = MutableStateFlow<TrackedAccounts?>(null)
    private val moments = MutableSharedFlow<DelightUi>(extraBufferCapacity = BUFFER)

    val tracked: Flow<TrackedAccounts> = state.filterNotNull()
    val events: SharedFlow<DelightUi> = moments

    init {
        scope.launch {
            accounts.collect { list ->
                val justReset = tracker.update(list)
                tracker.lastLiveResets.forEach {
                    moments.tryEmit(DelightUi.Burst(it.accountId, it.fromNextReset))
                }
                val refreshing = list.any { it.isRefreshing }
                val syncedAt = list.mapNotNull { it.snapshot?.fetchedAt }.maxOrNull()
                if (fresh.update(refreshing, syncedAt)) moments.tryEmit(DelightUi.Shimmer)
                state.value = TrackedAccounts(list, justReset)
            }
        }
    }

    private companion object {
        const val BUFFER = 16
    }
}
