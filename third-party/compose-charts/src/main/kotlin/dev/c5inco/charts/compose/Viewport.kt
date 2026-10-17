package dev.c5inco.charts.compose

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.MotionDurationScale
import dev.c5inco.charts.core.ViewportBounds
import dev.c5inco.charts.core.ViewportRange
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import kotlin.coroutines.coroutineContext

/**
 * Accessibility action labels exposed in semantics when a [Chart] has a [ChartViewport]. Override
 * for localisation.
 */
@Immutable
data class ViewportActions(
    val scrollBack: String = "Scroll back",
    val scrollForward: String = "Scroll forward",
    val zoomIn: String = "Zoom in",
    val zoomOut: String = "Zoom out",
)

/**
 * Hoisted, saveable state for panning and zooming a continuous x axis ([dev.c5inco.charts.core.NumericAxis]
 * or [dev.c5inco.charts.core.TimeAxis], where domain values are epoch milliseconds).
 *
 * Holds the visible window `[start, end]` within `[min, max]`, constrained so its width `end - start`
 * stays in `[minSpan, maxSpan]`. Pass to [Chart] as `Chart(spec, viewport = viewport)`.
 *
 * - **Gestures.** When a viewport is attached, a one-finger horizontal drag pans and flings on
 *   release, a two-finger pinch zooms around the fingers' centroid (and pans with it), and a
 *   long-press-then-drag scrubs with the focus needle and tooltip.
 * - **Imperative control.** [panBy], [zoomBy], [snapTo] and [animateTo] move the window from
 *   code (for range chips such as "1W / 1M / 3M / 1Y"). Any touch or imperative move cancels an
 *   in-flight fling or [animateTo].
 * - **Recomposition scope.** [start], [end] and [span] are snapshot state that change on every
 *   frame of a pan, pinch, fling or [animateTo]. Pass `viewport` straight to [Chart] and wrap any
 *   UI state derived from it (such as which range chip is selected) in [androidx.compose.runtime.derivedStateOf]
 *   so the surrounding screen only recomposes when that boolean or index flips:
 *   ```kotlin
 *   val selectedRange by remember(viewport) {
 *       derivedStateOf {
 *           ranges.indexOfFirst { (_, span) -> abs(viewport.end - max) < eps && abs(viewport.span - span) < eps }
 *       }
 *   }
 *   ```
 */
@Stable
class ChartViewport(
    val min: Double,
    val max: Double,
    initialStart: Double = min,
    initialEnd: Double = max,
    val minSpan: Double = 0.0,
    val maxSpan: Double = max - min,
    actions: ViewportActions = ViewportActions(),
) {
    /** Accessibility action labels exposed in semantics; updatable on locale changes without resetting the window. */
    var actions: ViewportActions by mutableStateOf(actions)
    constructor(
        min: Instant,
        max: Instant,
        initialStart: Instant = min,
        initialEnd: Instant = max,
        minSpan: Duration? = null,
        maxSpan: Duration? = null,
        actions: ViewportActions = ViewportActions(),
    ) : this(
        min = min.toEpochMilli().toDouble(),
        max = max.toEpochMilli().toDouble(),
        initialStart = initialStart.toEpochMilli().toDouble(),
        initialEnd = initialEnd.toEpochMilli().toDouble(),
        minSpan = minSpan?.toMillis()?.toDouble() ?: 0.0,
        maxSpan = maxSpan?.toMillis()?.toDouble() ?: (max.toEpochMilli() - min.toEpochMilli()).toDouble(),
        actions = actions,
    )

    internal val bounds = ViewportBounds(min = min, max = max, minSpan = minSpan, maxSpan = maxSpan)

    private val initial = bounds.clamp(initialStart, initialEnd)

    /** Start of the visible x window, in domain units (epoch milliseconds on a `TimeAxis`). */
    var start: Double by mutableDoubleStateOf(initial.start)
        private set

    /** End of the visible x window, in domain units (epoch milliseconds on a `TimeAxis`). */
    var end: Double by mutableDoubleStateOf(initial.end)
        private set

    /** Current visible width `end - start`, in domain units. */
    val span: Double get() = end - start

    /** Current `[start, end]` snapshot as a core [ViewportRange]. */
    val range: ViewportRange get() = ViewportRange(start, end)

    private val eps: Double get() = bounds.totalSpan * 1e-9

    val canPanBack: Boolean get() = start > bounds.min + eps
    val canPanForward: Boolean get() = end < bounds.max - eps
    val canZoomIn: Boolean get() = span > bounds.effectiveMinSpan * (1.0 + 1e-6)
    val canZoomOut: Boolean get() = span < bounds.effectiveMaxSpan * (1.0 - 1e-6)

    private var activeJob: Job? = null

    /** Cancels any running [animateTo] or fling decay at the window's current position. */
    internal fun stopAnimation() {
        activeJob?.cancel()
        activeJob = null
    }

    internal fun launchFling(scope: CoroutineScope, block: suspend ChartViewport.(isActiveFling: () -> Boolean) -> Unit) {
        stopAnimation()
        lateinit var flingJob: Job
        flingJob = scope.launch {
            try {
                block { activeJob === flingJob && isActive }
            } finally {
                if (activeJob === flingJob) activeJob = null
            }
        }
        activeJob = flingJob
    }

    /** Moves the window immediately to `[start, end]`, clamped to bounds and span constraints. */
    fun snapTo(start: Double, end: Double) {
        stopAnimation()
        val next = bounds.clamp(start, end)
        applyRange(next)
    }

    /** Moves the window immediately to `[start, end]` on a time axis, clamped to bounds and span constraints. */
    fun snapTo(start: Instant, end: Instant) =
        snapTo(start.toEpochMilli().toDouble(), end.toEpochMilli().toDouble())

    /**
     * Shifts the visible window by [delta] domain units (positive moves forward toward [max],
     * negative backward toward [min]), preserving [span] and clamping at `[min, max]`. Returns the
     * actual domain shift applied after clamping.
     */
    fun panBy(delta: Double): Double {
        stopAnimation()
        return panStep(delta)
    }

    /** Internal pan step used by fling frames so the fling does not cancel its own job. */
    internal fun panStep(delta: Double): Double {
        if (!delta.isFinite() || delta == 0.0) return 0.0
        val before = start
        val next = bounds.pan(ViewportRange(start, end), delta)
        applyRange(next)
        return next.start - before
    }

    /**
     * Scales the visible window by magnification [factor] (`> 1` zooms in, `< 1` zooms out) around
     * [anchor] (in domain units; defaults to the window's centre). While neither data bound is hit,
     * [anchor] stays at the same relative position in the window.
     */
    fun zoomBy(factor: Double, anchor: Double = start + (end - start) * 0.5) {
        stopAnimation()
        val next = bounds.zoom(ViewportRange(start, end), factor, anchor)
        applyRange(next)
    }

    /**
     * Animates the visible window to `[targetStart, targetEnd]` (clamped to bounds and span
     * constraints) using a non-overshooting spec ([ChartMotion.Viewport] by default). Jumps
     * immediately when animations are disabled (`MotionDurationScale.scaleFactor == 0f`) or
     * [animationSpec] is [ChartMotion.Instant].
     */
    suspend fun animateTo(
        targetStart: Double,
        targetEnd: Double,
        animationSpec: FiniteAnimationSpec<Float> = ChartMotion.Viewport,
    ) {
        val target = bounds.clamp(targetStart, targetEnd)
        stopAnimation()
        val fromStart = start
        val fromEnd = end
        if (fromStart == target.start && fromEnd == target.end) return
        val reduced = coroutineContext[MotionDurationScale]?.scaleFactor == 0f
        if (reduced || animationSpec.isInstant()) {
            applyRange(target)
            return
        }
        coroutineScope {
            val job = coroutineContext[Job]
            activeJob = job
            try {
                val anim = Animatable(0f)
                anim.animateTo(1f, animationSpec) {
                    if (activeJob !== job || !isActive) return@animateTo
                    val t = value.coerceIn(0f, 1f).toDouble()
                    val s = fromStart + (target.start - fromStart) * t
                    val e = fromEnd + (target.end - fromEnd) * t
                    applyRange(bounds.clamp(s, e))
                }
                if (activeJob === job && isActive) applyRange(target)
            } finally {
                if (activeJob === job) activeJob = null
            }
        }
    }

    /** Animates the visible window to `[targetStart, targetEnd]` on a time axis. */
    suspend fun animateTo(
        targetStart: Instant,
        targetEnd: Instant,
        animationSpec: FiniteAnimationSpec<Float> = ChartMotion.Viewport,
    ) = animateTo(targetStart.toEpochMilli().toDouble(), targetEnd.toEpochMilli().toDouble(), animationSpec)

    private fun applyRange(next: ViewportRange) {
        if (start != next.start) start = next.start
        if (end != next.end) end = next.end
    }

    companion object {
        /** Saves and restores a [ChartViewport], keeping its current `[start, end]` across recreation. */
        fun Saver(
            min: Double,
            max: Double,
            minSpan: Double = 0.0,
            maxSpan: Double = max - min,
            actions: ViewportActions = ViewportActions(),
        ): Saver<ChartViewport, DoubleArray> = Saver(
            save = { doubleArrayOf(it.start, it.end) },
            restore = { saved ->
                ChartViewport(
                    min = min,
                    max = max,
                    initialStart = saved[0],
                    initialEnd = saved[1],
                    minSpan = minSpan,
                    maxSpan = maxSpan,
                    actions = actions,
                )
            },
        )
    }
}

/**
 * Remembers and saves a [ChartViewport] over `[min, max]` (numeric x values, or epoch milliseconds
 * on a time axis).
 */
@Composable
fun rememberChartViewport(
    min: Double,
    max: Double,
    initialStart: Double = min,
    initialEnd: Double = max,
    minSpan: Double = 0.0,
    maxSpan: Double = max - min,
    actions: ViewportActions = ViewportActions(),
): ChartViewport = rememberSaveable(
    min, max, minSpan, maxSpan,
    saver = ChartViewport.Saver(min, max, minSpan, maxSpan, actions),
) {
    ChartViewport(
        min = min,
        max = max,
        initialStart = initialStart,
        initialEnd = initialEnd,
        minSpan = minSpan,
        maxSpan = maxSpan,
        actions = actions,
    )
}.also { it.actions = actions }

/**
 * Remembers and saves a [ChartViewport] over `[min, max]` instants for a [dev.c5inco.charts.core.TimeAxis].
 */
@Composable
fun rememberChartViewport(
    min: Instant,
    max: Instant,
    initialStart: Instant = min,
    initialEnd: Instant = max,
    minSpan: Duration? = null,
    maxSpan: Duration? = null,
    actions: ViewportActions = ViewportActions(),
): ChartViewport {
    val minMs = min.toEpochMilli().toDouble()
    val maxMs = max.toEpochMilli().toDouble()
    val minSpanMs = minSpan?.toMillis()?.toDouble() ?: 0.0
    val maxSpanMs = maxSpan?.toMillis()?.toDouble() ?: (maxMs - minMs)
    return rememberChartViewport(
        min = minMs,
        max = maxMs,
        initialStart = initialStart.toEpochMilli().toDouble(),
        initialEnd = initialEnd.toEpochMilli().toDouble(),
        minSpan = minSpanMs,
        maxSpan = maxSpanMs,
        actions = actions,
    )
}
