package dev.sebastiano.headroom.model

import androidx.compose.runtime.Immutable
import kotlin.time.Instant

/** What the detail screen's pace chart shows for one window. */
@Immutable
public data class ChartSummary(
    val start: Instant,
    val end: Instant,
    val usedPercent: Double,
    /** Where even pace is now. */
    val expectedPercent: Double,
    val kind: WindowKind,
    val points: List<UsagePoint>,
    /** When the window reaches 100% at the current rate, or null when that is after the reset. */
    val projectedLimitAt: Instant?,
    /** Where the window ends at the current rate, when it does not reach the limit. */
    val projectedEndPercent: Double?,
)

/**
 * The pace chart of [window]: its span, its [points] inside the current window, and where it is
 * heading at the current rate. Null for a window without a reset time or a length.
 */
public fun chartSummary(
    window: QuotaWindow,
    points: List<UsagePoint>,
    now: Instant,
): ChartSummary? {
    val resetsAt = window.resetsAt ?: return null
    val length = window.length ?: return null
    val start = resetsAt.minus(length)
    // A projection is an estimate; whole minutes keep "in 1d 17h, 1d 1h before" consistent.
    val hit =
        Pace.projectedLimitAt(window, now)?.let {
            Instant.fromEpochSeconds(it.epochSeconds - it.epochSeconds.mod(SECONDS_PER_MINUTE))
        }
    val elapsed = (now - start).inWholeMilliseconds
    val projectedEnd =
        if (hit == null && elapsed > 0) {
            window.usedPercent / elapsed * length.inWholeMilliseconds
        } else {
            null
        }
    return ChartSummary(
        start = start,
        end = resetsAt,
        usedPercent = window.usedPercent,
        expectedPercent = Pace.expectedPercent(window, now) ?: 0.0,
        kind = window.kind,
        // History keeps earlier windows too. Their samples would pile up on the left edge.
        points = points.filter { it.at in start..now },
        projectedLimitAt = hit,
        projectedEndPercent = projectedEnd,
    )
}

private const val SECONDS_PER_MINUTE = 60L
