package dev.c5inco.charts.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Measures text the way the renderer will draw it. Layout needs label sizes to size margins. */
fun interface TextMeasure {
    fun measure(text: String, kind: TextKind): Extent

    /**
     * Vertical ink extent of [text], without the line box's ascent and descent padding, for testing
     * whether stacked single-line labels collide. Defaults to the full line height.
     */
    fun inkHeight(text: String, kind: TextKind): Float = measure(text, kind).height
}

/**
 * Geometry knobs, in pixels. These change where things are, so they live in layout; colours and
 * stroke widths change only how things look, and stay in the renderer's style.
 */
data class ChartMetrics(
    val labelGap: Float = 8f,
    /** Fraction of each category band left empty between bars. */
    val bandPadding: Float = 0.3f,
    val bandOuterPadding: Float = 0.15f,
    /** Fraction of each bar slot left empty between bars of one group. */
    val groupPadding: Float = 0.12f,
    val maxBarWidth: Float = Float.POSITIVE_INFINITY,
    /** Gap between stacked segments. */
    val stackGap: Float = 0f,
    val dotRadius: Float = 4f,
    val markerRadius: Float = 3.5f,
    val minLabelSpacing: Float = 12f,
    val pieInnerRatio: Float = 0.6f,
    /** Gap between slices, measured along the middle of the ring. */
    val pieGap: Float = 0f,
    val pieInset: Float = 0f,
    /**
     * The plot's inset on a side whose axis is hidden: half the widest stroke (lines, area edges,
     * wicks, x rules), so a line or rule at the edge of a sparkline is not clipped. Line markers
     * widen it to [markerRadius] and dots to [dotRadius], when a chart has them.
     */
    val strokeInset: Float = 1.5f,
    /**
     * Fraction of each slot left empty between candles, used in place of [bandPadding] when a
     * chart has candles and no bars.
     */
    val candlePadding: Float = 0.3f,
    /** Space between the price area and a [CandleMark]'s volume strip under it. */
    val volumeGap: Float = 4f,
    /** Gap between neighbouring treemap tiles. Tiles on a group's outer edge keep that edge. */
    val tileGap: Float = 1f,
    /** Gap between treemap groups. Groups on the chart's outer edge keep that edge. */
    val groupGap: Float = 4f,
    /**
     * Space between a treemap tile's edge and its text; a group header strip is its label's
     * height plus this much.
     */
    val tilePadding: Float = 4f,
    /** Gap between neighbouring heatmap cells; shrinks to a quarter of a cell's step when cells are tiny. */
    val cellGap: Float = 3f,
    /** The empty centre of a [RoseChart], as a fraction of its radius. */
    val roseInnerRatio: Float = 0.16f,
    /**
     * How far a [RoseChart] petal's hit region reaches out from the inner radius at least, so a zero
     * or tiny petal can still be pressed. Never past the rim.
     */
    val petalMinHit: Float = 24f,
    /** Dash and gap lengths in pixels for a [LinePattern.Dashed] line, scaled from its stroke width. */
    val lineDashOn: Float = 7.5f,
    val lineDashOff: Float = 5f,
    /** Centre-to-centre dot spacing in pixels for a [LinePattern.Dotted] line, scaled from its stroke width. */
    val lineDotSpacing: Float = 6.25f,
)

/**
 * Compiles [spec] into a renderer-neutral [ChartScene] of size [size].
 *
 * When [viewport] is given, [spec] must be a [CartesianChart] with a [NumericAxis] or [TimeAxis]
 * (any other chart or a [CategoryAxis] throws [IllegalArgumentException]): the x scale's domain
 * becomes `[viewport.start, viewport.end]`, data outside the window is culled by binary search on
 * the ascending x positions (keeping one point beyond each edge so lines and areas run to the plot
 * border), dense windows (`visiblePositions > ceil(plot.width)`) are M4-decimated per pixel column
 * (`Markers.All` then places markers on the decimated defined samples), ticks are recomputed for
 * the window, and [ChartScene.clipPlot] is set so marks are clipped to the plot rectangle. When
 * [viewport] is null, no culling or decimation is applied.
 */
fun compile(
    spec: ChartSpec,
    size: Extent,
    measure: TextMeasure,
    metrics: ChartMetrics = ChartMetrics(),
    viewport: ViewportRange? = null,
): ChartScene {
    if (viewport != null) {
        require(spec is CartesianChart && (spec.x is NumericAxis || spec.x is TimeAxis)) {
            val axisOrKind = if (spec is CartesianChart) spec.x::class.simpleName else spec::class.simpleName
            "A viewport requires a CartesianChart with a NumericAxis or TimeAxis, got $axisOrKind"
        }
    }
    return when (spec) {
        is CartesianChart -> compileCartesian(spec, size, measure, metrics, viewport)
        is HorizontalBarChart -> compileHorizontal(spec, size, measure, metrics)
        is PieChart -> compilePie(spec, size, metrics)
        is TreemapChart -> compileTreemap(spec, size, measure, metrics)
        is HeatmapChart -> compileHeatmap(spec, size, measure, metrics)
        is RadarChart -> compileRadar(spec, size, measure, metrics)
        is RoseChart -> compileRose(spec, size, measure, metrics)
    }
}

private fun seriesCount(mark: Mark): Int = when (mark) {
    is BarMark -> mark.series.size
    is LineMark -> mark.series.size
    is AreaMark -> mark.series.size
    is DotMark -> mark.series.size
    is CandleMark -> 1
    is RuleMark, is XRuleMark, is XBandMark -> 0
}

internal fun emptyScene(size: Extent, spec: ChartSpec, focusMode: FocusMode = FocusMode.NearestX, clipPlot: Boolean = false) = ChartScene(
    size, Bounds(0f, 0f, size.width, size.height), if (focusMode == FocusMode.NearestY) 0f else size.height, spec.legend(),
    emptyList(), emptyList(), emptyList(), emptyList(), focusMode, spec.description, clipPlot = clipPlot,
)

/** One day, the fallback bar slot on a time axis with a single point. */
private const val DAY_MS = 86_400_000.0

/**
 * Spec-only invariants of a [CartesianChart]: value domain, diverging stacks, normalized candles,
 * series offsets and formatted-text caches. Independent of canvas size, metrics, text measurement
 * and the active [ViewportRange], so panning or zooming a viewport reuses them without rescanning
 * the full dataset.
 */
internal class CartesianPrep(spec: CartesianChart) {
    val categories: List<String>? = (spec.x as? CategoryAxis)?.labels
    val numericX: NumericAxis? = spec.x as? NumericAxis
    val timeX: TimeAxis? = spec.x as? TimeAxis
    val xValues: DoubleArray? = numericX?.values?.toDoubleArray() ?: timeX?.epochMillisArray
    val positions: Int = categories?.size ?: xValues?.size ?: 0
    val xStep: Double = numericX?.minStep ?: timeX?.minStep ?: 0.0
    val xAscending: Boolean = run {
        val xs = xValues ?: return@run false
        var prev = Double.NEGATIVE_INFINITY
        for (i in xs.indices) {
            val v = xs[i]
            if (!v.isFinite() || v < prev) return@run false
            prev = v
        }
        true
    }
    val hasBars: Boolean = spec.marks.any { it is BarMark }
    val hasSlots: Boolean = hasBars || spec.marks.any { it is CandleMark }
    val stripMark: Int = spec.marks.indexOfFirst { it is CandleMark && it.volumeFraction > 0f }
    val orderedMarks: List<Int> = spec.marks.indices.sortedBy {
        when (spec.marks[it]) {
            is XBandMark -> 0
            is AreaMark -> 1
            else -> 2
        }
    }
    val lastArea: Int? = orderedMarks.lastOrNull { spec.marks[it] is AreaMark }
    val dotsOnly: Boolean = spec.marks.any { it is DotMark } &&
        spec.marks.all { it is DotMark || it is RuleMark || it is XRuleMark || it is XBandMark }
    val focusMode: FocusMode = if (dotsOnly) FocusMode.NearestXY else FocusMode.NearestX
    val legend: List<SeriesInfo> = spec.legend()

    val firstSeries: IntArray = IntArray(spec.marks.size).also { arr ->
        spec.marks.foldIndexed(0) { i, next, mark -> arr[i] = next; next + seriesCount(mark) }
    }
    val totalSeries: Int = spec.marks.sumOf(::seriesCount)
    val hasMarkers: Boolean = spec.marks.any { it is LineMark && it.markers != Markers.None }
    val hasDots: Boolean = spec.marks.any { it is DotMark }
    val boxedDomainX: Array<Double?>? = xValues?.let { xs -> Array(xs.size) { xs[it] } }

    val candles: Map<Int, List<Ohlc?>> = HashMap<Int, List<Ohlc?>>().also { map ->
        spec.marks.forEachIndexed { i, mark ->
            if (mark is CandleMark) map[i] = mark.candles.take(positions).map { it.normalized() }
        }
    }

    val maxVolume: Map<Int, Double> = HashMap<Int, Double>().also { map ->
        candles.forEach { (i, periods) ->
            map[i] = periods.maxOfOrNull { it?.volume ?: 0.0 }?.takeIf { it > 0.0 } ?: 1.0
        }
    }

    val stacks: Map<Int, List<List<Pair<Double, Double>>>> = HashMap<Int, List<List<Pair<Double, Double>>>>().also { map ->
        spec.marks.forEachIndexed { i, mark ->
            when {
                mark is BarMark && mark.layout == BarLayout.Stacked -> map[i] = Stack.diverging(mark.series.map { it.values })
                mark is AreaMark && mark.stacked -> map[i] = Stack.diverging(mark.series.map { it.values })
            }
        }
    }

    val lo: Double
    val hi: Double
    val hasLength: Boolean
    val xDataLo: Double
    val xDataHi: Double

    init {
        var vLo = Double.POSITIVE_INFINITY
        var vHi = Double.NEGATIVE_INFINITY
        var length = false
        fun take(v: Double) {
            if (v.isFinite()) { vLo = min(vLo, v); vHi = max(vHi, v) }
        }
        fun takeStack(stack: List<List<Pair<Double, Double>>>) = stack.forEach { s ->
            s.forEach { (a, b) -> if (a != 0.0) take(a); if (b != 0.0) take(b) }
        }
        var dXLo = Double.POSITIVE_INFINITY
        var dXHi = Double.NEGATIVE_INFINITY
        fun takeX(v: Double) {
            if (v.isFinite()) { dXLo = min(dXLo, v); dXHi = max(dXHi, v) }
        }
        if (categories == null && xValues != null && xValues.isNotEmpty()) {
            if (xAscending) {
                dXLo = xValues.first()
                dXHi = xValues.last()
            } else {
                xValues.forEach(::takeX)
            }
        }
        spec.marks.forEachIndexed { i, mark ->
            val stack = stacks[i]
            when (mark) {
                is BarMark -> { length = true; if (stack != null) takeStack(stack) else mark.series.forEach { s -> s.values.forEach(::take) } }
                is AreaMark -> { length = true; if (stack != null) takeStack(stack) else mark.series.forEach { s -> s.values.forEach(::take) } }
                is LineMark -> mark.series.forEach { s -> s.values.forEach(::take) }
                is DotMark -> mark.series.forEach { s -> s.points.forEach { take(it.y); takeX(it.x) } }
                // Prices, not lengths: lows to highs, never zero. Volume has its own scale.
                is CandleMark -> candles[i]?.forEach { c -> if (c != null) { take(c.low); take(c.high) } }
                is RuleMark -> take(mark.y)
                is XRuleMark, is XBandMark -> Unit
            }
        }
        if (!dXLo.isFinite()) {
            spec.marks.forEach { mark ->
                when (mark) {
                    is XRuleMark -> takeX(mark.x)
                    is XBandMark -> { takeX(mark.from); takeX(mark.to) }
                    else -> Unit
                }
            }
        }
        lo = vLo
        hi = vHi
        hasLength = length
        xDataLo = dXLo
        xDataHi = dXHi
    }

    val yBaseScale: LinearScale = valueScale(spec.y, lo, hi, hasLength)
    val yTicks: List<Double> = if (spec.y.visible) yBaseScale.ticks(spec.y.tickCount) else emptyList()
    val yFormat: ValueFormat = spec.y.format ?: Formats.forTicks(yTicks)
    private val defaultYTexts: List<String>? = if (spec.y.format == null) yTicks.map(yFormat::format) else null

    fun yTexts(): List<String> = defaultYTexts ?: yTicks.map(yFormat::format)

    val pointFormat: ValueFormat = numericX?.let { it.pointFormat ?: it.format }
        ?: timeX?.let { it.pointFormat ?: it.defaultPointFormat }
        ?: Formats.Readable

    private val lastDefinedBySeries: Map<Pair<Int, Int>, Int> = HashMap<Pair<Int, Int>, Int>().also { map ->
        spec.marks.forEachIndexed { mIdx, mark ->
            if (mark is LineMark && mark.markers == Markers.Last) {
                mark.series.forEachIndexed { sIdx, s ->
                    val n = min(s.values.size, positions)
                    var last = -1
                    for (i in n - 1 downTo 0) {
                        if (s.values[i].isFinite()) { last = i; break }
                    }
                    map[mIdx to sIdx] = last
                }
            }
        }
    }

    fun lastDefined(markIndex: Int, seriesIndex: Int): Int = lastDefinedBySeries[markIndex to seriesIndex] ?: -1

    private val xLabelCache: Array<String?> = arrayOfNulls(positions)

    fun xLabel(i: Int): String = categories?.get(i)
        ?: xLabelCache[i]
        ?: pointFormat.format(xValues!![i]).also { xLabelCache[i] = it }

    private val valueTextCache: Array<Array<Array<String?>>?> = Array(spec.marks.size) { mIdx ->
        val seriesList = when (val mark = spec.marks[mIdx]) {
            is BarMark -> mark.series
            is LineMark -> mark.series
            is AreaMark -> mark.series
            else -> null
        }
        seriesList?.let { list -> Array(list.size) { sIdx -> arrayOfNulls(min(list[sIdx].values.size, positions)) } }
    }

    val seriesArrays: Array<Array<DoubleArray>?> = Array(spec.marks.size) { mIdx ->
        val seriesList = when (val mark = spec.marks[mIdx]) {
            is BarMark -> mark.series
            is LineMark -> mark.series
            is AreaMark -> mark.series
            else -> null
        }
        seriesList?.let { list -> Array(list.size) { sIdx -> list[sIdx].values.toDoubleArray() } }
    }

    val stackUpperArrays: Map<Int, Array<DoubleArray>> = HashMap<Int, Array<DoubleArray>>().also { map ->
        spec.marks.forEachIndexed { mIdx, mark ->
            if (mark is AreaMark && mark.stacked) {
                val st = stacks[mIdx] ?: return@forEachIndexed
                val sArrs = seriesArrays[mIdx] ?: return@forEachIndexed
                map[mIdx] = Array(mark.series.size) { sIdx ->
                    val raw = sArrs[sIdx]
                    val sStack = st[sIdx]
                    DoubleArray(raw.size) { idx -> if (!raw[idx].isFinite()) Double.NaN else sStack[idx].second }
                }
            }
        }
    }

    val stackLowerArrays: Map<Int, Array<DoubleArray>> = HashMap<Int, Array<DoubleArray>>().also { map ->
        spec.marks.forEachIndexed { mIdx, mark ->
            if (mark is AreaMark && mark.stacked) {
                val st = stacks[mIdx] ?: return@forEachIndexed
                val sArrs = seriesArrays[mIdx] ?: return@forEachIndexed
                map[mIdx] = Array(mark.series.size) { sIdx ->
                    val raw = sArrs[sIdx]
                    val sStack = st[sIdx]
                    DoubleArray(raw.size) { idx -> if (!raw[idx].isFinite()) Double.NaN else sStack[idx].first }
                }
            }
        }
    }

    fun valueText(markIndex: Int, seriesIndex: Int, datumIndex: Int, value: Double, format: ValueFormat, cacheCustom: Boolean): String {
        if (!cacheCustom) return format.format(value)
        val cache = valueTextCache[markIndex]?.get(seriesIndex) ?: return format.format(value)
        return cache[datumIndex] ?: format.format(value).also { cache[datumIndex] = it }
    }
}

/** Smallest index in `0..values.size` with `values[i] >= target`. */
private fun lowerBound(values: DoubleArray, target: Double): Int {
    var lo = 0
    var hi = values.size
    while (lo < hi) {
        val mid = (lo + hi) ushr 1
        if (values[mid] < target) lo = mid + 1 else hi = mid
    }
    return lo
}

/** Smallest index in `0..values.size` with `values[i] > target`. */
private fun upperBound(values: DoubleArray, target: Double): Int {
    var lo = 0
    var hi = values.size
    while (lo < hi) {
        val mid = (lo + hi) ushr 1
        if (values[mid] <= target) lo = mid + 1 else hi = mid
    }
    return lo
}

/**
 * M4 per-pixel-column decimation over `start until endExclusive`.
 *
 * Keeps any overhang point outside `[winStart, winEndExclusive)`, and inside the visible window
 * groups indices by integer pixel column `col = (sx(xValues[i]) - plotLeft).toInt().coerceIn(0, cols - 1)`.
 * Within each column (and each contiguous defined run inside a column), emits up to four real
 * sample indices in ascending order: `first`, `min`, `max` and `last`, plus one `NaN` sample for
 * any missing-data gap so paths still break at gaps.
 */
private fun flushColumn(out: IntArray, startK: Int, firstIdx: Int, minIdx: Int, maxIdx: Int, lastIdx: Int): Int {
    var k = startK
    out[k++] = firstIdx
    val loIdx = min(minIdx, maxIdx)
    val hiIdx = max(minIdx, maxIdx)
    if (loIdx > firstIdx && loIdx < lastIdx) out[k++] = loIdx
    if (hiIdx > loIdx && hiIdx < lastIdx) out[k++] = hiIdx
    if (lastIdx > firstIdx) out[k++] = lastIdx
    return k
}

private fun flushStackedColumn(
    out: IntArray,
    startK: Int,
    firstIdx: Int,
    minIdx: Int,
    maxIdx: Int,
    lowMinIdx: Int,
    lowMaxIdx: Int,
    lastIdx: Int,
): Int {
    var k = startK
    out[k++] = firstIdx
    var prev = firstIdx
    while (true) {
        var next = lastIdx
        if (minIdx > prev && minIdx < next) next = minIdx
        if (maxIdx > prev && maxIdx < next) next = maxIdx
        if (lowMinIdx > prev && lowMinIdx < next) next = lowMinIdx
        if (lowMaxIdx > prev && lowMaxIdx < next) next = lowMaxIdx
        if (next >= lastIdx) break
        out[k++] = next
        prev = next
    }
    if (lastIdx > prev) out[k++] = lastIdx
    return k
}

/**
 * Finds the index range `[start, endExclusive)` to compile for a series of length [n] under
 * [viewport] culling and [gaps]: includes `[inStart..inEnd]`, plus one neighbor beyond each edge
 * (scanning past leading/trailing `NaN`s when [gaps] connects across missing samples so connected
 * segments run all the way to the plot border).
 */
private fun culledSeriesWindow(
    n: Int,
    inStart: Int,
    inEnd: Int,
    cullStart: Int,
    cullEnd: Int,
    sValues: DoubleArray,
    gaps: Gaps,
    hasViewport: Boolean,
    xAscending: Boolean,
): Pair<Int, Int> {
    if (!hasViewport || !xAscending || n <= 0) return 0 to n
    if (gaps == Gaps.Break) {
        val s = min(cullStart, n)
        val e = min(cullEnd + 1, n)
        return s to max(s, e)
    }
    var left = min(inStart - 1, n - 1)
    while (left >= 0 && !sValues[left].isFinite()) left--
    val start = if (left >= 0) left else min(inStart.coerceAtLeast(0), n)
    var right = max(0, inEnd + 1)
    while (right < n && !sValues[right].isFinite()) right++
    val endExclusive = if (right < n) right + 1 else min(max(0, inEnd + 1), n)
    return min(start, endExclusive) to max(start, endExclusive)
}

/**
 * M4 per-pixel-column decimation over `start until endExclusive`, respecting [gaps]:
 *
 * - [Gaps.Break]: flushes the column and emits one `NaN` index per contiguous missing run.
 * - [Gaps.Connect]: skips `NaN`s so connected holes never turn into breaks.
 * - [Gaps.ConnectWithin]: connects across holes whose x span `abs(xCurr - xPrev) <= maxGap` and
 *   flushes + emits one `NaN` break across longer holes (`> maxGap`).
 *
 * Within each column (and each contiguous connected run), writes the distinct sample indices in
 * ascending order into [out]: `first`, `min`, `max` (plus `lowMin` and `lowMax` of [lowerValues]
 * when decimating a stacked area) and `last`. Returns the count written.
 */
private fun decimateM4Indices(
    start: Int,
    endExclusive: Int,
    winStart: Int,
    winEndExclusive: Int,
    xValues: DoubleArray,
    xDomainStart: Double,
    colScale: Double,
    cols: Int,
    sValues: DoubleArray,
    gaps: Gaps,
    out: IntArray,
    lowerValues: DoubleArray? = null,
): Int {
    val maxGap = (gaps as? Gaps.ConnectWithin)?.maxGap ?: Double.NaN
    var k = 0
    var lastDefined = -1
    var lastWasNaN = false

    for (i in start until winStart) {
        val v = sValues[i]
        if (v.isFinite()) {
            if (!maxGap.isNaN() && lastDefined >= 0 && i > lastDefined + 1 && abs(xValues[i] - xValues[lastDefined]) > maxGap) {
                out[k++] = lastDefined + 1
            }
            out[k++] = i
            lastDefined = i
            lastWasNaN = false
        } else if (gaps == Gaps.Break && !lastWasNaN) {
            out[k++] = i
            lastWasNaN = true
        }
    }

    var currentCol = -1
    var firstIdx = -1
    var lastIdx = -1
    var minIdx = -1
    var maxIdx = -1
    var minVal = Double.POSITIVE_INFINITY
    var maxVal = Double.NEGATIVE_INFINITY
    var lowMinIdx = -1
    var lowMaxIdx = -1
    var lowMinVal = Double.POSITIVE_INFINITY
    var lowMaxVal = Double.NEGATIVE_INFINITY

    for (i in winStart until winEndExclusive) {
        val v = sValues[i]
        if (!v.isFinite()) {
            if (gaps == Gaps.Break) {
                if (firstIdx >= 0) {
                    k = if (lowerValues == null) {
                        flushColumn(out, k, firstIdx, minIdx, maxIdx, lastIdx)
                    } else {
                        flushStackedColumn(out, k, firstIdx, minIdx, maxIdx, lowMinIdx, lowMaxIdx, lastIdx)
                    }
                    firstIdx = -1
                    minVal = Double.POSITIVE_INFINITY
                    maxVal = Double.NEGATIVE_INFINITY
                    lowMinVal = Double.POSITIVE_INFINITY
                    lowMaxVal = Double.NEGATIVE_INFINITY
                }
                if (!lastWasNaN) {
                    out[k++] = i
                    lastWasNaN = true
                }
            }
            continue
        }
        if (!maxGap.isNaN() && lastDefined >= 0 && i > lastDefined + 1 && abs(xValues[i] - xValues[lastDefined]) > maxGap) {
            if (firstIdx >= 0) {
                k = if (lowerValues == null) {
                    flushColumn(out, k, firstIdx, minIdx, maxIdx, lastIdx)
                } else {
                    flushStackedColumn(out, k, firstIdx, minIdx, maxIdx, lowMinIdx, lowMaxIdx, lastIdx)
                }
                firstIdx = -1
                minVal = Double.POSITIVE_INFINITY
                maxVal = Double.NEGATIVE_INFINITY
                lowMinVal = Double.POSITIVE_INFINITY
                lowMaxVal = Double.NEGATIVE_INFINITY
            }
            out[k++] = lastDefined + 1
            currentCol = -1
        }
        var col = ((xValues[i] - xDomainStart) * colScale).toInt()
        if (col < 0) col = 0 else if (col >= cols) col = cols - 1
        if (col != currentCol) {
            if (firstIdx >= 0) {
                k = if (lowerValues == null) {
                    flushColumn(out, k, firstIdx, minIdx, maxIdx, lastIdx)
                } else {
                    flushStackedColumn(out, k, firstIdx, minIdx, maxIdx, lowMinIdx, lowMaxIdx, lastIdx)
                }
                firstIdx = -1
                minVal = Double.POSITIVE_INFINITY
                maxVal = Double.NEGATIVE_INFINITY
                lowMinVal = Double.POSITIVE_INFINITY
                lowMaxVal = Double.NEGATIVE_INFINITY
            }
            currentCol = col
        }
        lastWasNaN = false
        if (firstIdx < 0) firstIdx = i
        lastIdx = i
        if (v < minVal) {
            minVal = v
            minIdx = i
        }
        if (v > maxVal) {
            maxVal = v
            maxIdx = i
        }
        if (lowerValues != null) {
            val lv = lowerValues[i]
            if (lv < lowMinVal) {
                lowMinVal = lv
                lowMinIdx = i
            }
            if (lv > lowMaxVal) {
                lowMaxVal = lv
                lowMaxIdx = i
            }
        }
        lastDefined = i
    }
    if (firstIdx >= 0) {
        k = if (lowerValues == null) {
            flushColumn(out, k, firstIdx, minIdx, maxIdx, lastIdx)
        } else {
            flushStackedColumn(out, k, firstIdx, minIdx, maxIdx, lowMinIdx, lowMaxIdx, lastIdx)
        }
        lastWasNaN = false
    }
    for (i in winEndExclusive until endExclusive) {
        val v = sValues[i]
        if (v.isFinite()) {
            if (!maxGap.isNaN() && lastDefined >= 0 && i > lastDefined + 1 && abs(xValues[i] - xValues[lastDefined]) > maxGap) {
                out[k++] = lastDefined + 1
            }
            out[k++] = i
            lastDefined = i
            lastWasNaN = false
        } else if (gaps == Gaps.Break && !lastWasNaN) {
            out[k++] = i
            lastWasNaN = true
        }
    }
    return k
}

private fun compileCartesian(
    spec: CartesianChart,
    size: Extent,
    measure: TextMeasure,
    m: ChartMetrics,
    viewport: ViewportRange? = null,
): ChartScene {
    val prep = spec.prep
    val categories = prep.categories
    val numericX = prep.numericX
    val timeX = prep.timeX
    // Continuous x positions that series values align to; null for a category axis.
    val xValues: DoubleArray? = prep.xValues
    val positions = prep.positions
    val xVisible = spec.x.visible
    val yVisible = spec.y.visible
    val hasBars = prep.hasBars
    // Bars and candles take slots: bands on a category axis, half a slot of room at the ends of a
    // continuous one.
    val hasSlots = prep.hasSlots
    // Slot padding: the bars' when there are bars, else the candles'.
    val slotPadding = if (hasBars) m.bandPadding else m.candlePadding
    val candles = prep.candles
    val firstSeries = prep.firstSeries
    val stacks = prep.stacks
    val hasLength = prep.hasLength
    val yBase = prep.yBaseScale
    val yTicks = prep.yTicks
    val yTexts = prep.yTexts()
    val valueFormat = spec.y.valueFormat ?: Formats.Readable
    val cacheValueTexts = spec.y.valueFormat == null || viewport != null

    // 2. Continuous x domain. Bars get half a slot of room at each end so the outer bars fit.
    val xStep = prep.xStep
    val xBase: LinearScale? = if (categories != null) null else if (viewport != null) {
        LinearScale(viewport.start, viewport.end)
    } else {
        var xLo = prep.xDataLo
        var xHi = prep.xDataHi
        if (!xLo.isFinite()) { xLo = 0.0; xHi = 1.0 }
        val unit = if (timeX != null) DAY_MS else 1.0
        if (hasSlots) {
            val half = (if (xStep > 0) xStep else unit) / 2
            xLo -= half
            xHi += half
        }
        xLo = numericX?.min ?: timeX?.min?.toEpochMilli()?.toDouble() ?: xLo
        xHi = numericX?.max ?: timeX?.max?.toEpochMilli()?.toDouble() ?: xHi
        if (xLo == xHi) { xLo -= unit / 2; xHi += unit / 2 }
        val raw = LinearScale(xLo, xHi)
        // Scatter plots read better against round extents; series should run edge to edge.
        if (numericX != null && prep.dotsOnly && numericX.min == null && numericX.max == null) raw.nice(numericX.tickCount) else raw
    }

    // 3. Margins from measured labels. A hidden axis measures nothing and keeps only enough room
    // for what overhangs the data: half the widest stroke, or a marker or dot when there are any.
    var inset = m.strokeInset
    if (prep.hasMarkers) inset = max(inset, m.markerRadius)
    if (prep.hasDots) inset = max(inset, m.dotRadius)
    val ySizes = yTexts.map { measure.measure(it, TextKind.TickY) }
    val catSizes = if (categories != null && xVisible) categories.map { measure.measure(it, TextKind.TickX) } else emptyList()
    val tickHeight = if (xVisible) measure.measure("0", TextKind.TickX).height else 0f
    val usesBands = categories != null && hasSlots
    var left = if (yVisible) (ySizes.maxOfOrNull { it.width } ?: 0f) + m.labelGap else inset
    var right = when {
        !xVisible -> inset
        categories == null -> m.markerRadius
        else -> 0f
    }
    if (categories != null && !usesBands && xVisible) {
        // Points sit on the plot edges, so their labels overhang it.
        left = max(left, (catSizes.firstOrNull()?.width ?: 0f) / 2f)
        right = (catSizes.lastOrNull()?.width ?: 0f) / 2f
    }
    val halfLabel = (ySizes.firstOrNull()?.height ?: 0f) / 2f
    val top = if (yVisible) max(halfLabel, m.markerRadius) else inset
    val bottom = if (xVisible) tickHeight + m.labelGap else max(halfLabel, inset)
    val plot = Bounds(left, top, size.width - right, size.height - bottom)
    if (plot.width <= 0f || plot.height <= 0f) return emptyScene(size, spec, clipPlot = viewport != null)

    // A volume strip, when a candle mark asks for one, takes the bottom of the plot; the value
    // axis (prices, and every other mark) keeps the rest above a gap.
    val stripMark = prep.stripMark
    val strip = if (stripMark < 0) 0f else plot.height * (spec.marks[stripMark] as CandleMark).volumeFraction
    val priceBottom = if (stripMark < 0) plot.bottom else plot.bottom - strip - m.volumeGap
    if (priceBottom <= plot.top) return emptyScene(size, spec, clipPlot = viewport != null)

    val y = yBase.withRange(priceBottom, plot.top)
    val x = xBase?.withRange(plot.left, plot.right)
    val band = categories?.let {
        val outer = if (hasBars) m.bandOuterPadding else m.candlePadding / 2f
        if (usesBands) BandScale(it.size, plot.left, plot.right, slotPadding, outer)
        else BandScale.point(it.size, plot.left, plot.right)
    }
    val zeroY = y(0.0).coerceIn(plot.top, priceBottom)
    // A stack bound of zero is the baseline, which stays in the plot even when zero is not.
    fun yStack(v: Double): Float = if (v == 0.0) zeroY else y(v)

    // Where position i sits, and the slot a bar there may fill.
    fun xAt(i: Int): Float = band?.center(i) ?: x!!(xValues!![i])
    val slotWidth: Float = band?.bandwidth ?: x?.let { sx ->
        val stepPx = if (xStep > 0) sx(sx.domainStart + xStep) - sx(sx.domainStart) else plot.width
        stepPx * (1f - slotPadding)
    } ?: 0f
    fun slotStart(i: Int): Float = band?.start(i) ?: (xAt(i) - slotWidth / 2f)
    val pointFormat: ValueFormat = prep.pointFormat
    fun xLabel(i: Int): String = prep.xLabel(i)
    // The raw x a focus reports: data units on a continuous axis, nothing for categories.
    val boxedX = prep.boxedDomainX
    fun domainX(i: Int): Double? = boxedX?.get(i)

    // Binary-search culling on ascending continuous x positions when a viewport is set: keep
    // points inside [viewLo, viewHi] for focus, plus one point beyond each edge so lines and areas
    // run to the plot edge.
    val viewLo = if (viewport != null) min(viewport.start, viewport.end) else Double.NEGATIVE_INFINITY
    val viewHi = if (viewport != null) max(viewport.start, viewport.end) else Double.POSITIVE_INFINITY
    val inStart: Int
    val inEnd: Int
    val cullStart: Int
    val cullEnd: Int
    if (viewport != null && xValues != null && positions > 0 && prep.xAscending) {
        inStart = lowerBound(xValues, viewLo)
        inEnd = upperBound(xValues, viewHi) - 1
        cullStart = (inStart - 1).coerceAtLeast(0)
        cullEnd = (inEnd + 1).coerceAtMost(positions - 1)
    } else {
        inStart = 0
        inEnd = positions - 1
        cullStart = 0
        cullEnd = positions - 1
    }

    val plotCols = max(1, ceil(plot.width).toInt())
    val visibleSpanCount = max(0, inEnd - inStart + 1)
    val guides = ArrayList<Node>(yTicks.size + 12)
    val marks = ArrayList<Node>(max(16, prep.totalSeries * 2))
    val labels = ArrayList<Node>(yTicks.size + 12)
    val points = ArrayList<FocusPoint>(max(16, min(visibleSpanCount, plotCols * 4) * prep.totalSeries))

    // 4. Guides and tick labels.
    yTicks.forEachIndexed { i, v ->
        val py = y(v)
        guides += RuleNode("grid-y:${yTexts[i]}", XY(plot.left, py), XY(plot.right, py), RuleKind.GridY)
        labels += TextNode("tick-y:${yTexts[i]}", yTexts[i], XY(plot.left - m.labelGap, py), HAlign.End, VAlign.Center, TextKind.TickY)
    }
    if (xVisible && band != null) {
        val widest = catSizes.maxOfOrNull { it.width } ?: 0f
        val stride = if (band.step <= 0f) 1 else max(1, ceil((widest + m.minLabelSpacing) / band.step).toInt())
        categories.indices.forEach { i ->
            guides += RuleNode("grid-x:$i", XY(band.center(i), plot.top), XY(band.center(i), plot.bottom), RuleKind.GridX)
            if (i % stride == 0) {
                labels += TextNode("tick-x:$i", categories[i], XY(band.center(i), plot.bottom + m.labelGap), HAlign.Center, VAlign.Top, TextKind.TickX)
            }
        }
    } else if (xVisible && x != null) {
        // Fewer ticks until the widest label fits between neighbours.
        var count = numericX?.tickCount ?: timeX?.tickCount ?: 5
        var ticks: List<Double>
        var format: ValueFormat
        var sizes: List<Extent>
        if (viewport != null) {
            // Under a viewport, choose count from the window's span and a reference label width so
            // the tick interval stays constant while panning across month lengths or year boundaries.
            val span = kotlin.math.abs(x.domainEnd - x.domainStart)
            val sampleWidth = measure.measure(if (timeX != null) "Sep 30" else "000,000", TextKind.TickX).width
            while (count > 2) {
                val stepDomain = if (timeX != null) {
                    val iv = TimeTicks.interval(min(x.domainStart, x.domainEnd), max(x.domainStart, x.domainEnd), count)
                    when {
                        iv == null -> kotlin.math.abs(Ticks.tickStep(x.domainStart, x.domainEnd, count))
                        iv.unit == TimeTicks.Unit.Year -> iv.millis.toDouble() * iv.step
                        else -> iv.millis.toDouble()
                    }
                } else {
                    kotlin.math.abs(Ticks.tickStep(x.domainStart, x.domainEnd, count))
                }
                val stepPx = if (span > 0.0) (stepDomain / span).toFloat() * plot.width else Float.POSITIVE_INFINITY
                if (stepPx >= sampleWidth + m.minLabelSpacing) break
                count--
            }
            var tickTexts: List<String>
            while (true) {
                ticks = if (timeX != null) TimeTicks.ticks(x.domainStart, x.domainEnd, count, timeX.zone) else x.ticks(count)
                format = numericX?.format ?: timeX?.format ?: if (timeX != null) timeX.defaultTickFormat else Formats.forTicks(ticks)
                tickTexts = ticks.map(format::format)
                val customFormat = numericX?.format != null || timeX?.format != null
                if (!customFormat || count <= 2) {
                    sizes = emptyList()
                    break
                }
                sizes = tickTexts.map { measure.measure(it, TextKind.TickX) }
                val widest = sizes.maxOfOrNull { it.width } ?: 0f
                val gap = ticks.zipWithNext { a, b -> x(b) - x(a) }.minOrNull() ?: Float.POSITIVE_INFINITY
                if (gap >= widest + m.minLabelSpacing) break
                count--
            }
            ticks.forEachIndexed { i, v ->
                val px = x(v)
                guides += RuleNode("grid-x:$v", XY(px, plot.top), XY(px, plot.bottom), RuleKind.GridX)
                labels += TextNode("tick-x:$v", tickTexts[i], XY(px, plot.bottom + m.labelGap), HAlign.Center, VAlign.Top, TextKind.TickX)
            }
        } else {
            var tickTexts: List<String>
            while (true) {
                ticks = if (timeX != null) TimeTicks.ticks(x.domainStart, x.domainEnd, count, timeX.zone) else x.ticks(count)
                format = numericX?.format ?: timeX?.format ?: if (timeX != null) timeX.defaultTickFormat else Formats.forTicks(ticks)
                tickTexts = ticks.map(format::format)
                sizes = tickTexts.map { measure.measure(it, TextKind.TickX) }
                val widest = sizes.maxOfOrNull { it.width } ?: 0f
                val gap = ticks.zipWithNext { a, b -> x(b) - x(a) }.minOrNull() ?: Float.POSITIVE_INFINITY
                if (count <= 2 || gap >= widest + m.minLabelSpacing) break
                count--
            }
            ticks.forEachIndexed { i, v ->
                val px = x(v)
                val w = sizes[i].width
                guides += RuleNode("grid-x:$v", XY(px, plot.top), XY(px, plot.bottom), RuleKind.GridX)
                // Labels near the edges slide inward rather than leaving the canvas.
                val lx = px.coerceIn(w / 2f, max(w / 2f, size.width - w / 2f))
                labels += TextNode("tick-x:$v", tickTexts[i], XY(lx, plot.bottom + m.labelGap), HAlign.Center, VAlign.Top, TextKind.TickX)
            }
        }
    }
    if (xVisible) guides += RuleNode("axis-x", XY(plot.left, plot.bottom), XY(plot.right, plot.bottom), RuleKind.AxisX)
    if (yVisible) guides += RuleNode("axis-y", XY(plot.left, plot.top), XY(plot.left, priceBottom), RuleKind.AxisY)
    if (hasLength && y.domainStart <= 0.0 && y.domainEnd >= 0.0) {
        guides += RuleNode("baseline", XY(plot.left, zeroY), XY(plot.right, zeroY), RuleKind.Baseline)
    }

    // 5. Marks. Bands go behind everything, then area fills so lines and bars are never buried.
    val ordered = prep.orderedMarks
    val areaEdges = mutableListOf<Node>()
    val lastArea = prep.lastArea
    val xDomLo = x?.let { min(it.domainStart, it.domainEnd) } ?: Double.NaN
    val xDomHi = x?.let { max(it.domainStart, it.domainEnd) } ?: Double.NaN
    // Reserve horizontal RuleMark label boxes before placing XBandMark and XRuleMark labels.
    val refLabelBoxes = mutableListOf<Bounds>()
    spec.marks.forEach { mark ->
        if (mark is RuleMark && mark.label != null) {
            val s = measure.measure(mark.label, TextKind.Reference)
            val rx = plot.right - m.labelGap
            val ry = y(mark.y) - m.labelGap
            refLabelBoxes += Bounds(rx - s.width, ry - s.height, rx, ry)
        }
    }
    fun referenceFits(box: Bounds): Boolean {
        if (box.left < 0f || box.right > size.width) return false
        return refLabelBoxes.none { existing ->
            box.top < existing.bottom && box.bottom > existing.top &&
                box.left < existing.right + m.minLabelSpacing && box.right + m.minLabelSpacing > existing.left
        }
    }
    fun Series.at(i: Int): Double {
        if (i >= values.size) return Double.NaN
        val v = values[i]
        return if (v.isFinite()) v else Double.NaN
    }
    fun formatVal(markIndex: Int, s: Int, i: Int, v: Double): String =
        prep.valueText(markIndex, s, i, v, valueFormat, cacheValueTexts)
    fun inWindow(i: Int): Boolean = viewport == null || (i in inStart..inEnd && (xValues == null || xValues[i] in viewLo..viewHi))
    fun buildUndecimatedPoints(
        start: Int,
        endExclusive: Int,
        sValues: DoubleArray,
        gaps: Gaps,
        yFor: (Int, Double) -> Float,
    ): List<XY> {
        val count = max(0, endExclusive - start)
        if (gaps == Gaps.Break) {
            val out = ArrayList<XY>(count)
            for (i in start until endExclusive) {
                val raw = sValues[i]
                out += XY(xAt(i), if (raw.isFinite()) yFor(i, raw) else Float.NaN)
            }
            return out
        }
        val out = ArrayList<XY>(count)
        var lastDefined = -1
        for (i in start until endExclusive) {
            val raw = sValues[i]
            if (!raw.isFinite()) continue
            if (lastDefined >= 0 && i > lastDefined + 1 && gaps is Gaps.ConnectWithin) {
                val xPrev = xValues?.get(lastDefined) ?: lastDefined.toDouble()
                val xCurr = xValues?.get(i) ?: i.toDouble()
                if (abs(xCurr - xPrev) > gaps.maxGap) {
                    for (k in (lastDefined + 1) until i) out += XY(xAt(k), Float.NaN)
                }
            }
            out += XY(xAt(i), yFor(i, raw))
            lastDefined = i
        }
        return out
    }
    fun capped(start: Float, width: Float, cap: Float): Pair<Float, Float> =
        if (width > cap) (start + (width - cap) / 2f) to cap else start to width
    for (markIndex in ordered) {
        val mark = spec.marks[markIndex]
        val first = firstSeries[markIndex]
        val stack = stacks[markIndex]
        when (mark) {
            is BarMark -> {
                val barEnd = min(cullEnd, positions - 1)
                if (stack == null) {
                    val k = mark.series.size
                    // Cap the group, not each bar, so capped bars stay together.
                    val slotCap = if (k > 1) m.maxBarWidth / (1f - m.groupPadding) else m.maxBarWidth
                    for (i in cullStart..barEnd) {
                        val (gStart, gWidth) = capped(slotStart(i), slotWidth, slotCap * k)
                        if (viewport != null && (gStart + gWidth < plot.left || gStart > plot.right)) continue
                        // A lone series fills its slot; padding only separates bars within a group.
                        val inner = BandScale(k, gStart, gStart + gWidth, if (k > 1) m.groupPadding else 0f, 0f)
                        mark.series.forEachIndexed { s, series ->
                            val v = series.at(i)
                            if (v.isNaN()) return@forEachIndexed
                            val l = inner.start(s)
                            val py = y(v)
                            val rect = Bounds(l, min(zeroY, py), l + inner.bandwidth, max(zeroY, py))
                            if (viewport != null && (rect.right < plot.left || rect.left > plot.right)) return@forEachIndexed
                            marks += RectNode("bar:${series.name}:$i", rect, first + s, i, if (v >= 0) Edge.Bottom else Edge.Top, kind = RectKind.Bar, value = v)
                            if (inWindow(i)) {
                                points += FocusPoint(first + s, i, MarkKind.Bar, rect.centerX, py, xAt(i), xLabel(i), v, formatVal(markIndex, s, i, v), hit = RectHit(rect), domainX = domainX(i))
                            }
                        }
                    }
                } else {
                    for (i in cullStart..barEnd) {
                        val (l, w) = capped(slotStart(i), slotWidth, m.maxBarWidth)
                        if (viewport != null && (l + w < plot.left || l > plot.right)) continue
                        val outermost = mark.series.indices.filter { s -> mark.series[s].at(i).let { !it.isNaN() && it != 0.0 } }
                            .groupBy { s -> mark.series[s].at(i) >= 0 }
                            .values.map { it.last() }.toSet()
                        mark.series.forEachIndexed { s, series ->
                            val v = series.at(i)
                            if (v.isNaN() || v == 0.0) return@forEachIndexed
                            val (v0, v1) = stack[s][i]
                            val base = if (v >= 0) Edge.Bottom else Edge.Top
                            var r = Bounds(l, min(yStack(v0), yStack(v1)), l + w, max(yStack(v0), yStack(v1)))
                            if (v0 != 0.0 && v1 != 0.0 && r.height > m.stackGap) {
                                r = if (base == Edge.Bottom) r.copy(bottom = r.bottom - m.stackGap) else r.copy(top = r.top + m.stackGap)
                            }
                            val end = if (v >= 0) yStack(v1) else yStack(v0)
                            marks += RectNode("bar:${series.name}:$i", r, first + s, i, base, isEnd = s in outermost, kind = RectKind.Bar, value = v)
                            if (inWindow(i)) {
                                points += FocusPoint(first + s, i, MarkKind.Bar, r.centerX, end, xAt(i), xLabel(i), v, formatVal(markIndex, s, i, v), hit = RectHit(r), domainX = domainX(i))
                            }
                        }
                    }
                }
            }
            is LineMark -> {
                val sx = x
                val decimateMark = viewport != null && categories == null && xValues != null && sx != null && prep.xAscending && visibleSpanCount > plotCols
                val xDomainSpan = if (sx != null) sx.domainEnd - sx.domainStart else 0.0
                val colScale = if (xDomainSpan != 0.0) plot.width / xDomainSpan else 0.0
                val markArrays = prep.seriesArrays[markIndex]!!
                val m4Buf = if (decimateMark) IntArray(min(visibleSpanCount, plotCols * 6) + 4) else null
                val hasConnectedGaps = mark.gaps != Gaps.Break
                mark.series.forEachIndexed { s, series ->
                    val sValues = markArrays[s]
                    val n = min(sValues.size, positions)
                    val (start, endExclusive) = culledSeriesWindow(
                        n, inStart, inEnd, cullStart, cullEnd, sValues, mark.gaps, viewport != null, prep.xAscending,
                    )
                    val winStart = if (viewport != null && prep.xAscending) max(start, inStart) else start
                    val winEndExclusive = if (viewport != null && prep.xAscending) min(endExclusive, inEnd + 1) else endExclusive
                    val winCount = max(0, winEndExclusive - winStart)
                    if (decimateMark && winCount > plotCols && m4Buf != null) {
                        val keptCount = decimateM4Indices(
                            start, endExclusive, winStart, winEndExclusive,
                            xValues, sx.domainStart, colScale, plotCols, sValues, mark.gaps, m4Buf,
                        )
                        val pts = ArrayList<XY>(keptCount)
                        var lastFocusCol = -1
                        var lastDefinedIdx = -1
                        for (i in winEndExclusive - 1 downTo winStart) {
                            if (sValues[i].isFinite()) {
                                lastDefinedIdx = i
                                break
                            }
                        }
                        for (k in 0 until keptCount) {
                            val i = m4Buf[k]
                            val raw = sValues[i]
                            val px = sx(xValues[i])
                            val defined = raw.isFinite()
                            val pt = XY(px, if (defined) y(raw) else Float.NaN)
                            pts += pt
                            if (defined && i in inStart..inEnd) {
                                val col = ((xValues[i] - sx.domainStart) * colScale).toInt().coerceIn(0, plotCols - 1)
                                if (hasConnectedGaps || col != lastFocusCol || i == lastDefinedIdx) {
                                    lastFocusCol = col
                                    points += FocusPoint(
                                        first + s, i, MarkKind.Line, pt.x, pt.y, pt.x,
                                        prep.xLabel(i), raw, prep.valueText(markIndex, s, i, raw, valueFormat, cacheValueTexts),
                                        domainX = boxedX?.get(i),
                                    )
                                }
                            }
                        }
                        marks += LineNode("line:${series.name}", pts, first + s, mark.curve, pattern = mark.pattern)
                        when (mark.markers) {
                            Markers.None -> Unit
                            Markers.Last -> {
                                val lastIdx = prep.lastDefined(markIndex, s)
                                if (lastIdx in start until endExclusive) {
                                    val pt = XY(sx(xValues[lastIdx]), y(sValues[lastIdx]))
                                    marks += DotNode("marker:${series.name}", pt, m.markerRadius, first + s, lastIdx, isMarker = true)
                                }
                            }
                            Markers.All -> {
                                for (k in 0 until keptCount) {
                                    val pt = pts[k]
                                    if (pt.isDefined) {
                                        marks += DotNode("marker:${series.name}:${m4Buf[k]}", pt, m.markerRadius, first + s, m4Buf[k], isMarker = true)
                                    }
                                }
                            }
                        }
                    } else {
                        val pts = buildUndecimatedPoints(start, endExclusive, sValues, mark.gaps) { _, v -> y(v) }
                        marks += LineNode("line:${series.name}", pts, first + s, mark.curve, pattern = mark.pattern)
                        when (mark.markers) {
                            Markers.None -> Unit
                            Markers.Last -> {
                                val lastIdx = prep.lastDefined(markIndex, s)
                                if (lastIdx in start until endExclusive) {
                                    val pt = XY(xAt(lastIdx), y(sValues[lastIdx]))
                                    marks += DotNode("marker:${series.name}", pt, m.markerRadius, first + s, lastIdx, isMarker = true)
                                }
                            }
                            Markers.All -> {
                                for (i in start until endExclusive) {
                                    val raw = sValues[i]
                                    if (raw.isFinite()) {
                                        marks += DotNode("marker:${series.name}:$i", XY(xAt(i), y(raw)), m.markerRadius, first + s, i, isMarker = true)
                                    }
                                }
                            }
                        }
                        val checkX = viewport != null && !prep.xAscending && xValues != null
                        for (i in winStart until winEndExclusive) {
                            val raw = sValues[i]
                            if (!raw.isFinite()) continue
                            if (checkX && (xValues[i] < viewLo || xValues[i] > viewHi)) continue
                            val px = xAt(i)
                            val py = y(raw)
                            points += FocusPoint(
                                first + s, i, MarkKind.Line, px, py, px,
                                prep.xLabel(i), raw, prep.valueText(markIndex, s, i, raw, valueFormat, cacheValueTexts),
                                domainX = boxedX?.get(i),
                            )
                        }
                    }
                }
            }
            is AreaMark -> {
                val sx = x
                val decimateMark = viewport != null && categories == null && xValues != null && sx != null && prep.xAscending && visibleSpanCount > plotCols
                val xDomainSpan = if (sx != null) sx.domainEnd - sx.domainStart else 0.0
                val colScale = if (xDomainSpan != 0.0) plot.width / xDomainSpan else 0.0
                val markArrays = prep.seriesArrays[markIndex]!!
                val markUpperArrays = prep.stackUpperArrays[markIndex]
                val markLowerArrays = prep.stackLowerArrays[markIndex]
                val m4Buf = if (decimateMark) IntArray(min(visibleSpanCount, plotCols * 6) + 4) else null
                val hasConnectedGaps = mark.gaps != Gaps.Break
                // Unclamped: with zero cropped out of the domain, every value is on one side of it.
                val split = if (mark.diverging) SignSplit(y(0.0), negativeBelow = y.domainEnd > y.domainStart) else null
                // The row the fill closes on: zero (clamped), or the bottom of the price area.
                val floor = if (mark.base == AreaBase.PlotBottom) priceBottom else null
                val closeY = floor ?: zeroY
                mark.series.forEachIndexed { s, series ->
                    val sValues = markArrays[s]
                    val n = min(sValues.size, positions)
                    val (start, endExclusive) = culledSeriesWindow(
                        n, inStart, inEnd, cullStart, cullEnd, sValues, mark.gaps, viewport != null, prep.xAscending,
                    )
                    val winStart = if (viewport != null && prep.xAscending) max(start, inStart) else start
                    val winEndExclusive = if (viewport != null && prep.xAscending) min(endExclusive, inEnd + 1) else endExclusive
                    val winCount = max(0, winEndExclusive - winStart)
                    val sStack = stack?.get(s)
                    if (decimateMark && winCount > plotCols && m4Buf != null) {
                        val upperVals = markUpperArrays?.get(s) ?: sValues
                        val lowerVals = markLowerArrays?.get(s)
                        val keptCount = decimateM4Indices(
                            start, endExclusive, winStart, winEndExclusive,
                            xValues, sx.domainStart, colScale, plotCols, upperVals, mark.gaps, m4Buf, lowerVals,
                        )
                        val top = ArrayList<XY>(keptCount)
                        val bot = ArrayList<XY>(keptCount)
                        var lastFocusCol = -1
                        var lastDefinedIdx = -1
                        for (i in winEndExclusive - 1 downTo winStart) {
                            if (sValues[i].isFinite()) {
                                lastDefinedIdx = i
                                break
                            }
                        }
                        for (k in 0 until keptCount) {
                            val i = m4Buf[k]
                            val raw = sValues[i]
                            val px = sx(xValues[i])
                            if (!raw.isFinite()) {
                                top += XY(px, Float.NaN)
                                bot += XY(px, Float.NaN)
                            } else {
                                val bounds = sStack?.get(i)
                                val py = bounds?.second?.let(::yStack) ?: y(raw)
                                top += XY(px, py)
                                bot += XY(px, bounds?.first?.let(::yStack) ?: closeY)
                                if (i in inStart..inEnd) {
                                    val col = ((xValues[i] - sx.domainStart) * colScale).toInt().coerceIn(0, plotCols - 1)
                                    if (hasConnectedGaps || col != lastFocusCol || i == lastDefinedIdx) {
                                        lastFocusCol = col
                                        points += FocusPoint(
                                            first + s, i, MarkKind.Area, px, py, px,
                                            prep.xLabel(i), raw, prep.valueText(markIndex, s, i, raw, valueFormat, cacheValueTexts),
                                            domainX = boxedX?.get(i),
                                        )
                                    }
                                }
                            }
                        }
                        marks += AreaNode("area:${series.name}", top, bot, first + s, mark.curve, signSplit = split, floor = floor)
                        areaEdges += LineNode("area-edge:${series.name}", top, first + s, mark.curve, isAreaEdge = true, signSplit = split, floor = floor)
                    } else {
                        val top = buildUndecimatedPoints(start, endExclusive, sValues, mark.gaps) { i, v ->
                            sStack?.get(i)?.second?.let(::yStack) ?: y(v)
                        }
                        val bot = if (mark.gaps == Gaps.Break) {
                            val count = max(0, endExclusive - start)
                            val bList = ArrayList<XY>(count)
                            for (i in start until endExclusive) {
                                val raw = sValues[i]
                                bList += XY(xAt(i), if (!raw.isFinite()) Float.NaN else sStack?.get(i)?.first?.let(::yStack) ?: closeY)
                            }
                            bList
                        } else {
                            top.map { p -> XY(p.x, if (p.y.isFinite()) closeY else Float.NaN) }
                        }
                        marks += AreaNode("area:${series.name}", top, bot, first + s, mark.curve, signSplit = split, floor = floor)
                        areaEdges += LineNode("area-edge:${series.name}", top, first + s, mark.curve, isAreaEdge = true, signSplit = split, floor = floor)
                        for (i in winStart until winEndExclusive) {
                            val raw = sValues[i]
                            if (!raw.isFinite() || !inWindow(i)) continue
                            val px = xAt(i)
                            val py = sStack?.get(i)?.second?.let(::yStack) ?: y(raw)
                            points += FocusPoint(first + s, i, MarkKind.Area, px, py, px, xLabel(i), raw, formatVal(markIndex, s, i, raw), domainX = domainX(i))
                        }
                    }
                }
            }
            is DotMark -> {
                val sx = x ?: continue
                mark.series.forEachIndexed { s, series ->
                    series.points.forEachIndexed { j, d ->
                        if (!d.x.isFinite() || !d.y.isFinite()) return@forEachIndexed
                        val c = XY(sx(d.x), y(d.y))
                        if (viewport != null && (c.x + m.dotRadius < plot.left || c.x - m.dotRadius > plot.right)) return@forEachIndexed
                        marks += DotNode("dot:${series.name}:$j", c, m.dotRadius, first + s, j)
                        if (viewport == null || d.x in viewLo..viewHi) {
                            points += FocusPoint(first + s, j, MarkKind.Dot, c.x, c.y, c.x, pointFormat.format(d.x), d.y, valueFormat.format(d.y), domainX = d.x)
                        }
                    }
                }
            }
            is CandleMark -> {
                val periods = candles.getValue(markIndex)
                // The strip's own scale: zero on its floor, the largest volume at its top.
                val volumeY = if (markIndex != stripMark) null else {
                    val most = prep.maxVolume[markIndex] ?: 1.0
                    LinearScale(0.0, most, plot.bottom, plot.bottom - strip)
                }
                val candleEnd = min(cullEnd, periods.size - 1)
                for (i in cullStart..candleEnd) {
                    val c = periods[i] ?: continue // missing: the slot stays empty
                    val (l, w) = capped(slotStart(i), slotWidth, m.maxBarWidth)
                    if (viewport != null && (l + w < plot.left || l > plot.right)) continue
                    val cx = xAt(i) // exactly where a line's point at i sits
                    val down = c.isDown
                    val yOpen = y(c.open)
                    val yClose = y(c.close)
                    val yHigh = y(c.high)
                    val yLow = y(c.low)
                    if (volumeY != null && c.volume != null) {
                        val v = Bounds(l, volumeY(c.volume), l + w, plot.bottom)
                        marks += RectNode("volume:${mark.name}:$i", v, first, i, Edge.Bottom, kind = RectKind.Volume, value = c.volume, down = down)
                    }
                    marks += RuleNode("wick:${mark.name}:$i", XY(cx, yHigh), XY(cx, yLow), RuleKind.Wick, datum = i, down = down)
                    // The open's edge: the bottom of an up candle, unless the value axis is reversed.
                    val base = if (yOpen >= yClose) Edge.Bottom else Edge.Top
                    val body = Bounds(l, min(yOpen, yClose), l + w, max(yOpen, yClose))
                    marks += RectNode("candle:${mark.name}:$i", body, first, i, base, kind = RectKind.Candle, value = c.close, down = down)
                    if (inWindow(i)) {
                        val detail = CandleDetail(
                            c, valueFormat.format(c.open), valueFormat.format(c.high), valueFormat.format(c.low), valueFormat.format(c.close),
                            c.volume?.let(Formats.Readable::format),
                        )
                        val top = min(yHigh, yLow)
                        points += FocusPoint(
                            first, i, MarkKind.Candle, cx, top, xAt(i), xLabel(i), c.close, detail.close,
                            hit = RectHit(Bounds(l, top, l + w, max(yHigh, yLow))), domainX = domainX(i), candle = detail,
                        )
                    }
                }
            }
            is RuleMark -> {
                val py = y(mark.y)
                marks += RuleNode("ref:${mark.y}", XY(plot.left, py), XY(plot.right, py), RuleKind.Reference)
                mark.label?.let {
                    labels += TextNode("ref-label:${mark.y}", it, XY(plot.right - m.labelGap, py - m.labelGap), HAlign.End, VAlign.Bottom, TextKind.Reference)
                }
            }
            is XBandMark -> {
                val sx = x ?: continue
                if (mark.to < xDomLo || mark.from > xDomHi) continue
                val cFrom = mark.from.coerceIn(xDomLo, xDomHi)
                val cTo = mark.to.coerceIn(xDomLo, xDomHi)
                val bLeft = min(sx(cFrom), sx(cTo))
                val bRight = max(sx(cFrom), sx(cTo))
                if (bRight <= bLeft) continue
                val rect = Bounds(bLeft, plot.top, bRight, plot.bottom)
                marks += RectNode("x-band:${mark.from}:${mark.to}", rect, -1, -1, Edge.Bottom, kind = RectKind.Band, color = mark.color)
                mark.label?.takeIf { it.isNotEmpty() }?.let { text ->
                    val s = measure.measure(text, TextKind.Reference)
                    val cx = rect.centerX.coerceIn(s.width / 2f, max(s.width / 2f, size.width - s.width / 2f))
                    val ly = plot.top + m.labelGap / 2f
                    val box = Bounds(cx - s.width / 2f, ly, cx + s.width / 2f, ly + s.height)
                    if (referenceFits(box)) {
                        refLabelBoxes += box
                        labels += TextNode("x-band-label:${mark.from}:${mark.to}", text, XY(cx, ly), HAlign.Center, VAlign.Top, TextKind.Reference)
                    }
                }
            }
            is XRuleMark -> {
                val sx = x ?: continue
                if (mark.x < xDomLo || mark.x > xDomHi) continue
                val px = sx(mark.x)
                marks += RuleNode("x-rule:${mark.x}", XY(px, plot.top), XY(px, plot.bottom), RuleKind.XRule, color = mark.color, pattern = mark.pattern)
                mark.label?.takeIf { it.isNotEmpty() }?.let { text ->
                    val s = measure.measure(text, TextKind.Reference)
                    val ly = plot.top + m.labelGap / 2f
                    val rightBox = Bounds(px + m.labelGap, ly, px + m.labelGap + s.width, ly + s.height)
                    val leftBox = Bounds(px - m.labelGap - s.width, ly, px - m.labelGap, ly + s.height)
                    when {
                        referenceFits(rightBox) -> {
                            refLabelBoxes += rightBox
                            labels += TextNode("x-rule-label:${mark.x}", text, XY(px + m.labelGap, ly), HAlign.Start, VAlign.Top, TextKind.Reference)
                        }
                        referenceFits(leftBox) -> {
                            refLabelBoxes += leftBox
                            labels += TextNode("x-rule-label:${mark.x}", text, XY(px - m.labelGap, ly), HAlign.End, VAlign.Top, TextKind.Reference)
                        }
                    }
                }
            }
        }
        // Edges go on once the last area is down, so no fill covers a stroke.
        if (markIndex == lastArea) marks += areaEdges
    }

    return ChartScene(
        size, plot, zeroY, prep.legend, guides, marks, labels, points, prep.focusMode, spec.description,
        clipPlot = viewport != null,
    )
}

/** Category labels never take more than this fraction of a horizontal bar chart's width. */
private const val MAX_LABEL_FRACTION = 0.4f

/**
 * Horizontal bars: categories down the left on a [BandScale], values along the bottom on a
 * [LinearScale]. The upright compiler's bar rules, turned sideways: bars sit on a vertical zero
 * line, a stack's zero bound stays on the (clamped) baseline while its other bounds follow the
 * scale, missing values are skipped and zero-length stack segments are not drawn.
 */
private fun compileHorizontal(spec: HorizontalBarChart, size: Extent, measure: TextMeasure, m: ChartMetrics): ChartScene {
    val categories = spec.categories.labels
    val rows = categories.size
    val mark = spec.bars
    val stack = if (mark.layout == BarLayout.Stacked) Stack.diverging(mark.series.map { it.values.take(rows) }) else null
    fun Series.at(i: Int): Double = values.getOrNull(i)?.takeIf { it.isFinite() } ?: Double.NaN

    // 1. Value domain, from the rows that exist. A stack's zero floor is the baseline, which
    // includeZero governs; every other bound is data.
    var lo = Double.POSITIVE_INFINITY
    var hi = Double.NEGATIVE_INFINITY
    fun take(v: Double) {
        if (v.isFinite()) { lo = min(lo, v); hi = max(hi, v) }
    }
    if (stack != null) stack.forEach { s -> s.forEach { (a, b) -> if (a != 0.0) take(a); if (b != 0.0) take(b) } }
    else mark.series.forEach { s -> for (i in 0 until rows) take(s.at(i)) }
    spec.rules.forEach { take(it.y) }
    var x = valueScale(spec.value, lo, hi, hasLength = true)
    val valueVisible = spec.value.visible
    val ticks = if (valueVisible) x.ticks(spec.value.tickCount) else emptyList()
    val tickFormat = spec.value.format ?: Formats.forTicks(ticks)
    val tickTexts = ticks.map(tickFormat::format)
    val tickSizes = tickTexts.map { measure.measure(it, TextKind.TickX) }
    val valueFormat = spec.value.valueFormat ?: Formats.Readable

    // 2. Margins. Category labels on the left, cut short past their share of the width; value
    // ticks along the bottom, whose end labels may overhang the plot by half their width.
    val maxLabel = size.width * MAX_LABEL_FRACTION - m.labelGap
    val catTexts = if (spec.categories.visible && maxLabel > 0f) categories.map { fit(it, maxLabel, TextKind.TickY, measure) } else emptyList()
    val catSizes = catTexts.map { measure.measure(it, TextKind.TickY) }
    val widest = catTexts.indices.filter { catTexts[it].isNotEmpty() }.maxOfOrNull { catSizes[it].width } ?: 0f
    var left = if (widest > 0f) widest + m.labelGap else 0f
    var right = 0f
    if (tickSizes.isNotEmpty()) {
        left = max(left, tickSizes.first().width / 2f)
        right = tickSizes.last().width / 2f
    }
    val bottom = if (valueVisible) measure.measure("0", TextKind.TickX).height + m.labelGap else 0f
    val plot = Bounds(left, 0f, size.width - right, size.height - bottom)
    if (plot.width <= 0f || plot.height <= 0f) return emptyScene(size, spec, FocusMode.NearestY)

    x = x.withRange(plot.left, plot.right)
    val band = BandScale(rows, plot.top, plot.bottom, m.bandPadding, m.bandOuterPadding)
    val zeroX = x(0.0).coerceIn(plot.left, plot.right)
    // A stack bound of zero is the baseline, which stays in the plot even when zero is not.
    fun xStack(v: Double): Float = if (v == 0.0) zeroX else x(v)

    val guides = mutableListOf<Node>()
    val marks = mutableListOf<Node>()
    val labels = mutableListOf<Node>()
    val points = mutableListOf<FocusPoint>()

    // 3. Guides and labels. There are no grid lines between rows: they would run through the bars.
    ticks.forEachIndexed { i, v ->
        val px = x(v)
        val w = tickSizes[i].width
        guides += RuleNode("grid-x:${tickTexts[i]}", XY(px, plot.top), XY(px, plot.bottom), RuleKind.GridX)
        val lx = px.coerceIn(w / 2f, max(w / 2f, size.width - w / 2f))
        labels += TextNode("tick-x:${tickTexts[i]}", tickTexts[i], XY(lx, plot.bottom + m.labelGap), HAlign.Center, VAlign.Top, TextKind.TickX)
    }
    if (catTexts.isNotEmpty()) {
        // Every stride-th label, so labels taller than their rows never overlap.
        val tallest = catSizes.maxOfOrNull { it.height } ?: 0f
        val stride = if (band.step <= 0f) 1 else max(1, ceil(tallest / band.step).toInt())
        categories.indices.forEach { i ->
            if (i % stride == 0 && catTexts[i].isNotEmpty()) {
                labels += TextNode("tick-y:$i", catTexts[i], XY(plot.left - m.labelGap, band.center(i)), HAlign.End, VAlign.Center, TextKind.TickY)
            }
        }
    }
    if (spec.categories.visible) guides += RuleNode("axis-y", XY(plot.left, plot.top), XY(plot.left, plot.bottom), RuleKind.AxisY)
    if (valueVisible) guides += RuleNode("axis-x", XY(plot.left, plot.bottom), XY(plot.right, plot.bottom), RuleKind.AxisX)
    if (x.domainStart <= 0.0 && x.domainEnd >= 0.0) {
        guides += RuleNode("baseline", XY(zeroX, plot.top), XY(zeroX, plot.bottom), RuleKind.Baseline)
    }

    // 4. Bars, row by row from the top, then reference rules over them.
    fun capped(start: Float, extent: Float, cap: Float): Pair<Float, Float> =
        if (extent > cap) (start + (extent - cap) / 2f) to cap else start to extent
    if (stack == null) {
        val k = mark.series.size
        // Cap the group, not each bar, so capped bars stay together.
        val slotCap = if (k > 1) m.maxBarWidth / (1f - m.groupPadding) else m.maxBarWidth
        for (i in 0 until rows) {
            val (gStart, gHeight) = capped(band.start(i), band.bandwidth, slotCap * k)
            val inner = BandScale(k, gStart, gStart + gHeight, if (k > 1) m.groupPadding else 0f, 0f)
            mark.series.forEachIndexed { s, series ->
                val v = series.at(i)
                if (v.isNaN()) return@forEachIndexed
                val t = inner.start(s)
                val px = x(v)
                val rect = Bounds(min(zeroX, px), t, max(zeroX, px), t + inner.bandwidth)
                marks += RectNode("bar:${series.name}:$i", rect, s, i, if (v >= 0) Edge.Left else Edge.Right, kind = RectKind.Bar, value = v)
                points += FocusPoint(s, i, MarkKind.Bar, px, rect.centerY, band.center(i), categories[i], v, valueFormat.format(v), hit = RectHit(rect))
            }
        }
    } else {
        for (i in 0 until rows) {
            val (t, h) = capped(band.start(i), band.bandwidth, m.maxBarWidth)
            val outermost = mark.series.indices.filter { s -> mark.series[s].at(i).let { !it.isNaN() && it != 0.0 } }
                .groupBy { s -> mark.series[s].at(i) >= 0 }
                .values.map { it.last() }.toSet()
            mark.series.forEachIndexed { s, series ->
                val v = series.at(i)
                if (v.isNaN() || v == 0.0) return@forEachIndexed
                val (v0, v1) = stack[s][i]
                val base = if (v >= 0) Edge.Left else Edge.Right
                var r = Bounds(min(xStack(v0), xStack(v1)), t, max(xStack(v0), xStack(v1)), t + h)
                if (v0 != 0.0 && v1 != 0.0 && r.width > m.stackGap) {
                    r = if (base == Edge.Left) r.copy(left = r.left + m.stackGap) else r.copy(right = r.right - m.stackGap)
                }
                val end = if (v >= 0) xStack(v1) else xStack(v0)
                marks += RectNode("bar:${series.name}:$i", r, s, i, base, isEnd = s in outermost, kind = RectKind.Bar, value = v)
                points += FocusPoint(s, i, MarkKind.Bar, end, r.centerY, band.center(i), categories[i], v, valueFormat.format(v), hit = RectHit(r))
            }
        }
    }
    spec.rules.forEach { rule ->
        val px = x(rule.y)
        marks += RuleNode("ref:${rule.y}", XY(px, plot.top), XY(px, plot.bottom), RuleKind.Reference)
        rule.label?.let {
            // Right of the line, or left of it when that would leave the chart.
            val fits = px + m.labelGap + measure.measure(it, TextKind.Reference).width <= size.width
            val at = XY(if (fits) px + m.labelGap else px - m.labelGap, plot.top)
            labels += TextNode("ref-label:${rule.y}", it, at, if (fits) HAlign.Start else HAlign.End, VAlign.Top, TextKind.Reference)
        }
    }
    return ChartScene(size, plot, zeroX, spec.legend(), guides, marks, labels, points, FocusMode.NearestY, spec.description)
}

/** [text], cut short with an ellipsis until it measures at most [maxWidth]; empty if nothing fits. */
internal fun fit(text: String, maxWidth: Float, kind: TextKind, measure: TextMeasure): String {
    if (measure.measure(text, kind).width <= maxWidth) return text
    fun cut(n: Int) = text.take(n).trimEnd() + "\u2026"
    // The longest prefix that fits with its ellipsis.
    var lo = 0
    var hi = text.length - 1
    while (lo < hi) {
        val mid = (lo + hi + 1) / 2
        if (measure.measure(cut(mid), kind).width <= maxWidth) lo = mid else hi = mid - 1
    }
    return if (lo > 0 && measure.measure(cut(lo), kind).width <= maxWidth) cut(lo) else ""
}

/**
 * The value scale over a data extent: [ValueAxis.padding] (never across zero on a side the data
 * does not cross, when zero is included), then zero for marks with a length, then explicit limits, then nice ends when the axis is shown. A hidden axis has no ticks to round
 * to, so the data fills the height. Flat data (or equal limits) has no range: on a shown axis it
 * sits at the bottom of a one-unit range, counting up from the value; on a hidden axis (a
 * sparkline) it sits in the middle of the plot.
 */
private fun valueScale(axis: ValueAxis, dataLo: Double, dataHi: Double, hasLength: Boolean): LinearScale {
    var lo = dataLo
    var hi = dataHi
    val zero = axis.includeZero ?: hasLength
    if (!lo.isFinite()) { lo = 0.0; hi = 1.0 } else if (axis.padding > 0.0) {
        // With zero in the domain, padding never pushes it across zero on a side the data does
        // not cross, so bars and areas keep their baseline on the plot edge.
        val pad = (hi - lo) * axis.padding
        if (!zero || lo < 0.0) lo -= pad
        if (!zero || hi > 0.0) hi += pad
    }
    if (zero) { lo = min(lo, 0.0); hi = max(hi, 0.0) }
    lo = axis.min ?: lo
    hi = axis.max ?: hi
    if (lo == hi) {
        if (axis.visible) hi = lo + 1.0 else { lo -= 1.0; hi += 1.0 }
    }
    val scale = LinearScale(lo, hi)
    return if (axis.visible && axis.min == null && axis.max == null) scale.nice(axis.tickCount) else scale
}

/** The smallest positive gap between consecutive distinct values, or 0 with fewer than two. */
internal fun computeMinStep(values: List<Double>?): Double {
    if (values == null || values.size < 2) return 0.0
    return computeMinStep(values.toDoubleArray())
}

internal fun computeMinStep(values: DoubleArray): Double {
    if (values.size < 2) return 0.0
    var minGap = Double.POSITIVE_INFINITY
    var prev = Double.NaN
    var ascending = true
    for (i in values.indices) {
        val v = values[i]
        if (!v.isFinite()) continue
        if (!prev.isNaN()) {
            if (v < prev) {
                ascending = false
                break
            }
            val gap = v - prev
            if (gap > 0.0 && gap < minGap) minGap = gap
        }
        prev = v
    }
    if (ascending) return if (minGap.isFinite()) minGap else 0.0
    val sorted = values.filter { it.isFinite() }.distinct().sorted()
    return sorted.zipWithNext { a, b -> b - a }.minOrNull() ?: 0.0
}

private fun compilePie(spec: PieChart, size: Extent, m: ChartMetrics): ChartScene {
    val slices = if (spec.sort) spec.slices.sortedByDescending { it.value } else spec.slices
    // Missing and nonpositive slices take no area. Normalize before summing so even several
    // finite values near Double.MAX_VALUE retain their proportions instead of overflowing.
    val peak = slices.maxOfOrNull { if (it.value.isFinite() && it.value > 0.0) it.value else 0.0 } ?: 0.0
    val total = if (peak > 0.0) slices.sumOf { if (it.value.isFinite() && it.value > 0.0) it.value / peak else 0.0 } else 0.0
    val center = XY(size.width / 2f, size.height / 2f)
    val outer = max(0f, min(size.width, size.height) / 2f - m.pieInset)
    val inner = outer * m.pieInnerRatio
    val mid = (outer + inner) / 2f
    val pad = if (mid > 0f) m.pieGap / mid else 0f
    val format = spec.valueFormat ?: Formats.Readable
    val marks = mutableListOf<Node>()
    val points = mutableListOf<FocusPoint>()
    var angle = 0.0
    slices.forEachIndexed { i, slice ->
        // Keep the drawn-order indices used by legend(), even when a slice has no geometry.
        if (!slice.value.isFinite() || slice.value <= 0.0) return@forEachIndexed
        val share = (slice.value / peak) / total
        val a0 = angle.toFloat()
        angle += share * 2 * PI
        val a1 = angle.toFloat()
        val inset = min(pad / 2f, (a1 - a0) / 2f)
        marks += ArcNode("arc:${slice.label}", center, inner, outer, a0 + inset, a1 - inset, i, i)
        val am = (a0 + a1) / 2f
        val r = if (inner == 0f) outer * 0.62f else mid
        points += FocusPoint(
            i, i, MarkKind.Arc, center.x + sin(am) * r, center.y - cos(am) * r, am,
            slice.label, slice.value, format.format(slice.value), share,
            hit = ArcHit(center, inner, outer, a0, a1),
        )
    }
    val plot = Bounds(center.x - outer, center.y - outer, center.x + outer, center.y + outer)
    return ChartScene(size, plot, center.y, spec.legend(), emptyList(), marks, emptyList(), points, FocusMode.Containment, spec.description)
}

/** A cut label must keep at least this many characters before its ellipsis, or it is left out. */
private const val MIN_CUT_CHARS = 3

/** [text] as [fit] cuts it, or empty when the cut would keep fewer than [MIN_CUT_CHARS] characters. */
internal fun fitLabel(text: String, maxWidth: Float, kind: TextKind, measure: TextMeasure): String {
    val fitted = fit(text, maxWidth, kind, measure)
    return if (fitted == text || fitted.length - 1 >= MIN_CUT_CHARS) fitted else ""
}

/**
 * [this] shrunk by [d] on each side that is inside [outer], so neighbours end up 2·[d] apart while
 * rectangles on [outer]'s edges keep them. Never inverts: a rectangle narrower than its gaps
 * collapses to zero width or height.
 */
private fun Bounds.insetWithin(d: Float, outer: Bounds): Bounds {
    if (d <= 0f) return this
    val l = if (left > outer.left + 0.5f) left + d else left
    val t = if (top > outer.top + 0.5f) top + d else top
    val r = if (right < outer.right - 0.5f) right - d else right
    val b = if (bottom < outer.bottom - 0.5f) bottom - d else bottom
    return Bounds(l, t, max(l, r), max(t, b))
}

/**
 * A two-level squarified treemap over the whole canvas. Groups are tiled first, then each group's
 * leaves inside it under an optional header strip; both largest first, ties broken by id.
 *
 * - Only leaves with a positive, finite size take part; groups without one are left out, and with
 *   none at all the scene is empty.
 * - Gaps come out of the tiles after squarifying ([Bounds.insetWithin]), so areas are proportional
 *   to size within a group before gaps and headers, and close to it across groups.
 * - A header is reserved only when its label fits (cut with an ellipsis if needed) and the group
 *   keeps at least as much height for its tiles as the header takes.
 * - Tile text: the label (cut with an ellipsis when it keeps at least [MIN_CUT_CHARS] characters,
 *   otherwise left out), and under it the sublabel or value when that fits whole; numbers are
 *   never cut.
 * - A tile whose gaps leave it no area keeps its node (so motion can pair it) but gets no focus
 *   point: there is nothing painted to touch.
 */
private fun compileTreemap(spec: TreemapChart, size: Extent, measure: TextMeasure, m: ChartMetrics): ChartScene {
    val plot = Bounds(0f, 0f, size.width, size.height)
    fun takesArea(leaf: TreemapLeaf) = leaf.size.isFinite() && leaf.size > 0.0
    val sizes = spec.groups.map { g -> g.leaves.filter(::takesArea).sumOf { it.size } }
    val total = sizes.sum()
    if (plot.width <= 0f || plot.height <= 0f || !(total > 0.0) || !total.isFinite()) {
        return emptyScene(size, spec, FocusMode.Containment)
    }
    val valueFormat = spec.valueFormat ?: Formats.Readable
    val sizeFormat = spec.sizeFormat ?: Formats.Readable
    val pad = m.tilePadding

    val groupOrder = spec.groups.indices.filter { sizes[it] > 0.0 }
        .sortedWith(compareByDescending<Int> { sizes[it] }.thenBy { spec.groups[it].id })
    val groupRects = Treemap.squarify(groupOrder.map { sizes[it] }, plot)

    val marks = mutableListOf<Node>()
    val labels = mutableListOf<Node>()
    val points = mutableListOf<FocusPoint>()
    groupOrder.forEachIndexed { k, g ->
        val group = spec.groups[g]
        var area = groupRects[k].insetWithin(m.groupGap / 2f, plot)
        if (spec.groupLabels) {
            val text = fitLabel(group.label, area.width - pad * 2, TextKind.GroupLabel, measure)
            val header = if (text.isEmpty()) 0f else measure.measure(text, TextKind.GroupLabel).height + pad
            if (header > 0f && header * 2 <= area.height) {
                labels += TextNode("group-label:${group.id}", text, XY(area.left + pad, area.top + pad / 2f), HAlign.Start, VAlign.Top, TextKind.GroupLabel)
                area = area.copy(top = area.top + header)
            }
        }
        val leafOrder = group.leaves.indices.filter { takesArea(group.leaves[it]) }
            .sortedWith(compareByDescending<Int> { group.leaves[it].size }.thenBy { group.leaves[it].id })
        val rects = Treemap.squarify(leafOrder.map { group.leaves[it].size }, area)
        leafOrder.forEachIndexed { r, j ->
            val leaf = group.leaves[j]
            val tile = rects[r].insetWithin(m.tileGap / 2f, area)
            marks += RectNode("tile:${leaf.id}", tile, g, j, Edge.Bottom, kind = RectKind.Tile, value = leaf.value)
            val valueText = if (leaf.value.isNaN()) "" else valueFormat.format(leaf.value)
            if (tile.width > 0f && tile.height > 0f) {
                points += FocusPoint(
                    g, j, MarkKind.Tile, tile.centerX, tile.centerY, tile.centerX, leaf.label, leaf.value, valueText,
                    share = leaf.size / total, hit = RectHit(tile), id = leaf.id,
                    tile = TileDetail(leaf.size, sizeFormat.format(leaf.size)),
                )
            }
            // Text: the label, and the sublabel or value under it when both fit as a block.
            val innerW = tile.width - pad * 2
            val innerH = tile.height - pad * 2
            if (innerW <= 0f || innerH <= 0f) return@forEachIndexed
            val name = fitLabel(leaf.label, innerW, TextKind.TileLabel, measure)
            if (name.isEmpty()) return@forEachIndexed
            val nameSize = measure.measure(name, TextKind.TileLabel)
            if (nameSize.height > innerH) return@forEachIndexed
            val detail = leaf.sublabel ?: valueText
            val detailSize = if (detail.isEmpty()) null else measure.measure(detail, TextKind.TileDetail)
            if (detailSize != null && detailSize.width <= innerW && nameSize.height + detailSize.height <= innerH) {
                val top = tile.centerY - (nameSize.height + detailSize.height) / 2f
                labels += TextNode("tile-label:${leaf.id}", name, XY(tile.centerX, top), HAlign.Center, VAlign.Top, TextKind.TileLabel)
                labels += TextNode("tile-detail:${leaf.id}", detail, XY(tile.centerX, top + nameSize.height), HAlign.Center, VAlign.Top, TextKind.TileDetail)
            } else {
                labels += TextNode("tile-label:${leaf.id}", name, XY(tile.centerX, tile.centerY), HAlign.Center, VAlign.Center, TextKind.TileLabel)
            }
        }
    }
    return ChartScene(size, plot, plot.bottom, spec.legend(), emptyList(), marks, labels, points, FocusMode.Containment, spec.description)
}

/** Half the diagonal of a unit square, rounded up so the middle of a gap crossing is inside it. */
private const val HALF_DIAGONAL = 0.7072f

/**
 * A grid of square cells, one per column and row, coloured by intensity, with column labels above
 * and row labels on the left.
 *
 * - **Geometry.** Cells are square: the step (cell plus gap) is the largest that fits both the
 *   columns across and the rows down, and the grid sits at the top left of the room the labels
 *   leave. The gap is [ChartMetrics.cellGap], shrunk to a quarter of the step when cells are tiny,
 *   so a cell never has zero or negative size. No columns, no rows, or no room after the labels
 *   give an empty scene in [FocusMode.Containment].
 * - **Semantics.** See [HeatmapChart]: a non-finite value is missing (an empty cell, no focus
 *   point); zero and negative values are real (focusable, printed raw) and negatives take
 *   intensity 0; intensity is the value over the maximum, clamped to 0..1, and 0 when there is
 *   no positive finite maximum.
 * - **Labels.** Blank labels are skipped. Column labels start at their column's left edge, pulled
 *   inside the chart at the right edge, and one that would come within
 *   [ChartMetrics.minLabelSpacing] of the label before it is left out. A row label whose ink
 *   ([TextMeasure.inkHeight]) would overlap the one above is left out; row labels are cut with an
 *   ellipsis past 40% of the width.
 * - **Focus.** A [RectHit] of the painted cell; the point is anchored at the cell's top centre, so
 *   a tooltip placed above it leaves the cell visible. A pointer in a gap focuses the nearest cell
 *   within [ChartScene.focusSlop], half the gap's diagonal.
 */
private fun compileHeatmap(spec: HeatmapChart, size: Extent, measure: TextMeasure, m: ChartMetrics): ChartScene {
    val cols = spec.columns.size
    val rows = spec.rows.size
    if (cols == 0 || rows == 0 || !(size.width > 0f) || !(size.height > 0f)) return emptyScene(size, spec, FocusMode.Containment)

    // Margins: row labels on the left (cut short past their share of the width), column labels on top.
    val maxRowLabel = size.width * MAX_LABEL_FRACTION - m.labelGap
    val rowTexts = spec.rows.map { if (it.isEmpty() || maxRowLabel <= 0f) "" else fit(it, maxRowLabel, TextKind.TickY, measure) }
    val rowWidth = rowTexts.filter { it.isNotEmpty() }.maxOfOrNull { measure.measure(it, TextKind.TickY).width } ?: 0f
    val colHeight = spec.columns.filter { it.isNotEmpty() }.maxOfOrNull { measure.measure(it, TextKind.TickX).height } ?: 0f
    val left = if (rowWidth > 0f) rowWidth + m.labelGap else 0f
    val top = if (colHeight > 0f) colHeight + m.labelGap else 0f
    val roomW = size.width - left
    val roomH = size.height - top
    if (roomW <= 0f || roomH <= 0f) return emptyScene(size, spec, FocusMode.Containment)

    // Square cells: n cells and n - 1 gaps fill the tighter direction exactly.
    var gap = max(0f, m.cellGap)
    var step = min((roomW + gap) / cols, (roomH + gap) / rows)
    if (step < gap * 4f) {
        // Too small for the full gap: a quarter of the step, so cells keep three quarters.
        step = min(roomW / (cols - 0.25f), roomH / (rows - 0.25f))
        gap = step * 0.25f
    }
    val side = step - gap
    if (!(side > 0f)) return emptyScene(size, spec, FocusMode.Containment)
    val plot = Bounds(left, top, left + cols * step - gap, top + rows * step - gap)

    val peak = spec.max ?: spec.values.maxOfOrNull { c -> c.filter { it.isFinite() }.maxOrNull() ?: Double.NEGATIVE_INFINITY }
    val scale = peak?.takeIf { it.isFinite() && it > 0.0 }
    val format = spec.valueFormat ?: Formats.Readable

    val marks = ArrayList<Node>(cols * rows)
    val labels = mutableListOf<Node>()
    val points = mutableListOf<FocusPoint>()
    for (c in 0 until cols) {
        val column = spec.values[c]
        val x = left + c * step
        for (r in 0 until rows) {
            val v = column.getOrNull(r)?.takeIf { it.isFinite() } ?: Double.NaN
            val y = top + r * step
            val cell = Bounds(x, y, x + side, y + side)
            val intensity = when {
                v.isNaN() -> Float.NaN
                scale == null -> 0f
                else -> (v / scale).coerceIn(0.0, 1.0).toFloat()
            }
            marks += RectNode("cell:$c:$r", cell, c, r, Edge.Bottom, kind = RectKind.Cell, value = v, intensity = intensity)
            if (v.isNaN()) continue // missing: drawn empty, never focused
            val label = spec.cellLabels?.get(c)?.getOrNull(r)?.takeIf { it.isNotEmpty() }
                ?: listOf(spec.columns[c], spec.rows[r]).filter { it.isNotEmpty() }.joinToString(" ")
            points += FocusPoint(
                c, r, MarkKind.Cell, cell.centerX, cell.top, cell.centerX, label, v, format.format(v),
                hit = RectHit(cell), intensity = intensity,
            )
        }
    }

    // Column labels: left to right, each clear of the one before it.
    var lastEnd = Float.NEGATIVE_INFINITY
    spec.columns.forEachIndexed { c, text ->
        if (text.isEmpty()) return@forEachIndexed
        val w = measure.measure(text, TextKind.TickX).width
        val x = (left + c * step).coerceAtMost(size.width - w)
        if (x < 0f || x < lastEnd + m.minLabelSpacing) return@forEachIndexed
        labels += TextNode("col:$c", text, XY(x, top - m.labelGap), HAlign.Start, VAlign.Bottom, TextKind.TickX)
        lastEnd = x + w
    }
    // Row labels: top to bottom, each clear of the one above it by ink height (not the full line
    // box, whose ascent and descent padding is taller than a compact row).
    var lastBottom = Float.NEGATIVE_INFINITY
    rowTexts.forEachIndexed { r, text ->
        if (text.isEmpty()) return@forEachIndexed
        val h = measure.inkHeight(text, TextKind.TickY)
        val cy = top + r * step + side / 2f
        if (cy - h / 2f < lastBottom) return@forEachIndexed
        labels += TextNode("row:$r", text, XY(left - m.labelGap, cy), HAlign.End, VAlign.Center, TextKind.TickY)
        lastBottom = cy + h / 2f
    }
    // A pointer in a gap focuses the nearest cell (see ChartScene.focusSlop). Half the gap's
    // diagonal, so the middle of a crossing, where every cell is diagonal, still reaches one; the
    // edge of a missing cell, a whole gap from its neighbours, does not.
    return ChartScene(
        size, plot, plot.bottom, spec.legend(), emptyList(), marks, labels, points, FocusMode.Containment, spec.description,
        focusSlop = gap * HALF_DIAGONAL,
    )
}
