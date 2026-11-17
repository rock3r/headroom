package dev.c5inco.charts.compose

import androidx.compose.runtime.State
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.ui.unit.dp
import dev.c5inco.charts.core.ChartScene
import dev.c5inco.charts.core.Focus
import dev.c5inco.charts.core.FocusDetail
import dev.c5inco.charts.core.FocusMode
import dev.c5inco.charts.core.XY
import dev.c5inco.charts.core.focusAt

/** How far a nearest-point focus reaches from the finger. */
internal val FocusReach = 40.dp

/**
 * What the chart is focused on, apart from any scene. A target is never a focus: it resolves to
 * one against a scene each time it is read, so a focus cannot outlive the data it came from.
 *
 * A pointer is the only kind yet. Focus chosen some other way (a datum picked by key or by an
 * accessibility action) is another case here, resolved by [resolve] like the pointer.
 */
internal sealed interface FocusTarget {
    /** The focus this target picks out on [scene]; [reach] in px bounds a nearest-point search. */
    fun resolve(scene: ChartScene, reach: Float): Focus?

    /**
     * A finger, mouse or stylus at [at]. [touch] lifts an above-placed tooltip clear of a fingertip.
     */
    data class Pointer(val at: XY, val touch: Boolean) : FocusTarget {
        override fun resolve(scene: ChartScene, reach: Float): Focus? = scene.focusAt(at, reach)
    }
}

/**
 * A chart's focus: what it is pointed at ([target]), the focus derived from that and a scene, the
 * haptic step it is on, and the last focus reported to `onFocusChange`.
 *
 * Every change of focus goes through [moveTo] and [clear]; [resolve] is the one way a target becomes
 * a focus, for the focus shown, for haptic steps and for a tap.
 */
@Stable
internal class ChartFocus {
    /**
     * Gesture state: written by gestures, read by [focusOn]'s derivation and by the tooltip's
     * placement ([touchY]), never directly in composition.
     */
    var target by mutableStateOf<FocusTarget?>(null)
        private set

    /** The last focus handed to `onFocusChange`. Plain, not state: writing it must not recompose. */
    var reported: Focus? = null
        private set

    // The haptic step last moved to, and the scene it was judged on. Plain: only gestures read them.
    private var stepScene: ChartScene? = null
    private var step: Any? = null

    // The last resolution, by identity of its target and scene. A pointer move resolves the same
    // target on the same scene twice (the haptic step in [moveTo], then the focus shown), and a
    // dense scene makes each resolution count. Plain: main-thread only, and a pure function's cache.
    private var resolvedTarget: FocusTarget? = null
    private var resolvedScene: ChartScene? = null
    private var resolvedReach = Float.NaN
    private var resolved: Focus? = null

    /** What [target] picks out on [scene], or null with no target or no scene. */
    fun resolve(target: FocusTarget?, scene: ChartScene?, reach: Float): Focus? {
        if (target == null || scene == null) return null
        if (target === resolvedTarget && scene === resolvedScene && reach == resolvedReach) return resolved
        val focus = target.resolve(scene, reach)
        resolvedTarget = target
        resolvedScene = scene
        resolvedReach = reach
        resolved = focus
        return focus
    }

    /**
     * The focus shown: [target] resolved against [scene], the scene compiled from the current spec
     * ([SceneFrame.fresh]), never stored, so it cannot outlive the data it came from. Remember it
     * keyed on its arguments. Structural equality: a pointer move within the same focus recomposes
     * nothing.
     */
    fun focusOn(scene: ChartScene?, interactive: Boolean, reach: Float): State<Focus?> =
        derivedStateOf(structuralEqualityPolicy()) { if (interactive) resolve(target, scene, reach) else null }

    /**
     * Focuses [next], judging haptic steps on [scene] (the scene focus comes from). True when the
     * focus moved to a new step: the caller's haptic tick.
     *
     * One tick per step of the needle, judged on the newest scene for both positions, so data moving
     * under a still finger never ticks. The previous step is reused unless the scene has changed
     * since.
     */
    fun moveTo(next: FocusTarget, scene: ChartScene?, reach: Float): Boolean {
        val before = if (scene === stepScene) step else resolve(target, scene, reach)?.step()
        val after = resolve(next, scene, reach)?.step()
        stepScene = scene
        step = after
        target = next
        return after != null && after != before
    }

    /** No focus: the gesture ended, was cancelled, or its handler went away. */
    fun clear() {
        target = null
        stepScene = null
        step = null
        resolvedTarget = null
        resolvedScene = null
        resolved = null
    }

    /** The y of a touch pointer, for the tooltip's finger clearance; NaN otherwise. Read at layout. */
    fun touchY(): Float {
        val pointer = target as? FocusTarget.Pointer ?: return Float.NaN
        return if (pointer.touch) pointer.at.y else Float.NaN
    }

    /** Reports [shown] to [onFocusChange] after every composition whose focus differs from the last one reported. */
    fun report(shown: Focus?, onFocusChange: ((Focus?) -> Unit)?) {
        if (shown != reported) {
            reported = shown
            onFocusChange?.invoke(shown)
        }
    }

    /** Leaving composition: reports `null` once if a focus had been reported. */
    fun dispose(onFocusChange: ((Focus?) -> Unit)?) {
        if (reported != null) {
            reported = null
            onFocusChange?.invoke(null)
        }
    }
}

/**
 * The haptic step: the axis position (a column, or a row of horizontal bars) when scrubbing, read
 * from the focused points aligned to the axis, never from a dot's own index ([position]); a dot
 * with no aligned point beside it is a step of its own. Otherwise a single datum, by its id when it
 * has one (each treemap leaf is its own step).
 */
internal fun Focus.step(): Any =
    if (mode == FocusMode.NearestX || mode == FocusMode.NearestY) position() ?: (primary.series to primary.datum)
    else (primary.detail as? FocusDetail.Tile)?.id ?: (primary.series to primary.datum)
