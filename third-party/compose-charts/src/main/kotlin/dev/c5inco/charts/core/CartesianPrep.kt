package dev.c5inco.charts.core

import java.util.concurrent.atomic.AtomicReference
import kotlin.math.max
import kotlin.math.min

private fun seriesCount(mark: Mark): Int = when (mark) {
    is BarMark -> mark.series.size
    is LineMark -> mark.series.size
    is AreaMark -> mark.series.size
    is DotMark -> mark.series.size
    is CandleMark -> 1
    is RuleMark, is XRuleMark, is XBandMark -> 0
}

/**
 * Display names may repeat. Unique names keep their identity across reorder; repeated names use
 * their occurrence in declaration order. Escape separators so names cannot impersonate a suffix.
 * Resolve these before skipping missing data, so a disappearing datum never renumbers another.
 */
internal fun nameKeys(names: List<String>): List<String> =
    occurrenceKeys(names.map { it.replace("%", "%25").replace(":", "%3A") })

/**
 * Keys for values that may repeat within one scene: a text that occurs once is its own key, and
 * repeats are numbered by occurrence in order (`2.0:occurrence:0`, `2.0:occurrence:1`), so removing
 * or moving one value never renumbers a different one. Escaped names and numbers never contain
 * `:occurrence:`; free text (a custom tick format) might, so a number another text already spells
 * is skipped and no two keys are ever equal. Returns [texts] itself when nothing repeats.
 */
internal fun occurrenceKeys(texts: List<String>): List<String> {
    if (texts.size < 2) return texts
    val counts = HashMap<String, Int>(texts.size * 2)
    var repeats = false
    for (text in texts) {
        val count = (counts[text] ?: 0) + 1
        counts[text] = count
        if (count > 1) repeats = true
    }
    if (!repeats) return texts
    val next = HashMap<String, Int>()
    return texts.map { text ->
        if (counts.getValue(text) == 1) text else {
            var occurrence = next[text] ?: 0
            var key = "$text:occurrence:$occurrence"
            while (key in counts) key = "$text:occurrence:${++occurrence}"
            next[text] = occurrence + 1
            key
        }
    }
}

/** The text a rule, x rule or band is keyed by: its value, or its range. Null for other marks. */
private fun annotationText(mark: Mark): String? = when (mark) {
    is RuleMark -> mark.y.toString()
    is XRuleMark -> mark.x.toString()
    is XBandMark -> "${mark.from}:${mark.to}"
    is BarMark, is LineMark, is AreaMark, is DotMark, is CandleMark -> null
}

/** Per mark, the key of a rule, x rule or band, unique among marks of its kind; null for other marks. */
private fun annotationIdentities(marks: List<Mark>): Array<String?> {
    val keys = arrayOfNulls<String>(marks.size)
    for (indices in marks.indices.filter { annotationText(marks[it]) != null }.groupBy { marks[it]::class }.values) {
        val resolved = occurrenceKeys(indices.map { annotationText(marks[it])!! })
        indices.forEachIndexed { k, i -> keys[i] = resolved[k] }
    }
    return keys
}

/** The values [positions] can draw: a view of the head of a series longer than the axis, never a copy. */
private fun Series.drawn(positions: Int): List<Double> = if (values.size <= positions) values else values.subList(0, positions)

/** Identity is scoped by mark kind, across all declarations of that kind in a Cartesian chart. */
private fun markIdentities(marks: List<Mark>): List<List<String>> {
    val keys = marks.map { mark ->
        when (mark) {
            is BarMark -> mark.series.map { it.name }
            is LineMark -> mark.series.map { it.name }
            is AreaMark -> mark.series.map { it.name }
            is DotMark -> mark.series.map { it.name }
            is CandleMark -> listOf(mark.name)
            is RuleMark, is XRuleMark, is XBandMark -> emptyList()
        }.toMutableList()
    }
    for (indices in marks.indices.groupBy { marks[it]::class }.values) {
        val resolved = nameKeys(indices.flatMap { keys[it] }).iterator()
        for (i in indices) for (s in keys[i].indices) keys[i][s] = resolved.next()
    }
    return keys
}

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
    /** Per mark, each series' escaped, disambiguated name for node keys (a candle mark's own name). */
    val seriesKeys: List<List<String>> = markIdentities(spec.marks)
    /** Per mark, a rule's, x rule's or band's key (shared by its line and its label); null for series marks. */
    val annotationKeys: Array<String?> = annotationIdentities(spec.marks)

    val firstSeries: IntArray = IntArray(spec.marks.size).also { arr ->
        spec.marks.foldIndexed(0) { i, next, mark -> arr[i] = next; next + seriesCount(mark) }
    }
    val totalSeries: Int = spec.marks.sumOf(::seriesCount)
    val seriesMarkIndex: IntArray = IntArray(totalSeries)
    val seriesLocalIndex: IntArray = IntArray(totalSeries)
    /** Per scene series, the kind [AlignedFocus] resolves it as; null for series it does not resolve. */
    val seriesAlignedKind: Array<AlignedKind?> = arrayOfNulls(totalSeries)

    init {
        spec.marks.forEachIndexed { mIdx, mark ->
            val count = seriesCount(mark)
            val first = firstSeries[mIdx]
            val kind = when (mark) {
                is LineMark -> AlignedKind.Line
                is AreaMark -> AlignedKind.Area
                else -> null
            }
            for (s in 0 until count) {
                seriesMarkIndex[first + s] = mIdx
                seriesLocalIndex[first + s] = s
                seriesAlignedKind[first + s] = kind
            }
        }
    }

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
                mark is BarMark && mark.layout == BarLayout.Stacked -> map[i] = Stack.diverging(mark.series.map { it.drawn(positions) })
                mark is AreaMark && mark.stacked -> map[i] = Stack.diverging(mark.series.map { it.drawn(positions) })
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
        // Only values with an axis position can be drawn or contribute to the domain.
        fun takeSeries(series: List<Series>) = series.forEach { s ->
            s.drawn(positions).forEach(::take)
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
                is BarMark -> { length = true; if (stack != null) takeStack(stack) else takeSeries(mark.series) }
                is AreaMark -> { length = true; if (stack != null) takeStack(stack) else takeSeries(mark.series) }
                is LineMark -> takeSeries(mark.series)
                // A dot is drawn only when both coordinates are finite, and only a drawn dot counts
                // toward either domain (issue #28). Marks at axis positions need no such check: their
                // x is a position, always finite, and a value counts exactly when it is finite.
                is DotMark -> mark.series.forEach { s ->
                    s.points.forEach { if (it.x.isFinite() && it.y.isFinite()) { take(it.y); takeX(it.x) } }
                }
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
                    // A zero-width band draws nothing, so it widens nothing.
                    is XBandMark -> if (mark.from < mark.to) { takeX(mark.from); takeX(mark.to) }
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

    private val defaultYKeys: List<String>? = defaultYTexts?.let(::occurrenceKeys)

    fun yTexts(): List<String> = defaultYTexts ?: yTicks.map(yFormat::format)

    /** Node keys for [texts] from [yTexts]: the texts themselves, unless a custom format repeats one. */
    fun yKeys(texts: List<String>): List<String> = if (texts === defaultYTexts) defaultYKeys!! else occurrenceKeys(texts)

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
        // Cut to the axis's positions: values past the last one are never drawn or focused.
        seriesList?.let { list -> Array(list.size) { sIdx -> list[sIdx].drawn(positions).toDoubleArray() } }
    }

    /**
     * For each `AreaMark` series, whether it falls below zero without ever rising above it: some
     * value below zero and none above. Its gradient fades from the value axis's negative end. Read
     * from the whole series, not the window, so a gradient does not turn over while panning.
     * Non-finite values are gaps, drawn as nothing, so they have no sign here either.
     */
    val areaFalls: Array<BooleanArray?> = Array(spec.marks.size) { mIdx ->
        if (spec.marks[mIdx] !is AreaMark) return@Array null
        val sArrs = seriesArrays[mIdx] ?: return@Array null
        BooleanArray(sArrs.size) { sIdx ->
            var below = false
            var above = false
            for (v in sArrs[sIdx]) {
                if (!v.isFinite()) continue
                if (v > 0.0) above = true else if (v < 0.0) below = true
            }
            below && !above
        }
    }

    /**
     * For each `LineMark`/`AreaMark` series and index `i`, the largest index `<= i` with a finite
     * value (or `-1` if none). Lets viewport compilation locate the nearest finite neighbour left
     * of the window and the last finite datum inside the window in O(1) without scanning `NaN` runs.
     */
    val prevDefinedArrays: Array<Array<IntArray>?> = Array(spec.marks.size) { mIdx ->
        val sArrs = seriesArrays[mIdx] ?: return@Array null
        Array(sArrs.size) { sIdx ->
            val raw = sArrs[sIdx]
            val out = IntArray(raw.size)
            var last = -1
            for (i in raw.indices) {
                if (raw[i].isFinite()) last = i
                out[i] = last
            }
            out
        }
    }

    /**
     * For each `LineMark`/`AreaMark` series and index `i`, the smallest index `>= i` with a finite
     * value (or `-1` if none). Lets viewport compilation locate the nearest finite neighbour right
     * of the window in O(1) without scanning `NaN` runs.
     */
    val nextDefinedArrays: Array<Array<IntArray>?> = Array(spec.marks.size) { mIdx ->
        val sArrs = seriesArrays[mIdx] ?: return@Array null
        Array(sArrs.size) { sIdx ->
            val raw = sArrs[sIdx]
            val out = IntArray(raw.size)
            var next = -1
            for (i in raw.indices.reversed()) {
                if (raw[i].isFinite()) next = i
                out[i] = next
            }
            out
        }
    }

    /** Total series sample reads performed by the most recent [compileCartesian] call, for testing O(window) work. */
    internal var lastInspectedSamples: Int = 0

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

    /** The last viewport compile's x tick labels: see [viewportXTickLabels]. */
    @Volatile private var lastXTickLabels: XTickLabels? = null

    /**
     * The labels and node keys of x [ticks] under a viewport, in the axis's own format (not a custom
     * one): [texts] makes the labels. A pan moves the window every frame while its ticks stay the
     * same values until one scrolls out, so these are kept for the last ticks and reused while they
     * last, rather than formatted (decimal arithmetic, for the default format) every frame. The
     * labels are a function of the ticks alone for this spec, so reuse changes nothing.
     */
    fun viewportXTickLabels(ticks: List<Double>, texts: (List<Double>) -> List<String>): XTickLabels {
        lastXTickLabels?.let { if (it.ticks == ticks) return it }
        val keys = occurrenceKeys(ticks.map { it.toString() })
        return XTickLabels(ticks, texts(ticks), keys.map { "grid-x:$it" }, keys.map { "tick-x:$it" }).also { lastXTickLabels = it }
    }

    // One M4 index buffer, lent to a compile at a time (compiles of one spec may run at once).
    private val indexBuffer = AtomicReference<IntArray?>(null)

    /**
     * A buffer of at least [size] indices for M4 decimation, to hand back with [returnIndexBuffer]
     * once the compile has read it: a pinch's window can span tens of thousands of positions, and a
     * new buffer for every mark of every frame was most of what a decimated compile allocated. Its
     * contents are whatever the last compile left; decimation writes before it reads.
     */
    fun takeIndexBuffer(size: Int): IntArray {
        val buffer = indexBuffer.getAndSet(null)
        return if (buffer != null && buffer.size >= size) buffer else IntArray(size)
    }

    fun returnIndexBuffer(buffer: IntArray) = indexBuffer.set(buffer)

    fun valueText(markIndex: Int, seriesIndex: Int, datumIndex: Int, value: Double, format: ValueFormat, cacheCustom: Boolean): String {
        if (!cacheCustom) return format.format(value)
        val cache = valueTextCache[markIndex]?.get(seriesIndex) ?: return format.format(value)
        return cache[datumIndex] ?: format.format(value).also { cache[datumIndex] = it }
    }

    /**
     * Everything [AlignedFocus] reads from these tables, as a value two preps of equal content
     * share: see [ResolutionKey]. Built once, with the tables (off the main thread when the host
     * compiles there), in one pass over the drawn values of the line and area series.
     */
    val resolution: ResolutionKey = run {
        val f = Fingerprint()
        f.add(positions.toLong())
        f.add(totalSeries.toLong())
        if (categories != null) {
            f.add(1)
            categories.forEach(f::add)
        } else f.add(0)
        if (xValues != null) {
            f.add(1)
            f.add(xValues.size.toLong())
            for (v in xValues) f.add(v)
        } else f.add(0)
        spec.marks.forEachIndexed { mIdx, mark ->
            // A mark with no series (a rule, an x rule, a band) is never resolved and shifts no
            // series index: adding or removing one leaves every resolved point as it was.
            if (seriesCount(mark) == 0) return@forEachIndexed
            // The kind decides how a series resolves (an area reads its stack when stacked); the
            // count keeps every later series on its scene index.
            f.add(
                when (mark) {
                    is LineMark -> 1
                    is AreaMark -> if (mark.stacked) 2 else 3
                    else -> 0
                },
            )
            f.add(seriesCount(mark).toLong())
            if (mark is LineMark || mark is AreaMark) {
                // The drawn head of each series: values past the axis are never read. Stacks are a
                // function of these values and the stacked flag, so they need no hashing of their own.
                seriesArrays[mIdx]?.forEach { values ->
                    f.add(values.size.toLong())
                    for (v in values) f.add(v)
                }
            }
        }
        ResolutionKey(f.high, f.low, pointFormatKey())
    }

    /**
     * What labels a datum's x ([xLabel]): the axis's format compared as itself (by identity for a
     * lambda), or, for a [TimeAxis]'s default, what that default is built from, since each axis
     * instance builds its own.
     */
    private fun pointFormatKey(): Any = when {
        categories != null -> Unit
        timeX != null && timeX.pointFormat == null -> TimePointFormat(timeX.pointStep, timeX.zone, timeX.locale)
        else -> pointFormat
    }
}

/** X tick labels for [ticks], from [CartesianPrep.viewportXTickLabels]: immutable, shared by every scene that uses them. */
internal class XTickLabels(val ticks: List<Double>, val texts: List<String>, val gridKeys: List<String>, val labelKeys: List<String>)

/** A [TimeAxis]'s default point format, by what it is built from. */
private data class TimePointFormat(val step: Double, val zone: java.time.ZoneId, val locale: java.util.Locale)

/**
 * What a [CartesianPrep] gives [AlignedFocus] to resolve: a 128-bit [Fingerprint] ([high], [low])
 * of the drawn values of every line and area series, the x positions or categories and the marks'
 * layout of series, plus the key of the x label format ([pointFormat]). Equal for preps of specs
 * with equal content, including a series longer than the axis and its truncation; different, but
 * for a hash collision, whenever any of it differs.
 */
internal data class ResolutionKey(val high: Long, val low: Long, val pointFormat: Any)

/**
 * A 128-bit, non-cryptographic content hash: two 64-bit lanes, each a chain of SplitMix64 finalizer
 * rounds with its own injection of every word, finished with the word count.
 *
 * Collisions: for unrelated inputs about 2⁻¹²⁸ per pair, which is not a practical concern for
 * telling a chart's data updates apart. It is not built to resist inputs crafted to collide; a
 * collision would only make two scenes compare equal, so a held focus could show the earlier
 * data's value until the next change.
 */
internal class Fingerprint {
    private var a = 0x6A09E667F3BCC908L
    private var b = -0x4498517A7B3558C5L
    private var words = 0L

    fun add(word: Long) {
        a = mix(a xor word)
        b = mix(java.lang.Long.rotateLeft(b, 23) + word * -0x00AE502812AA7333L)
        words++
    }

    fun add(word: Int) = add(word.toLong())

    /** By its bits, with every NaN alike: what focus equality ([FocusPoint.value]) sees. */
    fun add(value: Double) = add(value.toBits())

    /** Its length, then its characters four to a word. */
    fun add(text: String) {
        add(text.length.toLong())
        var word = 0L
        for (i in text.indices) {
            word = (word shl 16) or text[i].code.toLong()
            if (i % 4 == 3) { add(word); word = 0L }
        }
        if (text.length % 4 != 0) add(word)
    }

    val high: Long get() = mix(a xor words)
    val low: Long get() = mix(b + words * -0x61C8864680B583EBL)

    private fun mix(value: Long): Long {
        var z = value
        z = (z xor (z ushr 30)) * -0x40A7B892E31B1A47L
        z = (z xor (z ushr 27)) * -0x6B2FB644ECCEEE15L
        return z xor (z ushr 31)
    }
}
