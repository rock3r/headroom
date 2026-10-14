package dev.sebastiano.headroom.shared

import dev.sebastiano.headroom.model.ChartSummary
import dev.sebastiano.headroom.model.UsagePoint
import dev.sebastiano.headroom.model.WindowKind
import dev.sebastiano.headroom.model.stats.GivenBack
import dev.sebastiano.headroom.model.stats.ResetPeriod
import dev.sebastiano.headroom.model.stats.ResetUsage
import dev.sebastiano.headroom.model.stats.StatAccount
import dev.sebastiano.headroom.model.stats.Stats
import dev.sebastiano.headroom.model.stats.persona
import kotlinx.datetime.isoDayNumber

/** One sample of a usage history. */
public data class PointUi(val atEpochSeconds: Long, val usedPercent: Double)

/** The detail screen's pace chart for one window. */
public data class ChartUi(
    val windowId: String,
    val startEpochSeconds: Long,
    val endEpochSeconds: Long,
    val usedPercent: Double,
    /** Where usage would be now at even pace. */
    val expectedPercent: Double,
    /** `weekly`, `monthly` or another window kind: it decides the chart's title. */
    val kind: String,
    /** The usage inside the current window, oldest first. */
    val points: List<PointUi>,
    /** When the window reaches 100% at the current rate, or null when that is after the reset. */
    val projectedLimitAtEpochSeconds: Long?,
    /** Where the window ends at the current rate, when it does not reach the limit. */
    val projectedEndPercent: Double?,
)

/** Everything the Stats tab shows. A null or empty stat has not enough history yet. */
public data class StatsUi(
    val isDemo: Boolean,
    /** Whole days of history the stats are based on. */
    val coverageDays: Int?,
    val resetScore: ResetScoreUi?,
    val shares: List<ShareUi>,
    val heatmap: HeatmapUi?,
    val closestCall: PastResetUi?,
    val biggestDay: BiggestDayUi?,
    val leftOver: LeftOverUi?,
    val sparklines: List<SparklineUi>,
    /** How the usage-limit resets were spent; null when no account has resets. */
    val resetUsage: List<ResetUsageUi>?,
)

/** The account and the limit a stat is about. */
public data class StatAccountUi(
    val id: String,
    val providerId: String,
    val name: String,
    val kind: String,
)

/** How many resets came without hitting the limit. */
public data class ResetScoreUi(
    val total: Int,
    val clean: Int,
    val streak: Int,
    /** Every reset, oldest first: true when it hit the limit. */
    val timeline: List<Boolean>,
)

public data class ShareUi(
    val providerId: String,
    val providerName: String,
    val points: Double,
    val fraction: Double,
)

/** Percentage points burned in each hour of the week, Monday 00:00 to Sunday 23:00: 168 cells. */
public data class HeatmapUi(
    val cells: List<Double>,
    /** 1 for Monday to 7 for Sunday, as ISO 8601 numbers the days. */
    val busiestDay: Int,
    val busiestHour: Int,
    /** `earlyBird`, `nineToFive`, `eveningHacker`, `nightOwl` or `weekendWarrior`. */
    val persona: String,
)

public data class PastResetUi(
    val account: StatAccountUi,
    val peak: Double,
    val peakAtEpochSeconds: Long,
)

public data class BiggestDayUi(
    val account: StatAccountUi,
    val points: Double,
    /** The local day, as `yyyy-MM-dd`. */
    val date: String,
)

public data class LeftOverUi(
    val averageLeft: Double,
    val resets: Int,
    val accounts: List<AccountLeftOverUi>,
)

public data class AccountLeftOverUi(
    val account: StatAccountUi,
    val averageLeft: Double,
    val resets: Int,
)

public data class SparklineUi(
    val account: StatAccountUi,
    val points: List<PointUi>,
    val startEpochSeconds: Long,
    val endEpochSeconds: Long,
    val current: Double,
)

/** The resets used and expired in one period. */
public data class ResetUsageUi(
    /** `fourWeeks`, `threeMonths` or `twelveMonths`. */
    val period: String,
    val used: Int,
    val usedInHeadroom: Int,
    val usedElsewhere: Int,
    val expired: Int,
    val givenBack: GivenBackUi,
    val providers: List<ProviderResetUsageUi>,
)

public data class ProviderResetUsageUi(
    val providerId: String,
    val providerName: String,
    val used: Int,
    val usedInHeadroom: Int,
    val expired: Int,
    val givenBack: GivenBackUi,
)

/** How much the resets gave back, the longest kind of limit first: 1.5 weekly limits. */
public data class GivenBackUi(val limits: List<GivenBackLimitUi>, val estimated: Boolean)

/** [times] whole limits of [kind] given back. */
public data class GivenBackLimitUi(val kind: String, val times: Double)

internal object StatsMapping {
    private const val PERCENT = 100.0

    fun chart(windowId: String, summary: ChartSummary): ChartUi =
        ChartUi(
            windowId = windowId,
            startEpochSeconds = summary.start.epochSeconds,
            endEpochSeconds = summary.end.epochSeconds,
            usedPercent = summary.usedPercent,
            expectedPercent = summary.expectedPercent,
            kind = summary.kind.name.lowercase(),
            points = summary.points.map(::point),
            projectedLimitAtEpochSeconds = summary.projectedLimitAt?.epochSeconds,
            projectedEndPercent = summary.projectedEndPercent,
        )

    fun stats(stats: Stats, isDemo: Boolean): StatsUi =
        StatsUi(
            isDemo = isDemo,
            coverageDays = stats.coverage?.days,
            resetScore =
                stats.resets?.let { ResetScoreUi(it.total, it.clean, it.streak, it.timeline) },
            shares =
                stats.shares.map {
                    ShareUi(it.provider.id, it.provider.displayName, it.points, it.fraction)
                },
            heatmap =
                stats.heatmap?.let { map ->
                    val (day, hour) = map.busiest
                    HeatmapUi(map.cells, day.isoDayNumber, hour, persona(map).id())
                },
            closestCall =
                stats.closestCall?.let {
                    PastResetUi(account(it.account), it.peak, it.peakAt.epochSeconds)
                },
            biggestDay =
                stats.biggestDay?.let {
                    BiggestDayUi(account(it.account), it.points, it.date.toString())
                },
            leftOver =
                stats.leftOver?.let { left ->
                    LeftOverUi(
                        left.averageLeft,
                        left.resets,
                        left.accounts.map {
                            AccountLeftOverUi(account(it.account), it.averageLeft, it.resets)
                        },
                    )
                },
            sparklines =
                stats.sparklines.map {
                    SparklineUi(
                        account(it.account),
                        it.points.map(::point),
                        it.start.epochSeconds,
                        it.end.epochSeconds,
                        it.current,
                    )
                },
            resetUsage =
                stats.resetUsage?.let { usage ->
                    ResetPeriod.entries.map { period -> resetUsage(period, usage.of(period)) }
                },
        )

    private fun resetUsage(period: ResetPeriod, usage: ResetUsage): ResetUsageUi =
        ResetUsageUi(
            period = period.id(),
            used = usage.used,
            usedInHeadroom = usage.usedInHeadroom,
            usedElsewhere = usage.usedElsewhere,
            expired = usage.expired,
            givenBack = givenBack(usage.givenBack),
            providers =
                usage.providers.map {
                    ProviderResetUsageUi(
                        it.provider.id,
                        it.provider.displayName,
                        it.used,
                        it.usedInHeadroom,
                        it.expired,
                        givenBack(it.givenBack),
                    )
                },
        )

    private fun givenBack(givenBack: GivenBack): GivenBackUi =
        GivenBackUi(
            limits =
                givenBack.byKind.map { (kind, percent) ->
                    GivenBackLimitUi(kind.label(), percent / PERCENT)
                },
            estimated = givenBack.estimated,
        )

    private fun WindowKind.label(): String = name.lowercase()

    private fun account(account: StatAccount): StatAccountUi =
        StatAccountUi(account.id, account.provider.id, account.name, account.kind.label())

    private fun point(point: UsagePoint): PointUi =
        PointUi(point.at.epochSeconds, point.usedPercent)
}
