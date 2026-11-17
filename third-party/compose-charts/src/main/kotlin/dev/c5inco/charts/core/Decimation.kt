package dev.c5inco.charts.core

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Smallest index in `0..values.size` with `values[i] >= target`. */
internal fun lowerBound(values: DoubleArray, target: Double): Int {
    var lo = 0
    var hi = values.size
    while (lo < hi) {
        val mid = (lo + hi) ushr 1
        if (values[mid] < target) lo = mid + 1 else hi = mid
    }
    return lo
}

/** Smallest index in `0..values.size` with `values[i] > target`. */
internal fun upperBound(values: DoubleArray, target: Double): Int {
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
 * The visible index slice `[winStart, winEndExclusive)` and the single overhang index on each side
 * (`leftIdx < winStart` and `rightIdx >= winEndExclusive`, or `-1` when none) for a series of
 * length [n]. Under [Gaps.Connect] or [Gaps.ConnectWithin], uses the precomputed [prevDefined] and
 * [nextDefined] tables in O(1) so long `NaN` runs outside the viewport are never scanned.
 */
internal class SeriesSlice(
    val leftIdx: Int,
    val winStart: Int,
    val winEndExclusive: Int,
    val rightIdx: Int,
    val lastDefinedInWin: Int,
) {
    val winCount: Int get() = winEndExclusive - winStart
    val inspectedCount: Int get() = (if (leftIdx >= 0) 1 else 0) + winCount + (if (rightIdx >= 0) 1 else 0)

    fun contains(index: Int): Boolean =
        index >= 0 && (index == leftIdx || index in winStart until winEndExclusive || index == rightIdx)
}

internal fun culledSeriesSlice(
    n: Int,
    inStart: Int,
    inEnd: Int,
    sValues: DoubleArray,
    prevDefined: IntArray,
    nextDefined: IntArray,
    gaps: Gaps,
    hasViewport: Boolean,
    xAscending: Boolean,
): SeriesSlice {
    if (!hasViewport || !xAscending || n <= 0) {
        val last = if (n > 0) prevDefined[n - 1] else -1
        return SeriesSlice(leftIdx = -1, winStart = 0, winEndExclusive = n, rightIdx = -1, lastDefinedInWin = last)
    }
    val winStart = inStart.coerceIn(0, n)
    val winEndExclusive = (inEnd + 1).coerceIn(winStart, n)
    val lastInWin = if (winEndExclusive > winStart) {
        prevDefined[winEndExclusive - 1].takeIf { it >= winStart } ?: -1
    } else if (inStart in 0 until n && sValues[inStart].isFinite()) {
        inStart
    } else {
        -1
    }
    val leftCandidate = min(inStart - 1, n - 1)
    val leftIdx = when {
        leftCandidate < 0 -> -1
        gaps == Gaps.Break -> leftCandidate
        else -> prevDefined[leftCandidate]
    }
    val rightCandidate = max(0, inEnd + 1)
    val rightIdx = when {
        rightCandidate >= n -> -1
        gaps == Gaps.Break -> rightCandidate
        else -> nextDefined[rightCandidate]
    }
    return SeriesSlice(leftIdx, winStart, winEndExclusive, rightIdx, lastInWin)
}

/**
 * M4 per-pixel-column decimation over [slice], respecting [gaps]:
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
internal fun decimateM4Indices(
    slice: SeriesSlice,
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
    var inGapBreak = false

    val leftIdx = slice.leftIdx
    if (leftIdx >= 0) {
        val v = sValues[leftIdx]
        if (v.isFinite()) {
            out[k++] = leftIdx
            lastDefined = leftIdx
        } else if (gaps == Gaps.Break) {
            out[k++] = leftIdx
            inGapBreak = true
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

    for (i in slice.winStart until slice.winEndExclusive) {
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
                    inGapBreak = false
                }
                if (!inGapBreak) {
                    out[k++] = i
                    inGapBreak = true
                }
            }
            continue
        }
        // A finite sample ends any active gap break so a subsequent NaN in the same column emits its own break.
        inGapBreak = false
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
        inGapBreak = false
    }
    val rightIdx = slice.rightIdx
    if (rightIdx >= 0) {
        val v = sValues[rightIdx]
        if (v.isFinite()) {
            if (!maxGap.isNaN() && lastDefined >= 0 && rightIdx > lastDefined + 1 && abs(xValues[rightIdx] - xValues[lastDefined]) > maxGap) {
                out[k++] = lastDefined + 1
            }
            out[k++] = rightIdx
        } else if (gaps == Gaps.Break && !inGapBreak) {
            out[k++] = rightIdx
        }
    }
    return k
}
