package dev.sebastiano.headroom.shared

import dev.sebastiano.headroom.model.AccountState
import dev.sebastiano.headroom.model.OverviewSort
import kotlin.time.Instant

/**
 * The accounts in the order the overview shows them for [sort], as the Android overview sorts them:
 * by the primary window's used percent or reset time, accounts without one last in both directions,
 * and ties by name from A to Z, so the order does not jump between syncs.
 */
internal fun List<AccountState>.sortedFor(sort: OverviewSort): List<AccountState> {
    val key: Comparator<AccountState> =
        when (sort) {
            OverviewSort.YourOrder -> return this
            OverviewSort.MostUsedFirst ->
                compareBy(nullsLast(reverseOrder<Double>())) { it.primaryWindow?.usedPercent }
            OverviewSort.LeastUsedFirst ->
                compareBy(nullsLast(naturalOrder<Double>())) { it.primaryWindow?.usedPercent }
            OverviewSort.SoonestResetFirst ->
                compareBy(nullsLast(naturalOrder<Instant>())) { it.primaryWindow?.resetsAt }
            OverviewSort.LatestResetFirst ->
                compareBy(nullsLast(reverseOrder<Instant>())) { it.primaryWindow?.resetsAt }
        }
    return sortedWith(key.thenBy(String.CASE_INSENSITIVE_ORDER) { it.account.name })
}
