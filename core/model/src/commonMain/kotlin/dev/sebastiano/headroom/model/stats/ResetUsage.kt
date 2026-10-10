package dev.sebastiano.headroom.model.stats

import androidx.compose.runtime.Immutable
import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.ResetEvent
import dev.sebastiano.headroom.model.ResetEventKind
import dev.sebastiano.headroom.model.ResetUseSource
import dev.sebastiano.headroom.model.WindowKind
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/** The spans the reset stat can cover. The longest is as long as the reset history is kept. */
public enum class ResetPeriod(public val length: Duration) {
    FourWeeks(FOUR_WEEKS_DAYS.days),
    ThreeMonths(THREE_MONTHS_DAYS.days),
    TwelveMonths(TWELVE_MONTHS_DAYS.days),
}

private const val FOUR_WEEKS_DAYS = 28L
private const val THREE_MONTHS_DAYS = 91L
private const val TWELVE_MONTHS_DAYS = 365L

/** How the usage-limit resets were spent, for each [ResetPeriod]. */
@Immutable
public data class ResetUsageStats(val periods: Map<ResetPeriod, ResetUsage>) {
    public fun of(period: ResetPeriod): ResetUsage = periods.getValue(period)
}

/** The resets used and expired in one period, in total and per provider. */
@Immutable
public data class ResetUsage(
    val used: Int,
    val usedInHeadroom: Int,
    val expired: Int,
    val givenBack: GivenBack,
    /** The providers with at least one event, the most resets first. */
    val providers: List<ProviderResetUsage>,
) {
    val usedElsewhere: Int
        get() = used - usedInHeadroom
}

@Immutable
public data class ProviderResetUsage(
    val provider: Provider,
    val used: Int,
    val usedInHeadroom: Int,
    val expired: Int,
    val givenBack: GivenBack,
)

/**
 * The used percent the resets gave back, added up per kind of limit, the longest kind first: 150
 * for [WindowKind.Weekly] is one and a half weekly limits. [estimated] is true when part of it
 * comes from usage that may have been out of date.
 */
@Immutable
public data class GivenBack(val byKind: Map<WindowKind, Double>, val estimated: Boolean) {
    val isEmpty: Boolean
        get() = byKind.isEmpty()
}

/** The order given-back limits are listed in: the longest first. */
private val GIVEN_BACK_ORDER =
    listOf(WindowKind.Monthly, WindowKind.Weekly, WindowKind.Daily, WindowKind.Session)

/**
 * The reset stat from the reset history. It is null, so the card is left out, when there is no
 * event and no account has resets ([hasResets]).
 */
public fun resetUsage(
    events: List<ResetEvent>,
    now: Instant,
    hasResets: Boolean,
): ResetUsageStats? {
    if (events.isEmpty() && !hasResets) return null
    return ResetUsageStats(
        ResetPeriod.entries.associateWith { period ->
            val start = now.minus(period.length)
            usage(events.filter { it.at >= start })
        }
    )
}

private fun usage(events: List<ResetEvent>): ResetUsage {
    val providers =
        events
            .groupBy { it.provider }
            .map { (provider, own) ->
                ProviderResetUsage(
                    provider = provider,
                    used = own.usedCount(),
                    usedInHeadroom = own.usedInHeadroomCount(),
                    expired = own.count { it.kind == ResetEventKind.Expired },
                    givenBack = own.givenBack(),
                )
            }
            .sortedByDescending { it.used + it.expired }
    return ResetUsage(
        used = events.usedCount(),
        usedInHeadroom = events.usedInHeadroomCount(),
        expired = events.count { it.kind == ResetEventKind.Expired },
        givenBack = events.givenBack(),
        providers = providers,
    )
}

private fun List<ResetEvent>.usedCount() = count { it.kind == ResetEventKind.Used }

private fun List<ResetEvent>.usedInHeadroomCount() = count {
    it.kind == ResetEventKind.Used && it.source == ResetUseSource.Headroom
}

private fun List<ResetEvent>.givenBack(): GivenBack {
    val measured = filter { it.kind == ResetEventKind.Used && it.givenBack.isNotEmpty() }
    val sums = GIVEN_BACK_ORDER.associateWith { kind ->
        measured.sumOf { it.givenBack[kind] ?: 0.0 }
    }
    val present = GIVEN_BACK_ORDER.filter { kind -> measured.any { kind in it.givenBack } }
    return GivenBack(
        byKind = present.associateWith { sums.getValue(it) },
        estimated = measured.any { it.givenBackEstimated },
    )
}
