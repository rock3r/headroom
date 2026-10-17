package dev.c5inco.charts.core

/** Stacking layout. A port of d3-shape's `stack()` with `stackOffsetDiverging`. */
object Stack {
    /**
     * Stacks [values] (indexed `[series][datum]`). Positive values stack upward from zero and
     * negative values downward, so mixed-sign data never overlaps. A missing (non-finite) value
     * gets a zero-height segment at the current top, so series above it still stack correctly.
     *
     * @return `[series][datum]` = lower and upper bound of each segment.
     */
    fun diverging(values: List<List<Double>>): List<List<Pair<Double, Double>>> {
        val n = values.maxOfOrNull { it.size } ?: 0
        val result = values.map { MutableList(it.size) { 0.0 to 0.0 } }
        for (i in 0 until n) {
            var positive = 0.0
            var negative = 0.0
            values.forEachIndexed { s, series ->
                val v = series.getOrNull(i)?.takeIf { it.isFinite() }
                if (v == null) {
                    if (i < result[s].size) result[s][i] = positive to positive
                    return@forEachIndexed
                }
                result[s][i] = if (v >= 0) {
                    (positive to positive + v).also { positive += v }
                } else {
                    (negative + v to negative).also { negative += v }
                }
            }
        }
        return result
    }
}
