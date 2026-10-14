package dev.sebastiano.headroom.shared

import dev.sebastiano.headroom.model.Pace
import dev.sebastiano.headroom.model.PaceStatus
import dev.sebastiano.headroom.model.QuotaWindow
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.Instant

/** What the pace chip says about a window, as the Android `PaceChipState` works it out. */
internal object PaceChip {
    const val JUST_RESET = "justReset"

    /** A window this new and this empty reads as "just reset" rather than "under pace". */
    private const val JUST_RESET_MAX_USED = 0.5
    private const val JUST_RESET_MAX_ELAPSED = 2.0

    /** The chip's id and its points over or under pace, or null when the window has no pace. */
    fun of(window: QuotaWindow, now: Instant): Pair<String, Int>? {
        val expected = Pace.expectedPercent(window, now) ?: return null
        if (window.usedPercent <= JUST_RESET_MAX_USED && expected <= JUST_RESET_MAX_ELAPSED) {
            return JUST_RESET to 0
        }
        val points = abs(window.usedPercent - expected).roundToInt()
        return when (Pace.status(window, now)) {
            PaceStatus.Over -> "over" to points
            PaceStatus.Under -> "under" to points
            PaceStatus.On -> "on" to 0
        }
    }
}
