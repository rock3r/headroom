package dev.c5inco.charts.compose

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ScrollAxisRange
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.horizontalScrollAxisRange
import androidx.compose.ui.semantics.scrollBy
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Velocity
import dev.c5inco.charts.core.ChartScene
import dev.c5inco.charts.core.Focus
import dev.c5inco.charts.core.XY
import kotlinx.coroutines.CoroutineScope
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sign

/**
 * Remembers the chart's pointer handlers. [fresh] (the scene focus comes from), [newest] (the scene
 * on screen) and [onTap] are this composition's; the handlers read them, and the haptics, through
 * state at event time, so a running handler sees the latest without restarting.
 */
@Composable
internal fun rememberChartGestures(
    host: ChartHost,
    fresh: ChartScene?,
    newest: ChartScene?,
    onTap: ((Focus?) -> Unit)?,
): ChartGestures {
    val haptics = rememberUpdatedState(LocalHapticFeedback.current)
    // Haptic steps are judged on the scene focus comes from; a tap on the scene on screen.
    val latest = rememberUpdatedState(fresh)
    val onScreen = rememberUpdatedState(newest)
    val tapped = rememberUpdatedState(onTap)
    val gestureScope = rememberCoroutineScope()
    val decay = rememberSplineBasedDecay<Float>()
    return remember(gestureScope, decay) { ChartGestures(host, latest, onScreen, tapped, haptics, gestureScope, decay) }
}

/**
 * The chart's pointer input: press to focus, tap to select, and, with a viewport, pan, fling,
 * pinch, wheel and long-press scrubbing. Focus moves only through [ChartHost.focus].
 */
internal class ChartGestures(
    private val host: ChartHost,
    private val fresh: State<ChartScene?>,
    private val newest: State<ChartScene?>,
    private val onTap: State<((Focus?) -> Unit)?>,
    private val haptics: State<HapticFeedback>,
    private val scope: CoroutineScope,
    private val decay: DecayAnimationSpec<Float>,
) {
    private val focus: ChartFocus get() = host.focus

    /** The plot's width in px, of the scene on screen, else the chart's. */
    fun plotWidth(): Float = plotWidth(newest.value, host.size)

    /** Focuses the pointer at [p], ticking once when the focus moves to a new step. */
    private fun pointTo(p: XY, touch: Boolean, reach: Float) {
        if (focus.moveTo(FocusTarget.Pointer(p, touch), fresh.value, reach)) {
            haptics.value.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
        }
    }

    /** Reports a tap at [p], resolved against the newest scene's end state, to `onTap`. */
    private fun tap(p: XY, touch: Boolean, reach: Float) {
        onTap.value?.invoke(focus.resolve(FocusTarget.Pointer(p, touch), newest.value, reach))
    }

    /** Press to focus: focus follows the finger from touch-down until it lifts or a parent scrolls. */
    suspend fun PointerInputScope.pressToFocus() {
        val reachPx = FocusReach.toPx()
        awaitEachGesture {
            try {
                val down = awaitFirstDown(requireUnconsumed = false)
                pointTo(XY(down.position.x, down.position.y), down.type == PointerType.Touch, reachPx)
                var scrubbing = false
                while (true) {
                    val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break // lifted, or cancelled
                    if (!scrubbing) {
                        if (change.isConsumed) break // a parent took the gesture
                        val d = change.position - down.position
                        // Vertical intent belongs to the scrolling parent; horizontal is ours.
                        when (dragIntent(d.x, d.y, viewConfiguration.touchSlop)) {
                            DragIntent.Parent -> break
                            DragIntent.Ours -> scrubbing = true
                            DragIntent.Undecided -> Unit
                        }
                    }
                    if (scrubbing) change.consume()
                    pointTo(XY(change.position.x, change.position.y), change.type == PointerType.Touch, reachPx)
                }
            } finally {
                // Also runs when the modifier goes away (interactive = false, disposal).
                focus.clear()
            }
        }
    }

    /**
     * Tap to select: a touch that lifts within the touch slop, without a parent having taken it, is
     * a tap. Nothing happens while the finger is down.
     */
    suspend fun PointerInputScope.tapToSelect() {
        val reachPx = FocusReach.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            while (true) {
                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                if (change.isConsumed) break // a parent took the gesture: a scroll
                if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) break // a drag
                if (!change.pressed) {
                    change.consume()
                    tap(XY(change.position.x, change.position.y), change.type == PointerType.Touch, reachPx)
                    break
                }
            }
        }
    }

    /** Mouse wheel and trackpad scroll on a viewport: horizontal pans, vertical zooms around the pointer. */
    suspend fun PointerInputScope.viewportWheel(viewport: ChartViewport) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                if (event.type != PointerEventType.Scroll) continue
                val change = event.changes.firstOrNull() ?: continue
                val delta = change.scrollDelta
                if (delta == Offset.Zero) continue
                val plot = newest.value?.plot
                val plotLeft = plot?.left ?: 0f
                val plotWidth = (plot?.width ?: host.size.width.toFloat()).coerceAtLeast(1f)
                if (delta.x != 0f && !event.keyboardModifiers.isCtrlPressed && !event.keyboardModifiers.isMetaPressed) {
                    viewport.panBy(wheelPanDomain(delta.x, plotWidth, viewport.span))
                    change.consume()
                } else if (delta.y != 0f) {
                    val anchor = anchorAt(change.position.x, plotLeft, plotWidth, viewport.start, viewport.span)
                    viewport.zoomBy(wheelZoomFactor(delta.y), anchor)
                    change.consume()
                }
            }
        }
    }

    /**
     * Touch on a viewport: a horizontal drag pans and flings on release, two fingers pinch and pan,
     * a long press then drag scrubs, and, with [tapToSelect], a tap selects.
     */
    suspend fun PointerInputScope.viewportGestures(viewport: ChartViewport, tapToSelect: Boolean) {
        val reachPx = FocusReach.toPx()
        awaitEachGesture {
            try {
                val down = awaitFirstDown(requireUnconsumed = false)
                // A new touch stops any running decay fling or animateTo.
                viewport.stopAnimation()
                val slop = viewConfiguration.touchSlop
                val longPressMs = viewConfiguration.longPressTimeoutMillis
                val velocityTracker = VelocityTracker()
                velocityTracker.addPointerInputChange(down)

                var mode = ViewportGesture.Undecided
                val activeId = down.id
                var currentPos = down.position
                var currentType = down.type
                var initialPanDx = 0f

                val finishedBeforeTimeout = withTimeoutOrNull(longPressMs) {
                    while (mode == ViewportGesture.Undecided) {
                        val event = awaitPointerEvent()
                        if (event.changes.count { it.pressed } >= 2) {
                            mode = ViewportGesture.Pinching
                            applyTwoFingerChange(event, viewport, newest.value, host.size)
                            break
                        }
                        val change = event.changes.firstOrNull { it.id == activeId }
                        if (change == null || !change.pressed) {
                            if (change != null && !change.isConsumed && tapToSelect) {
                                change.consume()
                                tap(XY(change.position.x, change.position.y), change.type == PointerType.Touch, reachPx)
                            }
                            mode = ViewportGesture.Finished
                            break
                        }
                        if (change.isConsumed) {
                            mode = ViewportGesture.Finished
                            break
                        }
                        currentPos = change.position
                        currentType = change.type
                        val d = change.position - down.position
                        val prevDist = (change.previousPosition - down.position).getDistance()
                        // Vertical intent belongs to the scrolling parent; horizontal pans.
                        mode = viewportDrag(d.x, d.y, prevDist, change.uptimeMillis - down.uptimeMillis, slop, longPressMs)
                        when (mode) {
                            ViewportGesture.Scrubbing -> {
                                pointTo(XY(change.previousPosition.x, change.previousPosition.y), change.type == PointerType.Touch, reachPx)
                                change.consume()
                            }
                            ViewportGesture.Panning -> {
                                velocityTracker.addPointerInputChange(change)
                                change.consume()
                                initialPanDx = d.x - sign(d.x) * slop
                            }
                            else -> Unit
                        }
                    }
                    true
                }

                if (finishedBeforeTimeout == null && mode == ViewportGesture.Undecided) {
                    mode = ViewportGesture.Scrubbing
                }

                when (mode) {
                    ViewportGesture.Finished, ViewportGesture.Undecided -> Unit
                    ViewportGesture.Scrubbing -> {
                        pointTo(XY(currentPos.x, currentPos.y), currentType == PointerType.Touch, reachPx)
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == activeId } ?: break
                            if (!change.pressed) break
                            change.consume()
                            pointTo(XY(change.position.x, change.position.y), change.type == PointerType.Touch, reachPx)
                        }
                    }
                    ViewportGesture.Panning -> {
                        if (initialPanDx != 0f) {
                            viewport.panBy(-pxToDomain(initialPanDx, plotWidth(), viewport.span))
                        }
                        var shouldFling = true
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.changes.count { it.pressed } >= 2) {
                                shouldFling = false
                                applyTwoFingerChange(event, viewport, newest.value, host.size)
                                runPinchLoop(viewport, { newest.value }, { host.size })
                                break
                            }
                            val change = event.changes.firstOrNull { it.id == activeId } ?: break
                            velocityTracker.addPointerInputChange(change)
                            if (!change.pressed) {
                                change.consume()
                                break
                            }
                            val dx = change.positionChange().x
                            if (dx != 0f) {
                                viewport.panBy(-pxToDomain(dx, plotWidth(), viewport.span))
                            }
                            change.consume()
                        }
                        if (shouldFling) {
                            val maxVel = viewConfiguration.maximumFlingVelocity
                            val vx = velocityTracker.calculateVelocity(Velocity(maxVel, maxVel)).x
                            if (abs(vx) > viewConfiguration.minimumFlingVelocity) fling(viewport, vx)
                        }
                    }
                    ViewportGesture.Pinching -> {
                        runPinchLoop(viewport, { newest.value }, { host.size })
                    }
                }
            } finally {
                focus.clear()
            }
        }
    }

    /** Decays a release at [vx] px/s into pan steps, stopping at a data bound. */
    private fun fling(viewport: ChartViewport, vx: Float) {
        viewport.launchFling(scope) { isActiveFling ->
            var lastValue = 0f
            AnimationState(initialValue = 0f, initialVelocity = vx).animateDecay(decay) {
                if (!isActiveFling()) {
                    cancelAnimation()
                    return@animateDecay
                }
                val frameDx = value - lastValue
                lastValue = value
                if (frameDx == 0f) return@animateDecay
                val deltaDomain = -pxToDomain(frameDx, plotWidth(), span)
                val moved = panStep(deltaDomain)
                if (abs(moved) < abs(deltaDomain) * 0.5 && (!canPanBack || !canPanForward)) {
                    cancelAnimation()
                }
            }
        }
    }
}

/**
 * Accessibility for a viewport: its scroll position and range, and, when [interactive], scrolling
 * and the back, forward, zoom in and zoom out actions. [plotWidth] is read when an action runs.
 */
internal fun SemanticsPropertyReceiver.viewportSemantics(
    viewport: ChartViewport,
    interactive: Boolean,
    plotWidth: () -> Float,
) {
    horizontalScrollAxisRange = ScrollAxisRange(
        value = { (viewport.start - viewport.min).coerceAtLeast(0.0).toFloat() },
        maxValue = { (viewport.bounds.totalSpan - viewport.span).coerceAtLeast(0.0).toFloat() },
    )
    if (interactive) {
        scrollBy { x, _ ->
            viewport.panBy(pxToDomain(x, plotWidth(), viewport.span)) != 0.0
        }
        val act = viewport.actions
        customActions = listOf(
            CustomAccessibilityAction(act.scrollBack) {
                viewport.panBy(-viewport.span * 0.5) != 0.0
            },
            CustomAccessibilityAction(act.scrollForward) {
                viewport.panBy(viewport.span * 0.5) != 0.0
            },
            CustomAccessibilityAction(act.zoomIn) {
                val prev = viewport.span
                viewport.zoomBy(2.0)
                viewport.span != prev
            },
            CustomAccessibilityAction(act.zoomOut) {
                val prev = viewport.span
                viewport.zoomBy(0.5)
                viewport.span != prev
            },
        )
    }
}

internal enum class ViewportGesture { Undecided, Panning, Pinching, Scrubbing, Finished }

/** Whose a drag is, once it has gone past the touch slop. */
internal enum class DragIntent {
    /** Still within the slop. */
    Undecided,

    /** Horizontal: the chart's (a scrub, or a viewport pan). */
    Ours,

    /** Vertical: the scrolling parent's. */
    Parent,
}

/** Vertical intent belongs to the scrolling parent; horizontal is ours. */
internal fun dragIntent(dx: Float, dy: Float, slop: Float): DragIntent = when {
    abs(dy) > slop && abs(dy) > abs(dx) -> DragIntent.Parent
    abs(dx) > slop -> DragIntent.Ours
    else -> DragIntent.Undecided
}

/**
 * What a one-finger touch on a viewport becomes, [elapsedMs] after touch-down and [dx], [dy] px from
 * it: a scrub once it has been held for [longPressMs] without having left the slop
 * ([previousDistance] is how far it had gone before this event), else per [dragIntent], a pan or the
 * parent's scroll, else still undecided.
 */
internal fun viewportDrag(
    dx: Float,
    dy: Float,
    previousDistance: Float,
    elapsedMs: Long,
    slop: Float,
    longPressMs: Long,
): ViewportGesture =
    if (elapsedMs >= longPressMs && previousDistance <= slop) ViewportGesture.Scrubbing
    else when (dragIntent(dx, dy, slop)) {
        DragIntent.Parent -> ViewportGesture.Finished
        DragIntent.Ours -> ViewportGesture.Panning
        DragIntent.Undecided -> ViewportGesture.Undecided
    }

/** The plot's width in px: [scene]'s, else the whole chart of [size]; never below 1. */
internal fun plotWidth(scene: ChartScene?, size: IntSize): Float =
    (scene?.plot?.width ?: size.width.toFloat()).coerceAtLeast(1f)

/** [px] across a plot [plotWidth] px wide, in the domain units of a window [span] wide. */
internal fun pxToDomain(px: Float, plotWidth: Float, span: Double): Double = (px.toDouble() / plotWidth) * span

/** The domain value under [x] px in a window from [start], [span] wide; clamped to the window. */
internal fun anchorAt(x: Float, plotLeft: Float, plotWidth: Float, start: Double, span: Double): Double {
    val fraction = ((x - plotLeft) / plotWidth).toDouble().coerceIn(0.0, 1.0)
    return start + fraction * span
}

/** A horizontal wheel or trackpad scroll of [dx] in the domain units of a window [span] wide. */
internal fun wheelPanDomain(dx: Float, plotWidth: Float, span: Double): Double = (dx.toDouble() * 40.0 / plotWidth) * span

/** The magnification for a vertical wheel scroll of [dy]: each notch toward the user zooms out. */
internal fun wheelZoomFactor(dy: Float): Double = 2.0.pow(-dy.toDouble() * 0.25)

/**
 * One event of a two-finger gesture: zoom by [zoom] (null: none) around [anchorX], the fingers'
 * previous centroid, then pan by the centroid's move, [panPx].
 */
internal class TwoFingerStep(val anchorX: Float, val zoom: Double?, val panPx: Float)

/**
 * The pinch and pan between two events for pointers down in both, at [previous] and then
 * [current] (same order, at least two). Fingers too close together to measure a spread do not zoom.
 */
internal fun twoFingerStep(previous: List<Offset>, current: List<Offset>): TwoFingerStep {
    var prevCx = 0f
    var prevCy = 0f
    var currCx = 0f
    var currCy = 0f
    for (i in previous.indices) {
        prevCx += previous[i].x
        prevCy += previous[i].y
        currCx += current[i].x
        currCy += current[i].y
    }
    val n = previous.size.toFloat()
    val prevCentroid = Offset(prevCx / n, prevCy / n)
    val currCentroid = Offset(currCx / n, currCy / n)
    var prevSpread = 0.0
    var currSpread = 0.0
    for (i in previous.indices) {
        prevSpread += (previous[i] - prevCentroid).getDistance()
        currSpread += (current[i] - currCentroid).getDistance()
    }
    val zoom = if (prevSpread > 0.5 && currSpread > 0.5) {
        (currSpread / prevSpread).takeIf { it.isFinite() && it > 0.0 && it != 1.0 }
    } else null
    return TwoFingerStep(prevCentroid.x, zoom, currCentroid.x - prevCentroid.x)
}

/**
 * Applies two-finger pinch zoom around the previous centroid and two-finger pan for pointers that
 * were down in both the previous and current event, avoiding a centroid jump when a second finger
 * first touches down or lifts.
 */
private fun applyTwoFingerChange(
    event: PointerEvent,
    viewport: ChartViewport,
    scene: ChartScene?,
    size: IntSize,
) {
    val ongoing = event.changes.filter { it.pressed && it.previousPressed }
    if (ongoing.size >= 2) {
        val step = twoFingerStep(ongoing.map { it.previousPosition }, ongoing.map { it.position })
        val plotLeft = scene?.plot?.left ?: 0f
        val plotWidth = plotWidth(scene, size)
        val anchor = anchorAt(step.anchorX, plotLeft, plotWidth, viewport.start, viewport.span)
        if (step.zoom != null) viewport.zoomBy(step.zoom, anchor)
        if (step.panPx != 0f) {
            viewport.panBy(-pxToDomain(step.panPx, plotWidth, viewport.span))
        }
    }
    event.changes.forEach { if (it.positionChanged()) it.consume() }
}

private suspend fun AwaitPointerEventScope.runPinchLoop(
    viewport: ChartViewport,
    scene: () -> ChartScene?,
    size: () -> IntSize,
) {
    while (true) {
        val event = awaitPointerEvent()
        if (event.changes.none { it.pressed }) break
        applyTwoFingerChange(event, viewport, scene(), size())
    }
}
