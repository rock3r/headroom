package dev.sebastiano.headroom.designsystem

import androidx.compose.runtime.Immutable
import dev.sebastiano.headroom.model.Pace
import dev.sebastiano.headroom.model.PaceStatus
import dev.sebastiano.headroom.model.QuotaWindow
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.Instant

/** What the pace chip says about one window. */
@Immutable
sealed interface PaceChipState {
    data class Over(val points: Int) : PaceChipState

    data class Under(val points: Int) : PaceChipState

    data object OnPace : PaceChipState

    /** The window reset recently and nothing is used yet. */
    data object JustReset : PaceChipState

    companion object {
        /** A window this new and this empty reads as "just reset" rather than "under pace". */
        private const val JUST_RESET_MAX_USED = 0.5
        private const val JUST_RESET_MAX_ELAPSED = 2.0

        fun from(window: QuotaWindow, now: Instant): PaceChipState {
            val expected = Pace.expectedPercent(window, now) ?: return OnPace
            if (window.usedPercent <= JUST_RESET_MAX_USED && expected <= JUST_RESET_MAX_ELAPSED) {
                return JustReset
            }
            val points = abs(window.usedPercent - expected).roundToInt()
            return when (Pace.status(window, now)) {
                PaceStatus.Over -> Over(points)
                PaceStatus.Under -> Under(points)
                PaceStatus.On -> OnPace
            }
        }
    }
}
