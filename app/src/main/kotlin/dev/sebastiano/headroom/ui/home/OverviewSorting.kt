package dev.sebastiano.headroom.ui.home

import dev.sebastiano.headroom.model.OverviewSort
import java.time.Instant

/**
 * The accounts in the order the overview shows them for [sort].
 *
 * The quota sorts compare the primary window's used percent, and the reset sorts compare when the
 * primary window resets. The primary window is the one the card leads with, so the order matches
 * the number and the reset time the user reads on each card. Accounts without that value go last in
 * both directions, and ties go by name from A to Z, so the order does not jump between syncs.
 * [OverviewSort.YourOrder] keeps the order the list came in.
 */
internal fun List<AccountSummary>.sortedFor(sort: OverviewSort): List<AccountSummary> {
    val key: Comparator<AccountSummary> =
        when (sort) {
            OverviewSort.YourOrder -> return this
            OverviewSort.MostUsedFirst ->
                compareBy(nullsLast(reverseOrder<Double>())) { it.primary?.usedPercent }
            OverviewSort.LeastUsedFirst ->
                compareBy(nullsLast(naturalOrder<Double>())) { it.primary?.usedPercent }
            OverviewSort.SoonestResetFirst ->
                compareBy(nullsLast(naturalOrder<Instant>())) { it.primary?.resetsAt }
            OverviewSort.LatestResetFirst ->
                compareBy(nullsLast(reverseOrder<Instant>())) { it.primary?.resetsAt }
        }
    return sortedWith(key.then(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }))
}
