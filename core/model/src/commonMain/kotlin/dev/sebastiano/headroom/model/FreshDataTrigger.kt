package dev.sebastiano.headroom.model

import kotlin.time.Instant

/**
 * Says when a refresh has just brought new data, for the refresh shimmer. That is when the state
 * stops refreshing and the newest fetch time is not the one it had when the refresh started. A
 * refresh that failed for every account leaves the fetch times alone, so it does not count. Data
 * that changes without a refresh, such as stored data loading, does not count either.
 */
public class FreshDataTrigger {
    private var refreshing = false
    private var syncedAtStart: Instant? = null

    /** Records the latest state and returns true when it ends a refresh that brought new data. */
    public fun update(isRefreshing: Boolean, lastSyncedAt: Instant?): Boolean {
        val started = isRefreshing && !refreshing
        val ended = !isRefreshing && refreshing
        refreshing = isRefreshing
        if (started) syncedAtStart = lastSyncedAt
        return ended && lastSyncedAt != null && lastSyncedAt != syncedAtStart
    }
}
