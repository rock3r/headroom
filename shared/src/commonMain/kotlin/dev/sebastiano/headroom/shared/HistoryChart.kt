package dev.sebastiano.headroom.shared

import dev.sebastiano.headroom.model.AccountState
import dev.sebastiano.headroom.model.UsagePoint
import dev.sebastiano.headroom.model.chartSummary
import dev.sebastiano.headroom.model.detectResets
import dev.sebastiano.headroom.model.isChartableHistoryWindow
import dev.sebastiano.headroom.model.prepareHistoryPoints
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * The detail screen's usage history chart: every weekly and monthly window of an account on one
 * time axis, as the Android app draws it.
 */
public data class HistoryChartUi(
    /** The oldest stored point, or the earliest current period start when that is older. */
    val minEpochSeconds: Long,
    /** The end of the latest current period. */
    val maxEpochSeconds: Long,
    /** The range presets the history supports, in order: see [HistoryRanges]. */
    val ranges: List<String>,
    /** The primary window first. */
    val windows: List<HistoryWindowUi>,
) {
    /**
     * The window whose current period [HistoryRanges.PERIOD] shows: the shortest of [shownIds], the
     * primary one on a tie, or the first window when none of [shownIds] is known.
     */
    public fun periodWindow(shownIds: List<String>): HistoryWindowUi? =
        windows
            .filter { it.id in shownIds }
            .ifEmpty { windows.take(1) }
            .minWithOrNull(
                compareBy<HistoryWindowUi> {
                        it.summary.endEpochSeconds - it.summary.startEpochSeconds
                    }
                    .thenBy { !it.isPrimary }
            )

    /** Where [range] starts, in epoch seconds, with [shownIds] toggled on. */
    public fun rangeStartEpochSeconds(range: String, shownIds: List<String>): Long =
        when (range) {
            HistoryRanges.PERIOD ->
                periodWindow(shownIds)?.summary?.startEpochSeconds ?: minEpochSeconds
            HistoryRanges.ONE_YEAR -> minEpochSeconds
            else ->
                maxOf(minEpochSeconds, maxEpochSeconds - HistoryRanges.span(range).inWholeSeconds)
        }

    /** Where [range] ends, in epoch seconds, with [shownIds] toggled on. */
    public fun rangeEndEpochSeconds(range: String, shownIds: List<String>): Long =
        if (range == HistoryRanges.PERIOD) {
            periodWindow(shownIds)?.summary?.endEpochSeconds ?: maxEpochSeconds
        } else {
            maxEpochSeconds
        }
}

/** One long window's whole stored history, and its current period. */
public data class HistoryWindowUi(
    val id: String,
    val label: String,
    val isPrimary: Boolean,
    /**
     * The current period, anchored at [sampleAtEpochSeconds] so the projection matches the plot.
     */
    val summary: ChartUi,
    /** Every stored point, oldest first, past periods included. */
    val points: List<PointUi>,
    /** When the window reset across [points], oldest first. */
    val resetsEpochSeconds: List<Long>,
    /** The latest sample of the current period. */
    val sampleAtEpochSeconds: Long,
)

/** The usage history chart's range presets. */
public object HistoryRanges {
    /** The current period of the shortest shown window, up to its reset. */
    public const val PERIOD: String = "period"
    public const val ONE_MONTH: String = "1m"
    public const val THREE_MONTHS: String = "3m"
    public const val ONE_YEAR: String = "1y"

    internal fun span(range: String): Duration =
        when (range) {
            ONE_MONTH -> ONE_MONTH_DAYS.days
            THREE_MONTHS -> THREE_MONTHS_DAYS.days
            else -> ONE_YEAR_DAYS.days
        }

    internal const val ONE_MONTH_DAYS = 30
    internal const val THREE_MONTHS_DAYS = 90
    private const val ONE_YEAR_DAYS = 365
}

internal object HistoryMapping {
    /**
     * The history chart of [state], from each window's stored [histories], or null when the account
     * has no weekly or monthly window to chart. A stale account is charted as of its last sync.
     */
    fun history(
        state: AccountState,
        histories: Map<String, List<UsagePoint>>,
        now: Instant,
        keepLongHistory: Boolean,
    ): HistoryChartUi? {
        val dataFrom = state.snapshot?.fetchedAt
        val asOf = if (state.isSignInExpired) dataFrom ?: now else now
        val primaryId = state.primaryWindow?.id
        val windows =
            chartableWindows(state).mapIndexedNotNull { index, window ->
                val resetsAt = window.resetsAt ?: return@mapIndexedNotNull null
                val start = resetsAt.minus(window.length ?: return@mapIndexedNotNull null)
                val points =
                    prepareHistoryPoints(window, histories[window.id].orEmpty(), asOf, dataFrom)
                val sampleAt =
                    points.lastOrNull()?.at?.takeIf { it in start..resetsAt }
                        ?: (dataFrom ?: asOf).coerceIn(start, resetsAt)
                val summary =
                    chartSummary(window, points, sampleAt) ?: return@mapIndexedNotNull null
                HistoryWindowUi(
                    id = window.id,
                    label = window.label,
                    isPrimary = window.id == primaryId || index == 0,
                    summary = StatsMapping.chart(window.id, summary),
                    points = points.map { PointUi(it.at.epochSeconds, it.usedPercent) },
                    resetsEpochSeconds =
                        detectResets(points, window.length).map { it.epochSeconds },
                    sampleAtEpochSeconds = sampleAt.epochSeconds,
                )
            }
        if (windows.isEmpty()) return null
        val periodStarts = windows.map { it.summary.startEpochSeconds }
        val oldest =
            windows.mapNotNull { it.points.firstOrNull()?.atEpochSeconds }.minOrNull()
                ?: shortest(windows).summary.startEpochSeconds
        val min = minOf(oldest, periodStarts.min())
        val max = windows.maxOf { it.summary.endEpochSeconds }
        return HistoryChartUi(
            minEpochSeconds = min,
            maxEpochSeconds = max,
            ranges = ranges(windows, oldest, asOf.epochSeconds, keepLongHistory),
            windows = windows,
        )
    }

    /** The weekly and monthly windows of [state], the primary one first. */
    fun chartableWindows(state: AccountState) =
        state.snapshot
            ?.windows
            .orEmpty()
            .filter { it.isChartableHistoryWindow }
            .sortedByDescending { it.id == state.primaryWindow?.id }

    /**
     * The presets the history supports, across every window so toggling one never adds or removes a
     * button: a longer range needs history before the current period, and a year needs long history
     * kept and more than three months of it.
     */
    private fun ranges(
        windows: List<HistoryWindowUi>,
        oldest: Long,
        now: Long,
        keepLongHistory: Boolean,
    ): List<String> {
        val shortest = shortest(windows).summary
        val periodStart = shortest.startEpochSeconds
        val shortestLength = shortest.endEpochSeconds - periodStart
        val hasPast = oldest < periodStart
        val age = now - oldest
        val month = HistoryRanges.ONE_MONTH_DAYS.days.inWholeSeconds
        val threeMonths = HistoryRanges.THREE_MONTHS_DAYS.days.inWholeSeconds
        return buildList {
            add(HistoryRanges.PERIOD)
            if (hasPast && shortestLength < month) add(HistoryRanges.ONE_MONTH)
            if (hasPast && age > month && shortestLength < threeMonths) {
                add(HistoryRanges.THREE_MONTHS)
            }
            if (keepLongHistory && hasPast && age > threeMonths) add(HistoryRanges.ONE_YEAR)
        }
    }

    private fun shortest(windows: List<HistoryWindowUi>): HistoryWindowUi =
        windows.minWith(
            compareBy<HistoryWindowUi> { it.summary.endEpochSeconds - it.summary.startEpochSeconds }
                .thenBy { !it.isPrimary }
        )
}
