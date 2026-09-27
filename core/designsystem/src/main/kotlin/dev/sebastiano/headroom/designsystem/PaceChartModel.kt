package dev.sebastiano.headroom.designsystem

import androidx.compose.runtime.Immutable
import dev.sebastiano.headroom.model.UsagePoint
import java.time.Instant

/** Everything the pace chart draws, in model terms. */
@Immutable
data class PaceChartModel(
    val start: Instant,
    val end: Instant,
    val now: Instant,
    val usedPercent: Double,
    val points: List<UsagePoint>,
    /** When usage at the current rate reaches 100%, or null when that is after the reset. */
    val projectedLimitAt: Instant?,
    /** One label per equal slice of the window, for example the days of the week. */
    val tickLabels: List<String>,
)
