package dev.sebastiano.headroom.model.stats

import dev.sebastiano.headroom.model.UsagePoint

/** Reads "how much was used when each window reset" out of the stored usage history. */
public object ResetPeaks {
    /** A drop smaller than this is noise, not a reset. */
    public const val MIN_DROP_POINTS: Double = 5.0

    /** The highest usage before each drop, oldest first. The window still running is left out. */
    public fun usedAtResets(points: List<UsagePoint>): List<Double> =
        resets(points).map { it.usedPercent }

    /**
     * The highest point before each drop, oldest first, with the time it was first reached. The
     * window still running is left out.
     */
    public fun resets(points: List<UsagePoint>): List<UsagePoint> {
        val peaks = mutableListOf<UsagePoint>()
        var peak: UsagePoint? = null
        for (point in points) {
            val current = peak
            if (current != null && current.usedPercent - point.usedPercent >= MIN_DROP_POINTS) {
                peaks += current
                peak = point
            } else if (current == null || point.usedPercent > current.usedPercent) {
                peak = point
            }
        }
        return peaks
    }
}
