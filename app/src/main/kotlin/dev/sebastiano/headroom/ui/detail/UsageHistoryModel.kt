package dev.sebastiano.headroom.ui.detail

import androidx.compose.runtime.Immutable
import dev.c5inco.charts.core.CartesianChart
import dev.c5inco.charts.core.Curve
import dev.c5inco.charts.core.Gaps
import dev.c5inco.charts.core.LineMark
import dev.c5inco.charts.core.LinePattern
import dev.c5inco.charts.core.Mark
import dev.c5inco.charts.core.Markers
import dev.c5inco.charts.core.RuleMark
import dev.c5inco.charts.core.Series
import dev.c5inco.charts.core.TimeAxis
import dev.c5inco.charts.core.ValueAxis
import dev.c5inco.charts.core.ValueFormat
import dev.c5inco.charts.core.ViewportRange
import dev.c5inco.charts.core.XBandMark
import dev.c5inco.charts.core.XRuleMark
import dev.sebastiano.headroom.model.ChartSummary
import dev.sebastiano.headroom.model.QuotaWindow
import dev.sebastiano.headroom.model.ResetBoundary
import dev.sebastiano.headroom.model.UsagePoint
import dev.sebastiano.headroom.model.WindowKind
import dev.sebastiano.headroom.model.chartSummary
import dev.sebastiano.headroom.ui.ResetFormatter
import java.time.Instant as JavaInstant
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlin.time.toJavaInstant

/** Preset time ranges for the detail screen's usage history chart. */
enum class HistoryRange(val duration: Duration?) {
    /** The current reset period of the shortest shown window, including its future end. */
    Period(null),
    OneMonth(ONE_MONTH_DAYS.days),
    ThreeMonths(THREE_MONTHS_DAYS.days),
    OneYear(ONE_YEAR_DAYS.days),
}

/** One long quota window's history and current-period summary for [UsageHistoryChart]. */
@Immutable
data class WindowHistorySeries(
    val id: String,
    val label: String,
    val kind: WindowKind,
    val isPrimary: Boolean,
    val colorSlot: Int,
    val markers: Markers = Markers.Last,
    val summary: ChartSummary,
    /** All stored usage points up to `now`, ordered oldest first. */
    val points: List<UsagePoint>,
    /** Instants at which this window reset across [points], oldest first. */
    val resets: List<Instant>,
)

/**
 * History-dependent plot inputs for one window series. Anchored to the series' latest recorded
 * sample ([sampleAt]) rather than the wall-clock ticker so a 1-minute clock tick never invalidates
 * the compiled chart specs.
 */
@Immutable
internal data class SeriesPlotData(
    val id: String,
    val label: String,
    val isPrimary: Boolean,
    val colorSlot: Int,
    val markers: Markers,
    val periodStart: Instant,
    val periodEnd: Instant,
    val usedPercent: Double,
    val points: List<UsagePoint>,
    val resets: List<Instant>,
    val sampleAt: Instant,
    val projectedLimitAt: Instant?,
    val projectedEndPercent: Double?,
)

/**
 * History- and selection-dependent key for building and warming [UsageHistorySpecs]. Invariant
 * across 1-minute `now` ticks when no new sync point has arrived.
 */
@Immutable
internal data class UsageHistoryPlotKey(
    val accountId: String,
    val shownWindows: List<SeriesPlotData>,
    val minBound: Instant,
    val maxBound: Instant,
)

/**
 * Immutable model for the account detail usage history chart: all long windows of the account,
 * which ones are toggled on, and whether long-term history is enabled.
 */
@Immutable
data class UsageHistoryModel(
    val accountId: String,
    val now: Instant,
    val windows: List<WindowHistorySeries>,
    val selectedWindowIds: Set<String>,
    val keepLongHistory: Boolean = true,
) {
    /** The windows currently toggled on (always non-empty when [windows] is non-empty). */
    val shownWindows: List<WindowHistorySeries> =
        windows
            .filter { it.id in selectedWindowIds }
            .ifEmpty { listOfNotNull(windows.firstOrNull()) }

    /** The primary window among [shownWindows], or the first shown window when primary is off. */
    val primaryShownWindow: WindowHistorySeries? =
        shownWindows.firstOrNull { it.isPrimary } ?: shownWindows.firstOrNull()

    /** The shortest-duration window among [shownWindows], breaking ties with [isPrimary]. */
    val shortestShownWindow: WindowHistorySeries? =
        shownWindows.minWithOrNull(
            compareBy<WindowHistorySeries> { it.summary.end - it.summary.start }
                .thenBy { !it.isPrimary }
        )

    private val shortestAccountWindow: WindowHistorySeries? =
        windows.minWithOrNull(
            compareBy<WindowHistorySeries> { it.summary.end - it.summary.start }
                .thenBy { !it.isPrimary }
        )

    private val oldestPointAt: Instant =
        windows.mapNotNull { it.points.firstOrNull()?.at }.minOrNull()
            ?: shortestAccountWindow?.summary?.start
            ?: now

    /** Left pan bound: the oldest stored point, or the earliest period start when newer. */
    val minBound: Instant = minOf(oldestPointAt, windows.minOfOrNull { it.summary.start } ?: now)

    /** Right pan bound: the end of the current period across the account's long windows. */
    val maxBound: Instant = windows.maxOfOrNull { it.summary.end } ?: now

    /** Default viewport start: the start of [shortestShownWindow]'s current period. */
    val defaultStart: Instant = shortestShownWindow?.summary?.start ?: minBound

    /** Default viewport end: the future reset of [shortestShownWindow]'s current period. */
    val defaultEnd: Instant = shortestShownWindow?.summary?.end ?: maxBound

    /**
     * Range presets supported by the account's long-window history. Computed across all [windows]
     * so toggling a filter chip never adds or removes the range button row under the finger.
     * [HistoryRange.OneYear] requires both [keepLongHistory] and history extending beyond three
     * months.
     */
    val availableRanges: List<HistoryRange> = run {
        val shortest = shortestAccountWindow ?: return@run listOf(HistoryRange.Period)
        val periodStart = shortest.summary.start
        val shortestLength = shortest.summary.end - periodStart
        val hasPastHistory = oldestPointAt < periodStart
        val historyAge = now - oldestPointAt
        buildList {
            add(HistoryRange.Period)
            if (hasPastHistory && shortestLength < ONE_MONTH_DAYS.days) {
                add(HistoryRange.OneMonth)
            }
            if (
                hasPastHistory &&
                    historyAge > ONE_MONTH_DAYS.days &&
                    shortestLength < THREE_MONTHS_DAYS.days
            ) {
                add(HistoryRange.ThreeMonths)
            }
            if (keepLongHistory && hasPastHistory && historyAge > THREE_MONTHS_DAYS.days) {
                add(HistoryRange.OneYear)
            }
        }
    }

    private val allResetEpochMs: LongArray =
        shownWindows.flatMap { it.resets }.map { it.toEpochMilliseconds() }.sorted().toLongArray()

    /**
     * History- and selection-dependent key for spec building and background cache warming. Does not
     * depend on [now], so 1-minute clock ticks do not rebuild or re-warm the chart specs.
     */
    internal val plotKey: UsageHistoryPlotKey =
        UsageHistoryPlotKey(
            accountId = accountId,
            shownWindows =
                shownWindows.map { window ->
                    val sampleAt = window.points.lastOrNull()?.at ?: window.summary.start
                    val sampleSummary =
                        chartSummary(
                            window =
                                QuotaWindow(
                                    id = window.id,
                                    label = window.label,
                                    kind = window.kind,
                                    usedPercent = window.summary.usedPercent,
                                    resetsAt = window.summary.end,
                                    length = window.summary.end - window.summary.start,
                                ),
                            points = window.points,
                            now = sampleAt,
                        )
                    SeriesPlotData(
                        id = window.id,
                        label = window.label,
                        isPrimary = window.isPrimary,
                        colorSlot = window.colorSlot,
                        markers = window.markers,
                        periodStart = window.summary.start,
                        periodEnd = window.summary.end,
                        usedPercent = window.summary.usedPercent,
                        points = window.points,
                        resets = window.resets,
                        sampleAt = sampleAt,
                        projectedLimitAt = sampleSummary?.projectedLimitAt,
                        projectedEndPercent = sampleSummary?.projectedEndPercent,
                    )
                },
            minBound = minBound,
            maxBound = maxBound,
        )

    /** Target `[start, end]` in epoch milliseconds for [range]. */
    fun targetRange(range: HistoryRange): ViewportRange =
        when (range) {
            HistoryRange.Period ->
                ViewportRange(
                    defaultStart.toEpochMilliseconds().toDouble(),
                    defaultEnd.toEpochMilliseconds().toDouble(),
                )
            HistoryRange.OneYear ->
                ViewportRange(
                    minBound.toEpochMilliseconds().toDouble(),
                    maxBound.toEpochMilliseconds().toDouble(),
                )
            else -> {
                val span = requireNotNull(range.duration)
                val start = maxOf(minBound, maxBound - span)
                ViewportRange(
                    start.toEpochMilliseconds().toDouble(),
                    maxBound.toEpochMilliseconds().toDouble(),
                )
            }
        }

    /** Number of resets across [shownWindows] that fall inside `(startEpochMs, endEpochMs]`. */
    fun resetsInRange(startEpochMs: Double, endEpochMs: Double): Int {
        if (allResetEpochMs.isEmpty() || endEpochMs <= startEpochMs) return 0
        val startIdx = firstIndexAfter(allResetEpochMs, startEpochMs.roundToLong())
        val endIdx = firstIndexAfter(allResetEpochMs, endEpochMs.roundToLong())
        return (endIdx - startIdx).coerceAtLeast(0)
    }
}

private fun firstIndexAfter(sorted: LongArray, value: Long): Int {
    var lo = 0
    var hi = sorted.size
    while (lo < hi) {
        val mid = (lo + hi) ushr 1
        if (sorted[mid] <= value) lo = mid + 1 else hi = mid
    }
    return lo
}

/**
 * True when a quota window is a long reset window (weekly or monthly) that can be plotted on the
 * usage history chart. Excludes session, daily, credit, unlimited and informational windows.
 */
internal val QuotaWindow.isChartableHistoryWindow: Boolean
    get() =
        (kind == WindowKind.Weekly || kind == WindowKind.Monthly) &&
            resetsAt != null &&
            length?.let { it > Duration.ZERO } == true &&
            !isUnlimited &&
            !isInformational

/**
 * Finds every reset instant in [points] (ordered oldest first) using [ResetBoundary.isReset], so
 * the chart's reset markers agree with history compaction and ignore relative countdown jitter.
 */
internal fun detectResets(
    points: List<UsagePoint>,
    windowLength: Duration? = null,
): List<Instant> {
    if (points.size < 2) return emptyList()
    val resets = mutableListOf<Instant>()
    var periodResetsAt: Instant? = points.first().resetsAt
    var periodPeak: Double = points.first().usedPercent
    var lastAt: Instant = points.first().at
    for (index in 1 until points.size) {
        val point = points[index]
        val previous =
            UsagePoint(
                at = lastAt,
                usedPercent = periodPeak,
                resetsAt = periodResetsAt,
            )
        if (ResetBoundary.isReset(previous, point, windowLength)) {
            resets += point.at
            periodResetsAt = point.resetsAt
            periodPeak = point.usedPercent
        } else {
            if (periodResetsAt == null && point.resetsAt != null) {
                periodResetsAt = point.resetsAt
            }
            if (point.usedPercent > periodPeak) periodPeak = point.usedPercent
        }
        lastAt = point.at
    }
    return resets
}

/**
 * Builds the [UsageHistoryModel] for an account from its [windows] and per-window [histories], or
 * null when the account has no chartable long windows.
 *
 * Every quota series is drawn with a solid stroke ([LinePattern.Solid]) so [LinePattern.Dashed]
 * remains exclusive to the even-pace guide and [LinePattern.Dotted] to projections. Series are
 * assigned palette slots in `0 until SERIES_SLOT_COUNT` (excluding [PACE_COLOR_SLOT]): slots `0
 * until SERIES_PALETTE_SIZE` pair the 6 colours with 6 distinct glyphs and [Markers.Last], while
 * overflow windows (`index >= SERIES_PALETTE_SIZE`) use slots `SERIES_PALETTE_SIZE until
 * SERIES_SLOT_COUNT` (rotated colour+glyph pairs) and [Markers.All] along the solid line.
 */
internal fun buildUsageHistoryModel(
    accountId: String,
    windows: List<QuotaWindow>,
    primaryWindowId: String?,
    histories: Map<String, List<UsagePoint>>,
    selectedWindowIds: Set<String>?,
    now: Instant,
    keepLongHistory: Boolean = true,
    dataFrom: Instant? = null,
): UsageHistoryModel? {
    val chartable =
        windows
            .filter { it.isChartableHistoryWindow }
            .sortedByDescending { it.id == primaryWindowId }
    if (chartable.isEmpty()) return null
    val series = chartable.mapIndexedNotNull { index, window ->
        val raw = histories[window.id].orEmpty()
        val prepared = prepareSeriesPoints(window, raw, now, dataFrom)
        val summary = chartSummary(window, prepared, now) ?: return@mapIndexedNotNull null
        WindowHistorySeries(
            id = window.id,
            label = window.label,
            kind = window.kind,
            isPrimary = window.id == primaryWindowId || index == 0,
            colorSlot = Math.floorMod(index, SERIES_SLOT_COUNT),
            markers = if (index < SERIES_PALETTE_SIZE) Markers.Last else Markers.All,
            summary = summary,
            points = prepared,
            resets = detectResets(prepared, window.length),
        )
    }
    if (series.isEmpty()) return null
    val allIds = series.map { it.id }.toSet()
    val validSelected = selectedWindowIds?.intersect(allIds).orEmpty().ifEmpty { allIds }
    return UsageHistoryModel(
        accountId = accountId,
        now = now,
        windows = series,
        selectedWindowIds = validSelected,
        keepLongHistory = keepLongHistory,
    )
}

private fun prepareSeriesPoints(
    window: QuotaWindow,
    raw: List<UsagePoint>,
    now: Instant,
    dataFrom: Instant?,
): List<UsagePoint> {
    val resetsAt = window.resetsAt ?: return emptyList()
    val length = window.length ?: return emptyList()
    val start = resetsAt.minus(length)
    val sorted = raw.filter { it.at <= now }.sortedBy { it.at }
    val deduped = ArrayList<UsagePoint>(sorted.size + 1)
    for (point in sorted) {
        if (deduped.isNotEmpty() && deduped.last().at == point.at) {
            deduped[deduped.lastIndex] = point
        } else {
            deduped += point
        }
    }
    val sampleAt = dataFrom ?: now
    val hasCurrentPeriodSample =
        deduped.isNotEmpty() &&
            deduped.last().at >= start &&
            (dataFrom == null || deduped.last().at >= dataFrom) &&
            deduped.last().usedPercent == window.usedPercent
    if (
        !hasCurrentPeriodSample &&
            sampleAt in start..resetsAt &&
            (deduped.isEmpty() || deduped.last().at < sampleAt)
    ) {
        deduped += UsagePoint(at = sampleAt, usedPercent = window.usedPercent, resetsAt = resetsAt)
    }
    return deduped
}

internal data class UsageHistorySpecs(
    val detail: CartesianChart,
    val overview: CartesianChart,
)

internal fun buildUsageHistoryChartSpecs(
    plotKey: UsageHistoryPlotKey,
    formatter: ResetFormatter,
    limitLabel: String,
    percentFormat: (Int) -> String,
): UsageHistorySpecs {
    val description = plotKey.shownWindows.joinToString { it.label }
    val detail =
        buildUsageHistoryChartSpec(
            plotKey = plotKey,
            formatter = formatter,
            limitLabel = limitLabel,
            percentFormat = percentFormat,
            description = description,
            resetStride = 1,
        )
    val hasDenseResets = plotKey.shownWindows.any { it.resets.size > MIN_RESETS_TO_THIN }
    val overview =
        if (!hasDenseResets) {
            detail
        } else {
            buildUsageHistoryChartSpec(
                plotKey = plotKey,
                formatter = formatter,
                limitLabel = limitLabel,
                percentFormat = percentFormat,
                description = description,
                resetStride = OVERVIEW_RESET_STRIDE,
            )
        }
    return UsageHistorySpecs(detail = detail, overview = overview)
}

/** Compiles [model] into a [CartesianChart] spec on a single merged [TimeAxis]. */
internal fun buildUsageHistoryChartSpec(
    model: UsageHistoryModel,
    formatter: ResetFormatter,
    limitLabel: String,
    percentFormat: (Int) -> String,
    description: String,
    resetStride: Int = 1,
): CartesianChart =
    buildUsageHistoryChartSpec(
        plotKey = model.plotKey,
        formatter = formatter,
        limitLabel = limitLabel,
        percentFormat = percentFormat,
        description = description,
        resetStride = resetStride,
    )

/**
 * Compiles [plotKey] into a [CartesianChart] spec on a single merged [TimeAxis].
 *
 * Series timestamps are merged in $O(N \log N)$ over primitive epoch-millisecond arrays and aligned
 * in a single linear pass per series with `Double.NaN` holes connected by [Gaps.Connect].
 */
internal fun buildUsageHistoryChartSpec(
    plotKey: UsageHistoryPlotKey,
    formatter: ResetFormatter,
    limitLabel: String,
    percentFormat: (Int) -> String,
    description: String,
    resetStride: Int = 1,
): CartesianChart {
    val shown = plotKey.shownWindows
    val uniqueMs = collectSortedEpochMillis(plotKey, shown)
    val times = List(uniqueMs.size) { JavaInstant.ofEpochMilli(uniqueMs[it]) }

    val marks = ArrayList<Mark>(shown.size * 3 + 4)
    val primary = shown.firstOrNull { it.isPrimary } ?: shown.firstOrNull()
    primary?.let {
        marks +=
            XBandMark(
                from = it.periodStart.toJavaInstant(),
                to = it.periodEnd.toJavaInstant(),
                color = it.colorSlot,
            )
    }

    shown
        .groupBy { it.markers }
        .forEach { (markerMode, windowsForMode) ->
            val usageSeries = windowsForMode.map { window ->
                Series(
                    name = window.label,
                    values =
                        alignPointsToGrid(
                            points = window.points,
                            periodStartMs = window.periodStart.toEpochMilliseconds(),
                            uniqueMs = uniqueMs,
                        ),
                    color = window.colorSlot,
                )
            }
            marks +=
                LineMark(
                    series = usageSeries,
                    curve = Curve.Linear,
                    markers = markerMode,
                    gaps = Gaps.Connect,
                    pattern = LinePattern.Solid,
                )
        }

    appendPaceMarks(shown, uniqueMs, marks)
    appendProjectionMarks(shown, uniqueMs, marks)
    appendResetRuleMarks(shown, resetStride.coerceAtLeast(1), marks)
    marks += RuleMark(y = MAX_PERCENT, label = limitLabel)

    val yFormat = ValueFormat { value -> percentFormat(value.roundToInt()) }
    return CartesianChart(
        x =
            TimeAxis(
                times = times,
                min = plotKey.minBound.toJavaInstant(),
                max = plotKey.maxBound.toJavaInstant(),
                tickCount = TIME_AXIS_TICK_COUNT,
                format = formatter.chartTickFormat(),
                pointFormat = formatter.chartPointFormat(),
                zone = formatter.zone,
                locale = formatter.locale,
            ),
        marks = marks,
        y =
            ValueAxis(
                min = 0.0,
                max = MAX_PERCENT,
                tickCount = VALUE_AXIS_TICK_COUNT,
                format = yFormat,
                valueFormat = yFormat,
            ),
        description = description,
    )
}

/**
 * Pre-populates `CartesianPrep`'s `xLabelCache` and `valueTextCache` across the full domain on a
 * background dispatcher so panning or flinging across a year of points never runs
 * `DateTimeFormatter` or allocates formatted strings on a gesture frame.
 */
internal fun warmSpecCaches(specs: UsageHistorySpecs, plotKey: UsageHistoryPlotKey) {
    val minMs = plotKey.minBound.toEpochMilliseconds().toDouble()
    val maxMs = plotKey.maxBound.toEpochMilliseconds().toDouble()
    if (maxMs > minMs) {
        val range = ViewportRange(minMs, maxMs)
        val extent = dev.c5inco.charts.core.Extent(WARM_EXTENT_WIDTH, WARM_EXTENT_HEIGHT)
        val measure =
            dev.c5inco.charts.core.TextMeasure { _, _ ->
                dev.c5inco.charts.core.Extent(WARM_TEXT_SIZE, WARM_TEXT_SIZE)
            }
        dev.c5inco.charts.core.compile(specs.detail, extent, measure, viewport = range)
        if (specs.overview !== specs.detail) {
            dev.c5inco.charts.core.compile(specs.overview, extent, measure, viewport = range)
        }
    }
}

/** One dashed even-pace diagonal per distinct current period `[start, end]` among shown windows. */
private fun appendPaceMarks(
    shown: List<SeriesPlotData>,
    uniqueMs: LongArray,
    marks: MutableList<Mark>,
) {
    shown
        .map { it.periodStart to it.periodEnd }
        .distinct()
        .forEach { (start, end) ->
            val startMs = start.toEpochMilliseconds()
            val endMs = end.toEpochMilliseconds()
            if (endMs > startMs) {
                val values =
                    twoPointSeries(
                        uniqueMs = uniqueMs,
                        firstMs = startMs,
                        firstValue = 0.0,
                        secondMs = endMs,
                        secondValue = MAX_PERCENT,
                    )
                marks +=
                    LineMark(
                        series =
                            listOf(
                                Series(
                                    name = "pace:$startMs:$endMs",
                                    values = values,
                                    color = PACE_COLOR_SLOT,
                                )
                            ),
                        curve = Curve.Linear,
                        gaps = Gaps.Connect,
                        pattern = LinePattern.Dashed,
                    )
            }
        }
}

/**
 * One dotted projection line per shown window within its current period (`sampleAt` -> hit or end).
 */
private fun appendProjectionMarks(
    shown: List<SeriesPlotData>,
    uniqueMs: LongArray,
    marks: MutableList<Mark>,
) {
    val projectionSeries = ArrayList<Series>(shown.size)
    shown.forEach { window ->
        val sampleMs = window.sampleAt.toEpochMilliseconds()
        val projectedLimitAt = window.projectedLimitAt
        val projectedEndPercent = window.projectedEndPercent
        if (window.usedPercent > FRESH_MAX_PERCENT) {
            val target =
                when {
                    projectedLimitAt != null && projectedLimitAt > window.sampleAt ->
                        projectedLimitAt.toEpochMilliseconds() to MAX_PERCENT
                    projectedEndPercent != null && window.periodEnd > window.sampleAt ->
                        window.periodEnd.toEpochMilliseconds() to
                            projectedEndPercent.coerceIn(0.0, MAX_PERCENT)
                    else -> null
                }
            if (target != null && target.first > sampleMs) {
                val values =
                    twoPointSeries(
                        uniqueMs = uniqueMs,
                        firstMs = sampleMs,
                        firstValue = window.usedPercent.coerceIn(0.0, MAX_PERCENT),
                        secondMs = target.first,
                        secondValue = target.second,
                    )
                projectionSeries +=
                    Series(
                        name = "projection:${window.id}",
                        values = values,
                        color = window.colorSlot,
                    )
            }
        }
    }
    if (projectionSeries.isNotEmpty()) {
        marks +=
            LineMark(
                series = projectionSeries,
                curve = Curve.Linear,
                gaps = Gaps.Connect,
                pattern = LinePattern.Dotted,
            )
    }
}

/**
 * Vertical [XRuleMark] in each window's series colour at its resets. When [resetStride] > 1 and a
 * window has more than [MIN_RESETS_TO_THIN] resets (e.g. 52 weekly resets in a 1-year view where
 * rules would sit closer than ~6 dp apart), keeps every [resetStride]-th reset counting back from
 * the latest reset so the newest reset is always marked.
 */
private fun appendResetRuleMarks(
    shown: List<SeriesPlotData>,
    resetStride: Int,
    marks: MutableList<Mark>,
) {
    val seenRules = HashSet<Long>()
    shown.forEach { window ->
        val resets = window.resets
        val stride = if (resets.size > MIN_RESETS_TO_THIN) resetStride else 1
        val startIdx = if (stride > 1 && resets.isNotEmpty()) resets.lastIndex % stride else 0
        for (index in startIdx until resets.size step stride) {
            val ms = resets[index].toEpochMilliseconds()
            if (seenRules.add(ms)) {
                marks +=
                    XRuleMark(
                        x = ms.toDouble(),
                        color = window.colorSlot,
                        pattern = LinePattern.Dashed,
                    )
            }
        }
    }
}

private fun collectSortedEpochMillis(
    plotKey: UsageHistoryPlotKey,
    shown: List<SeriesPlotData>,
): LongArray {
    val extraCount = shown.size * 5 + 2
    val totalCapacity = shown.sumOf { it.points.size } + extraCount
    val raw = LongArray(totalCapacity)
    var k = 0
    raw[k++] = plotKey.minBound.toEpochMilliseconds()
    raw[k++] = plotKey.maxBound.toEpochMilliseconds()
    for (window in shown) {
        val startMs = window.periodStart.toEpochMilliseconds()
        raw[k++] = startMs
        if (startMs > plotKey.minBound.toEpochMilliseconds()) {
            raw[k++] = startMs - 1L
        }
        raw[k++] = window.periodEnd.toEpochMilliseconds()
        raw[k++] = window.sampleAt.toEpochMilliseconds()
        window.projectedLimitAt?.let { raw[k++] = it.toEpochMilliseconds() }
        for (point in window.points) {
            raw[k++] = point.at.toEpochMilliseconds()
        }
    }
    raw.sort(0, k)
    var uniqueCount = 0
    for (i in 0 until k) {
        val v = raw[i]
        if (uniqueCount == 0 || raw[uniqueCount - 1] != v) {
            raw[uniqueCount++] = v
        }
    }
    return raw.copyOf(uniqueCount)
}

private fun alignPointsToGrid(
    points: List<UsagePoint>,
    periodStartMs: Long,
    uniqueMs: LongArray,
): List<Double> {
    val values = DoubleArray(uniqueMs.size) { Double.NaN }
    var cursor = 0
    var hasPointBeforePeriod = false
    var pointAtPeriodStart: Double = Double.NaN
    for (point in points) {
        val ms = point.at.toEpochMilliseconds()
        if (ms < periodStartMs - 1L) hasPointBeforePeriod = true
        val clamped = point.usedPercent.coerceIn(0.0, MAX_PERCENT)
        if (ms == periodStartMs) pointAtPeriodStart = clamped
        while (cursor < uniqueMs.size && uniqueMs[cursor] < ms) {
            cursor++
        }
        if (cursor < uniqueMs.size && uniqueMs[cursor] == ms) {
            values[cursor] = clamped
        }
    }
    if (hasPointBeforePeriod && pointAtPeriodStart.isFinite()) {
        val guardIdx = uniqueMs.binarySearch(periodStartMs - 1L)
        if (guardIdx >= 0 && values[guardIdx].isNaN()) {
            values[guardIdx] = pointAtPeriodStart
        }
    }
    return values.asList()
}

private fun twoPointSeries(
    uniqueMs: LongArray,
    firstMs: Long,
    firstValue: Double,
    secondMs: Long,
    secondValue: Double,
): List<Double> {
    val values = DoubleArray(uniqueMs.size) { Double.NaN }
    val firstIdx = uniqueMs.binarySearch(firstMs)
    val secondIdx = uniqueMs.binarySearch(secondMs)
    if (firstIdx >= 0) values[firstIdx] = firstValue
    if (secondIdx >= 0) values[secondIdx] = secondValue
    return values.asList()
}

/** Number of primary (colour, glyph) slots before overflow marker mode kicks in. */
internal const val SERIES_PALETTE_SIZE: Int = 6

/**
 * Total number of series slots (`0 until 12`): 6 primary `(colour, glyph)` pairs followed by 6
 * rotated `(colour, glyph)` pairs for overflow windows.
 */
internal const val SERIES_SLOT_COUNT: Int = SERIES_PALETTE_SIZE * 2

/** Palette slot reserved for the dashed even-pace guide line in [UsageHistoryChart]. */
internal const val PACE_COLOR_SLOT: Int = SERIES_SLOT_COUNT

/** Below this usage percentage, a window reads as just reset and has no projection line. */
internal const val FRESH_MAX_PERCENT: Double = 0.5

private const val MAX_PERCENT = 100.0
private const val ONE_MONTH_DAYS = 30L
private const val THREE_MONTHS_DAYS = 90L
private const val ONE_YEAR_DAYS = 365L
private const val OVERVIEW_RESET_STRIDE = 4
private const val TIME_AXIS_TICK_COUNT = 5
private const val VALUE_AXIS_TICK_COUNT = 3
private const val MIN_RESETS_TO_THIN = 20
private const val WARM_EXTENT_WIDTH = 16_000f
private const val WARM_EXTENT_HEIGHT = 200f
private const val WARM_TEXT_SIZE = 8f
