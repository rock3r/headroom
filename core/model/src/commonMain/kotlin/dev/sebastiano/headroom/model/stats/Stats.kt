package dev.sebastiano.headroom.model.stats

import androidx.compose.runtime.Immutable
import dev.sebastiano.headroom.model.Account
import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.QuotaWindow
import dev.sebastiano.headroom.model.UsagePoint
import dev.sebastiano.headroom.model.WindowKind
import kotlin.time.Instant
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber

/**
 * One account's main limit (see [dev.sebastiano.headroom.model.AccountState.primaryWindow]) and its
 * recorded usage, oldest point first. Every stat is made of these.
 */
public data class StatsSource(
    val account: Account,
    val window: QuotaWindow,
    val points: List<UsagePoint>,
)

/** Everything the Stats tab shows. A null or empty stat does not have enough history yet. */
@Immutable
public data class Stats(
    val coverage: Coverage? = null,
    val resets: ResetScore? = null,
    val shares: List<ProviderShare> = emptyList(),
    val heatmap: BurnHeatmap? = null,
    /** The highest usage before a reset that did not hit the limit. */
    val closestCall: PastReset? = null,
    val leftOver: LeftOver? = null,
    val biggestDay: BiggestDay? = null,
    val sparklines: List<Sparkline> = emptyList(),
    /** How the usage-limit resets were spent. Null when no account has resets. */
    val resetUsage: ResetUsageStats? = null,
)

/** How much history the stats are based on: whole days since the oldest point. */
@Immutable public data class Coverage(val days: Int)

/** The account and the limit a stat is about. */
@Immutable
public data class StatAccount(
    val id: String,
    val provider: Provider,
    /** The name the user gave the account, or the provider's name. */
    val name: String,
    val kind: WindowKind,
)

/** One reset seen in the history: the highest usage before it, and when that was reached. */
@Immutable
public data class PastReset(val account: StatAccount, val peak: Double, val peakAt: Instant) {
    val hitLimit: Boolean
        get() = peak >= LIMIT
}

/** How many resets came without hitting the limit. */
@Immutable
public data class ResetScore(
    val total: Int,
    val clean: Int,
    /** Resets in a row, up to the latest, that did not hit the limit. */
    val streak: Int,
    /** Every reset, oldest first: true when it hit the limit. */
    val timeline: List<Boolean>,
) {
    val hits: Int
        get() = total - clean
}

/** One provider's share of all the quota burned, in percentage points of each main limit. */
@Immutable
public data class ProviderShare(val provider: Provider, val points: Double, val fraction: Double)

/**
 * Percentage points burned in each hour of the week, in the user's zone, from Monday 00:00 to
 * Sunday 23:00: [DAYS] × [HOURS] cells.
 */
@Immutable
public data class BurnHeatmap(val cells: List<Double>) {
    public fun at(day: DayOfWeek, hour: Int): Double = cells[(day.isoDayNumber - 1) * HOURS + hour]

    public fun dayTotal(day: DayOfWeek): Double = (0 until HOURS).sumOf { at(day, it) }

    val total: Double
        get() = cells.sum()

    val max: Double
        get() = cells.maxOrNull() ?: 0.0

    /** The busiest hour of the week, as its day and its hour. The earliest wins a tie. */
    val busiest: Pair<DayOfWeek, Int>
        get() {
            val index = cells.indices.maxBy { cells[it] }
            return DayOfWeek(index / HOURS + 1) to index % HOURS
        }

    /** The day with the most use. The earliest wins a tie. */
    val busiestDay: DayOfWeek
        get() = DayOfWeek.entries.maxBy { dayTotal(it) }

    public companion object {
        public const val DAYS: Int = 7
        public const val HOURS: Int = 24
    }
}

/** How much was left, on average, when the limits reset. */
@Immutable
public data class LeftOver(
    /** Percent of the limit left unused, averaged over every reset. */
    val averageLeft: Double,
    val resets: Int,
    /** Each account's own average, the most left unused first. */
    val accounts: List<AccountLeftOver>,
)

@Immutable
public data class AccountLeftOver(
    val account: StatAccount,
    val averageLeft: Double,
    val resets: Int,
)

/** The most of one limit used in a single local day. */
@Immutable
public data class BiggestDay(val account: StatAccount, val points: Double, val date: LocalDate)

/** The usage of one account's main limit over a recent span, for a small line chart. */
@Immutable
public data class Sparkline(
    val account: StatAccount,
    /** The points inside the span, oldest first. Fewer than two cannot draw a line. */
    val points: List<UsagePoint>,
    val start: Instant,
    val end: Instant,
    /** The usage now, from the latest sync. */
    val current: Double,
)

/** Usage at or above this hit the limit. */
public const val LIMIT: Double = 100.0
