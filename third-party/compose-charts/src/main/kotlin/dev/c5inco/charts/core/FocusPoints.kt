package dev.c5inco.charts.core

/**
 * A Cartesian scene's focus points, in the order the compile emits them.
 *
 * Under a viewport the chart compiles on every frame of a pan or pinch, and nothing reads a focus
 * point while it moves, so there ([lazy]) a line or area point is recorded as its series and datum
 * only: [AlignedFocus.pointAt] builds it, equal to the point the compile would have built, if it is
 * ever read. That keeps the compile from building a [FocusPoint], its [FocusDetail] and its texts
 * for every visible datum. Without a viewport ([lazy] false) every point is built as it is emitted, as before:
 * a chart's one compile runs off the main thread, and focus later reads every point.
 *
 * [build] returns a plain list when every point was built, else a [CompactFocusPoints] equal to it.
 */
internal class FocusPointsBuilder(
    private val capacity: Int,
    /** Builds line and area points; null when the scene has no aligned positions. */
    private val aligned: AlignedFocus?,
    internal val lazy: Boolean,
) {
    // The points built as they are emitted: every point, unless [lazy].
    internal val eager = ArrayList<FocusPoint>(if (lazy) 16 else capacity)
    // Per entry: the datum of a line or area point, or the index of a built point in [eager].
    private var entries = IntArray(0)
    // Runs of consecutive entries: where each starts, and its scene series (-1: built points).
    private var runStarts = IntArray(4)
    private var runSeries = IntArray(4)
    private var runs = 0
    private var size = 0
    internal var compact = false
        private set

    // Inline, like addAligned: these run once per datum inside the compile's largest loops, where a
    // call that the JIT leaves out of line cost about 10% of a line compile.

    /** Adds a point built by the compile. */
    inline operator fun plusAssign(point: FocusPoint) {
        eager += point
        if (compact) appendBuilt()
    }

    internal fun appendBuilt() = append(-1, eager.size - 1)

    /**
     * Adds the point of line or area series [series] at [datum], which [build] makes as the compile
     * always has: called now, unless [lazy], when only the pair is kept and [AlignedFocus.pointAt]
     * builds the point if it is read.
     */
    inline fun addAligned(series: Int, datum: Int, build: () -> FocusPoint) {
        if (lazy) record(series, datum) else eager += build()
    }

    internal fun record(series: Int, datum: Int) {
        checkNotNull(aligned) { "line and area points need the scene's AlignedFocus" }
        if (!compact) {
            compact = true
            entries = IntArray(capacity)
            // Points built before the first aligned one: a run of their own.
            for (k in eager.indices) append(-1, k)
        }
        append(series, datum)
    }

    private fun append(series: Int, value: Int) {
        if (runs == 0 || runSeries[runs - 1] != series) {
            if (runs == runStarts.size) {
                runStarts = runStarts.copyOf(runs * 2)
                runSeries = runSeries.copyOf(runs * 2)
            }
            runStarts[runs] = size
            runSeries[runs] = series
            runs++
        }
        if (size == entries.size) entries = entries.copyOf(maxOf(16, size + (size shr 1)))
        entries[size++] = value
    }

    fun build(): List<FocusPoint> {
        if (!compact) return eager
        return CompactFocusPoints(entries, size, runStarts.copyOf(runs), runSeries.copyOf(runs), eager, aligned!!)
    }
}

/**
 * Focus points held as (series, datum) pairs for line and area series, each built by [aligned] the
 * first time it is read and kept from then on; other points as built. Equal, element for element,
 * to the list of built points, and immutable to its readers: safe to read from any thread (a point
 * two threads build at once is built twice, equal, and either may be kept).
 */
internal class CompactFocusPoints(
    private val entries: IntArray,
    override val size: Int,
    private val runStarts: IntArray,
    private val runSeries: IntArray,
    private val eager: List<FocusPoint>,
    private val aligned: AlignedFocus,
) : AbstractList<FocusPoint>(), RandomAccess {
    @Volatile private var built: Array<FocusPoint?>? = null

    override fun get(index: Int): FocusPoint {
        if (index !in 0 until size) throw IndexOutOfBoundsException("index $index, size $size")
        val series = runSeries[run(index)]
        if (series < 0) return eager[entries[index]]
        val cache = built ?: arrayOfNulls<FocusPoint>(size).also { built = it }
        return cache[index] ?: checkNotNull(aligned.pointAt(series, entries[index])) {
            "no focus point for series $series at datum ${entries[index]}"
        }.also { cache[index] = it }
    }

    /** The run holding entry [index]: the last whose start is at or before it. */
    private fun run(index: Int): Int {
        var lo = 0
        var hi = runStarts.size - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) ushr 1
            if (runStarts[mid] <= index) lo = mid else hi = mid - 1
        }
        return lo
    }
}
