package dev.c5inco.charts.compose

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.SnapSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ScrollAxisRange
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.horizontalScrollAxisRange
import androidx.compose.ui.semantics.scrollBy
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import dev.c5inco.charts.core.CartesianChart
import dev.c5inco.charts.core.ChartScene
import dev.c5inco.charts.core.ChartSpec
import dev.c5inco.charts.core.Extent
import dev.c5inco.charts.core.Focus
import dev.c5inco.charts.core.FocusMode
import dev.c5inco.charts.core.NumericAxis
import dev.c5inco.charts.core.SceneMotion
import dev.c5inco.charts.core.TimeAxis
import dev.c5inco.charts.core.ViewportRange
import dev.c5inco.charts.core.XY
import dev.c5inco.charts.core.compile
import dev.c5inco.charts.core.contentDescription
import dev.c5inco.charts.core.focusAt
import kotlin.coroutines.coroutineContext
import androidx.compose.runtime.staticCompositionLocalOf
import dev.c5inco.charts.core.ChartMetrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sign

/** Motion tokens. Literal specs live here and nowhere else. */
object ChartMotion {
    /**
     * Marks are data: their position is read as value, so they settle without bounce. Matches
     * Material's standard slow spatial spring.
     */
    val Reveal: FiniteAnimationSpec<Float> = spring(dampingRatio = 0.9f, stiffness = 300f)

    /**
     * Critically damped spring for [ChartViewport.animateTo]: settles without ever overshooting
     * the requested domain window.
     */
    val Viewport: FiniteAnimationSpec<Float> = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 400f)

    /**
     * No tween: each new scene is drawn as it is, from the frame it is compiled. For small charts
     * that update often and are read at a glance (sparklines in a scrolling list), where a tween
     * per update costs frames and says nothing. With the default `animateEntrance`, the first
     * scene also appears at once: there is nothing to grow in over.
     */
    val Instant: FiniteAnimationSpec<Float> = snap()
}

/**
 * Where a [Chart] compiles its scenes after the first. Whatever the policy, a `Chart` instance's
 * first scene compiles in place, in the composition that first has a size, so its first frame (and
 * any screenshot) has it.
 */
enum class SceneCompile {
    /**
     * The default. Later scenes compile off the main thread, the newest revision winning, except one
     * arriving while a focus is shown (a live price ticking under a scrubbing finger): that compiles
     * in place, so a shown tooltip and `onFocusChange` never pause for a compile.
     */
    Background,

    /**
     * Every later scene compiles off the main thread, a focused one too, so even a chart scrubbed on
     * a fast feed never compiles on the main thread. The price is a pause in the readout: while the
     * scene for the current spec compiles there is no focus (no tooltip, no needle, no haptic), and
     * `onFocusChange` reports `null` once if it had reported a focus. When the scene lands with the
     * finger still down, focus is found again under the finger, against the new scene, and reported.
     * Landing never ticks a haptic, even where the finger now sits on another step; only the finger
     * moving to a new step, judged on one current scene, does. An overlay keeps the scene on screen
     * throughout, so it does not blink.
     */
    BackgroundEvenWhileFocused,

    /**
     * Every scene compiles in place, in the composition that receives its spec. For a spec whose
     * formatters are not thread-safe, or a test that changes the spec and checks the result in the
     * same frame.
     */
    InPlace,
}

/** A spec that lands at once. Such scenes are shown synchronously rather than tweened. */
internal fun FiniteAnimationSpec<Float>.isInstant(): Boolean = this is SnapSpec<*> && delay <= 0

/**
 * Draws [spec]. Compiles it to a scene at the measured size, animates between scenes as data,
 * size or style change, and resolves touch into a [Focus] for the tooltip.
 *
 * Give it a size through [modifier]; the chart fills it.
 *
 * **Compiling.** A scene depends on the spec, the size, the density and the style's geometry (its
 * [ChartStyle.metrics] and text styles); together they are the scene's revision, and each compiled
 * scene is tagged with the revision it came from. A style change that is only paint (colours,
 * stroke widths, dimming) keeps the revision: it repaints the current scene and compiles nothing.
 * This instance's first scene compiles in place, so its first frame (and any screenshot) has it.
 * Later ones compile off the main thread (see [compile] and [SceneCompile]), the newest revision winning: a
 * compile superseded by a newer spec, size or style, or by the chart leaving composition, is
 * discarded. Until its scene lands the chart keeps drawing, and tweening toward, the scene it has.
 * Specs, and the [dev.c5inco.charts.core.ValueFormat]s in them, are therefore read on a background
 * thread: keep specs immutable and formatters thread-safe (pure functions, or `java.time`
 * formatters; not a shared `java.text.DecimalFormat`). Text is only ever measured on the main
 * thread.
 *
 * Focus is always resolved against the scene compiled from this composition's [spec], so the
 * [tooltip] and [onFocusChange] never see a focus from older data than the spec they were given.
 * While that scene is still compiling there is no focus: no tooltip, no needle and no haptic, and
 * `null` is reported if a focus had been. With the default [SceneCompile.Background], a spec that
 * arrives while a focus is shown (a live price ticking under a scrubbing finger) compiles in place,
 * so a shown focus never pauses; [SceneCompile.BackgroundEvenWhileFocused] compiles it off the main
 * thread and pauses the focus until its scene lands.
 * [onFocusChange] reports every change of the focused payload, including a new value under a
 * stationary finger, and reports `null` when the gesture ends, is cancelled, [interactive] turns
 * off or the chart leaves composition. Haptics tick only when the finger moves to a new step.
 *
 * A focus's datum index means "this position in the current data", not identity. When the chart
 * starts showing a different entity (another route, another account), reset its interaction by
 * wrapping it in `key(id) { Chart(...) }`.
 *
 * @param animationSpec drives every scene change: data, size and style. [ChartMotion.Instant]
 *   draws each scene as it is. With animations turned off on the device, every change lands on
 *   its final scene at once.
 * @param animateEntrance whether the first scene this Chart instance receives grows in from the
 *   baseline. Read when that first scene arrives (after the first measure); later scenes always
 *   tween from the current one. The entrance lives as long as this composition: a Chart that
 *   leaves composition (navigating away, scrolling out of a lazy list) and comes back, or a new
 *   instance from `key(id)`, grows in again. To reveal once per entity, hoist that decision and
 *   save it yourself:
 *   ```
 *   var revealed by rememberSaveable(id) { mutableStateOf(false) }
 *   val entrance = remember(id) { !revealed } // decided once, before the first scene arrives
 *   SideEffect { revealed = true }
 *   Chart(spec, animateEntrance = entrance)
 *   ```
 * @param onFocusChange see above. Pass `tooltip = null` to drive your own readout (a scrubbed
 *   price header) from this alone.
 * @param onTap opts in to selecting by tap instead of pressing to focus, for charts in a scrolling
 *   page (a treemap whose tiles open details). A touch that lifts within the touch slop, without a
 *   parent having taken it, is a tap: it reports the [Focus] under it, resolved against the newest
 *   scene's end state (null over nothing, such as a gap between tiles), and consumes the lift so a
 *   clickable parent does not also click. Nothing happens while the finger is down: no focus,
 *   tooltip, dimming or haptic, and [onFocusChange] is never called, so a scroll or swipe that
 *   starts on the chart is just a scroll. Resolve the tap by [dev.c5inco.charts.core.FocusPoint.id]
 *   where the mark has ids. [interactive] = false turns taps off too.
 * @param tooltipPlacement where [tooltip] sits relative to the focus. [TooltipPlacement.Auto]
 *   rides beside the needle when scrubbing and above the point otherwise.
 * @param compile where scenes after this instance's first compile: [SceneCompile.Background] (the
 *   default), [SceneCompile.BackgroundEvenWhileFocused], or [SceneCompile.InPlace] for a spec whose
 *   formatters are not thread-safe, or a test that changes the spec and checks the result in the
 *   same frame.
 * @param viewport optional hoisted pan/zoom window ([ChartViewport], remembered with
 *   [rememberChartViewport]) for a [CartesianChart] on a [NumericAxis] or [TimeAxis] (any other
 *   chart or a `CategoryAxis` throws [IllegalArgumentException]). When non-null, one-finger
 *   horizontal drag pans (flinging on release with `splineBasedDecay`), two-finger pinch zooms and
 *   pans around the fingers' centroid, and long-press-then-drag scrubs with the needle and tooltip.
 *   Viewport changes compile in place and draw without tweening the scene so the chart tracks the
 *   finger; data, size and style changes keep tweening as usual. Null (the default) leaves the full
 *   domain and immediate press-to-scrub behaviour unchanged.
 * @param overlay composed above the marks and the focus needle, below the tooltip, with the scene
 *   the marks are drawn toward: the animation's end state, so an overlay lands where the marks will
 *   settle; it does not follow them through a tween or the entrance. For UI pinned to the data: a
 *   live dot on [dev.c5inco.charts.core.lastPoint], a badge at [dev.c5inco.charts.core.pointAt].
 *   It is the scene compiled from this composition's [spec] except for the frame or two a newer
 *   scene compiles off the main thread, when it is the one before, still on screen: an overlay
 *   never blinks out, and never runs ahead of the marks it is pinned to.
 */
@Composable
fun Chart(
    spec: ChartSpec,
    modifier: Modifier = Modifier,
    style: ChartStyle = ChartStyle.clean(),
    animationSpec: FiniteAnimationSpec<Float> = ChartMotion.Reveal,
    interactive: Boolean = true,
    animateEntrance: Boolean = true,
    onFocusChange: ((Focus?) -> Unit)? = null,
    onTap: ((Focus?) -> Unit)? = null,
    tooltipPlacement: TooltipPlacement = TooltipPlacement.Auto,
    compile: SceneCompile = SceneCompile.Background,
    viewport: ChartViewport? = null,
    overlay: (@Composable BoxScope.(ChartScene) -> Unit)? = null,
    tooltip: (@Composable (Focus, ChartScene) -> Unit)? = { focus, scene -> ChartTooltip(focus, scene, style) },
) {
    if (viewport != null) {
        require(spec is CartesianChart && (spec.x is NumericAxis || spec.x is TimeAxis)) {
            val axisOrKind = if (spec is CartesianChart) spec.x::class.simpleName else spec::class.simpleName
            "A viewport requires a CartesianChart with a NumericAxis or TimeAxis, got $axisOrKind"
        }
    }
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    // Only the style's geometry is part of a scene's revision; the rest is paint, read while drawing.
    val textStyles = remember(style) { style.textStyles() }
    val texts = remember(measurer, textStyles) { TextCache(measurer, textStyles) }
    val metrics = remember(style, density) { style.metrics(density) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val viewportRange = viewport?.range
    val baseInputs = remember(spec, size, metrics, texts) {
        if (size.width == 0 || size.height == 0) null
        else BaseInputs(spec, Extent(size.width.toFloat(), size.height.toFloat()), metrics, texts)
    }
    val inputs = remember(baseInputs, viewportRange) {
        baseInputs?.let { SceneInputs(it, viewportRange) }
    }
    val scenes = remember { SceneStore() }
    val reported = remember { FocusReport() }
    // In place: the first scene, so the first frame has it; every scene when asked to; a viewport
    // pan/zoom on the current spec/size/style so the chart never lags behind the finger; and, by
    // default, a scene arriving while a focus is shown, so the tooltip and onFocusChange never pause.
    val inPlace = remember(inputs, compile) {
        val viewportOnly = inputs != null && scenes.newest?.let {
            it.inputs.base == inputs.base && it.inputs.viewport != inputs.viewport
        } == true
        val here = viewportOnly || when (compile) {
            SceneCompile.Background -> !scenes.started || reported.last != null
            SceneCompile.BackgroundEvenWhileFocused -> !scenes.started
            SceneCompile.InPlace -> true
        }
        if (inputs != null && here) Compiled(inputs, inputs.compileHere()) else null
    }
    // The newest scene, in place or landed from a background compile, and the revision it is for.
    val compiled = inPlace ?: scenes.newest
    SideEffect {
        if (inPlace != null) {
            scenes.started = true
            if (scenes.newest !== inPlace) scenes.newest = inPlace // not read this composition: no recompose
        }
    }
    if (inputs != null && inPlace == null) {
        val context = LocalSceneCompileContext.current
        // Restarted by a newer revision, and cancelled by it or by leaving composition before it can
        // land (withContext drops the result of a cancelled compile): only the latest scene lands.
        LaunchedEffect(inputs) {
            if (scenes.newest?.inputs == inputs) return@LaunchedEffect // back to the revision it has
            scenes.newest = Compiled(inputs, inputs.compileOffMain(context))
        }
    }
    // What is drawn and tweened toward: the newest scene, even while a newer revision compiles.
    val target = compiled?.scene
    val targetBase = compiled?.inputs?.base
    val targetViewport = compiled?.inputs?.viewport
    // What focus, the tooltip and onFocusChange may use: only the scene of this composition's inputs.
    val fresh = compiled?.takeIf { inputs != null && it.inputs == inputs }?.scene

    val transition = remember { SceneTransition() }
    // A scene that does not tween (instant spec, entrance disabled on first scene, or a viewport
    // window change — including attaching or removing a viewport — on the same spec/size/style) is
    // shown while this composition applies, so it is drawn in the same frame; the effect below then
    // finds it already in place.
    val instant = animationSpec.isInstant()
    val viewportMoved = targetBase != null && transition.base == targetBase && transition.viewport != targetViewport
    SideEffect {
        if (target != null && target !== transition.target && (instant || viewportMoved || (transition.target == null && !animateEntrance))) {
            transition.show(target, targetBase, targetViewport)
        }
    }
    LaunchedEffect(target) {
        if (target != null) transition.animateTo(target, targetBase, targetViewport, animationSpec, animateEntrance)
    }

    // The finger is gesture state; focus is derived from it and the scene compiled above, never
    // stored, so it cannot outlive the data it came from.
    var pointer by remember { mutableStateOf<XY?>(null) }
    var touchPointer by remember { mutableStateOf(false) }
    val reach = with(density) { FocusReach.toPx() }
    val focus by remember(fresh, interactive, reach) {
        // Structural equality: a pointer move within the same focus recomposes nothing.
        derivedStateOf(structuralEqualityPolicy()) { if (interactive) pointer?.let { fresh?.focusAt(it, reach) } else null }
    }
    val shown = focus

    // Report after every composition whose focus differs from the last one reported.
    val focusChanged by rememberUpdatedState(onFocusChange)
    SideEffect {
        if (shown != reported.last) {
            reported.last = shown
            onFocusChange?.invoke(shown)
        }
    }
    DisposableEffect(Unit) {
        onDispose { if (reported.last != null) { reported.last = null; focusChanged?.invoke(null) } }
    }

    val haptics by rememberUpdatedState(LocalHapticFeedback.current)
    // Haptic steps are judged on the scene focus comes from; a tap on the scene on screen.
    val latest by rememberUpdatedState(fresh)
    val newest by rememberUpdatedState(target)
    val tapped by rememberUpdatedState(onTap)
    val gestureScope = rememberCoroutineScope()
    val decay = rememberSplineBasedDecay<Float>()

    val description = trace("Chart:description") { spec.contentDescription() }
    Box(
        modifier
            .onSizeChanged { size = it }
            .semantics {
                contentDescription = description
                if (viewport != null) {
                    horizontalScrollAxisRange = ScrollAxisRange(
                        value = { (viewport.start - viewport.min).coerceAtLeast(0.0).toFloat() },
                        maxValue = { (viewport.bounds.totalSpan - viewport.span).coerceAtLeast(0.0).toFloat() },
                    )
                    if (interactive) {
                        scrollBy { x, _ ->
                            val plotWidth = (newest?.plot?.width ?: size.width.toFloat()).coerceAtLeast(1f)
                            viewport.panBy((x.toDouble() / plotWidth) * viewport.span) != 0.0
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
            }
            .then(
                if (!interactive) Modifier
                else if (viewport != null) Modifier
                    .pointerInput(viewport) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.type != PointerEventType.Scroll) continue
                                val change = event.changes.firstOrNull() ?: continue
                                val delta = change.scrollDelta
                                if (delta == Offset.Zero) continue
                                val plot = newest?.plot
                                val plotLeft = plot?.left ?: 0f
                                val plotWidth = (plot?.width ?: size.width.toFloat()).coerceAtLeast(1f)
                                if (delta.x != 0f && !event.keyboardModifiers.isCtrlPressed && !event.keyboardModifiers.isMetaPressed) {
                                    viewport.panBy((delta.x.toDouble() * 40.0 / plotWidth) * viewport.span)
                                    change.consume()
                                } else if (delta.y != 0f) {
                                    val frac = ((change.position.x - plotLeft) / plotWidth).toDouble().coerceIn(0.0, 1.0)
                                    val anchor = viewport.start + frac * viewport.span
                                    viewport.zoomBy(2.0.pow(-delta.y.toDouble() * 0.25), anchor)
                                    change.consume()
                                }
                            }
                        }
                    }
                    .pointerInput(viewport, onTap != null) {
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

                                var lastScene: ChartScene? = null
                                var lastStep: Any? = null
                                fun moveTo(p: XY, touch: Boolean) {
                                    val scene = latest
                                    val before = if (scene === lastScene) lastStep else pointer?.let { scene?.focusAt(it, reachPx) }?.step()
                                    val after = scene?.focusAt(p, reachPx)?.step()
                                    lastScene = scene
                                    lastStep = after
                                    touchPointer = touch
                                    pointer = p
                                    if (after != null && after != before) haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                                }

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
                                            applyTwoFingerChange(event, viewport, newest, size)
                                            break
                                        }
                                        val change = event.changes.firstOrNull { it.id == activeId }
                                        if (change == null || !change.pressed) {
                                            if (change != null && !change.isConsumed && onTap != null) {
                                                change.consume()
                                                tapped?.invoke(newest?.focusAt(XY(change.position.x, change.position.y), reachPx))
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
                                        if (change.uptimeMillis - down.uptimeMillis >= longPressMs && prevDist <= slop) {
                                            mode = ViewportGesture.Scrubbing
                                            moveTo(XY(change.previousPosition.x, change.previousPosition.y), change.type == PointerType.Touch)
                                            change.consume()
                                            break
                                        }
                                        // Vertical intent belongs to the scrolling parent; horizontal pans.
                                        if (abs(d.y) > slop && abs(d.y) > abs(d.x)) {
                                            mode = ViewportGesture.Finished
                                            break
                                        }
                                        if (abs(d.x) > slop) {
                                            mode = ViewportGesture.Panning
                                            velocityTracker.addPointerInputChange(change)
                                            change.consume()
                                            initialPanDx = d.x - sign(d.x) * slop
                                            break
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
                                        moveTo(XY(currentPos.x, currentPos.y), currentType == PointerType.Touch)
                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val change = event.changes.firstOrNull { it.id == activeId } ?: break
                                            if (!change.pressed) break
                                            change.consume()
                                            moveTo(XY(change.position.x, change.position.y), change.type == PointerType.Touch)
                                        }
                                    }
                                    ViewportGesture.Panning -> {
                                        if (initialPanDx != 0f) {
                                            val plotWidth = (newest?.plot?.width ?: size.width.toFloat()).coerceAtLeast(1f)
                                            viewport.panBy(-(initialPanDx.toDouble() / plotWidth) * viewport.span)
                                        }
                                        var shouldFling = true
                                        while (true) {
                                            val event = awaitPointerEvent()
                                            if (event.changes.count { it.pressed } >= 2) {
                                                shouldFling = false
                                                applyTwoFingerChange(event, viewport, newest, size)
                                                runPinchLoop(viewport, { newest }, { size })
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
                                                val plotWidth = (newest?.plot?.width ?: size.width.toFloat()).coerceAtLeast(1f)
                                                viewport.panBy(-(dx.toDouble() / plotWidth) * viewport.span)
                                            }
                                            change.consume()
                                        }
                                        if (shouldFling) {
                                            val maxVel = viewConfiguration.maximumFlingVelocity
                                            val vx = velocityTracker.calculateVelocity(Velocity(maxVel, maxVel)).x
                                            if (abs(vx) > viewConfiguration.minimumFlingVelocity) {
                                                viewport.launchFling(gestureScope) { isActiveFling ->
                                                    var lastValue = 0f
                                                    AnimationState(initialValue = 0f, initialVelocity = vx).animateDecay(decay) {
                                                        if (!isActiveFling()) {
                                                            cancelAnimation()
                                                            return@animateDecay
                                                        }
                                                        val frameDx = value - lastValue
                                                        lastValue = value
                                                        if (frameDx == 0f) return@animateDecay
                                                        val plotWidth = (newest?.plot?.width ?: size.width.toFloat()).coerceAtLeast(1f)
                                                        val deltaDomain = -(frameDx.toDouble() / plotWidth) * span
                                                        val moved = panStep(deltaDomain)
                                                        if (abs(moved) < abs(deltaDomain) * 0.5 && (!canPanBack || !canPanForward)) {
                                                            cancelAnimation()
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    ViewportGesture.Pinching -> {
                                        runPinchLoop(viewport, { newest }, { size })
                                    }
                                }
                            } finally {
                                pointer = null
                            }
                        }
                    }
                else if (onTap != null) Modifier.pointerInput(TapMode) {
                    val reachPx = FocusReach.toPx()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            if (change.isConsumed) break // a parent took the gesture: a scroll
                            if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) break // a drag
                            if (!change.pressed) {
                                change.consume()
                                tapped?.invoke(newest?.focusAt(XY(change.position.x, change.position.y), reachPx))
                                break
                            }
                        }
                    }
                }
                else Modifier.pointerInput(Unit) {
                    val reachPx = FocusReach.toPx()
                    awaitEachGesture {
                        try {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            var lastScene: ChartScene? = null
                            var lastStep: Any? = null
                            fun moveTo(p: XY, touch: Boolean) {
                                // One tick per step of the needle, judged on the newest scene for
                                // both positions, so data moving under a still finger never ticks.
                                // The previous step is reused unless the scene has changed since.
                                val scene = latest
                                val before = if (scene === lastScene) lastStep else pointer?.let { scene?.focusAt(it, reachPx) }?.step()
                                val after = scene?.focusAt(p, reachPx)?.step()
                                lastScene = scene
                                lastStep = after
                                touchPointer = touch
                                pointer = p
                                if (after != null && after != before) haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                            }
                            moveTo(XY(down.position.x, down.position.y), down.type == PointerType.Touch)
                            var scrubbing = false
                            while (true) {
                                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break // lifted, or cancelled
                                if (!scrubbing) {
                                    if (change.isConsumed) break // a parent took the gesture
                                    val d = change.position - down.position
                                    // Vertical intent belongs to the scrolling parent; horizontal is ours.
                                    if (abs(d.y) > viewConfiguration.touchSlop && abs(d.y) > abs(d.x)) break
                                    if (abs(d.x) > viewConfiguration.touchSlop) scrubbing = true
                                }
                                if (scrubbing) change.consume()
                                moveTo(XY(change.position.x, change.position.y), change.type == PointerType.Touch)
                            }
                        } finally {
                            // Also runs when the modifier goes away (interactive = false, disposal).
                            pointer = null
                        }
                    }
                },
            )
            .drawBehind { transition.current()?.let { drawScene(it, style, shown, texts) } },
    ) {
        if (inputs != null && target != null && overlay != null) overlay(target)
        if (shown != null && fresh != null && tooltip != null) {
            PlaceTooltip(
                shown, fresh, tooltipPlacement, gap = 12.dp,
                fingerClearance = style.tooltip.fingerClearance,
                pointerY = { if (touchPointer) pointer?.y ?: Float.NaN else Float.NaN },
            ) { tooltip(shown, fresh) }
        }
    }
}

/**
 * A system trace section around [block], for Perfetto and macrobenchmarks: `Chart:compile` and
 * `Chart:description`. With tracing off it costs a cached flag check.
 */
private inline fun <T> trace(name: String, block: () -> T): T {
    android.os.Trace.beginSection(name)
    try {
        return block()
    } finally {
        android.os.Trace.endSection()
    }
}

/**
 * Where background compiles run. Internal so tests can hold or step them; [Dispatchers.Default]
 * everywhere else.
 */
internal val LocalSceneCompileContext = staticCompositionLocalOf<CoroutineContext> { Dispatchers.Default }

/** The spec, size, metrics and text cache a scene's geometry depends on, apart from any viewport. */
private data class BaseInputs(val spec: ChartSpec, val size: Extent, val metrics: ChartMetrics, val texts: TextCache)

/** What a scene's geometry depends on: the revision it is compiled from. */
private data class SceneInputs(val base: BaseInputs, val viewport: ViewportRange?) {
    fun compileHere(): ChartScene = trace("Chart:compile") {
        compile(base.spec, base.size, base.texts, base.metrics, viewport)
    }

    /**
     * Compiles off the main thread with the text sizes already laid out. A compile that met text not
     * yet laid out is thrown away, the text is laid out here, on the main thread, and it compiles
     * again; usually once, since labels repeat from one revision to the next. Still short after
     * [MeasureRounds], the last round compiles here.
     */
    suspend fun compileOffMain(context: CoroutineContext): ChartScene {
        val known = base.texts.known()
        repeat(MeasureRounds) {
            val scene = withContext(context) {
                trace("Chart:compile") { compile(base.spec, base.size, known, base.metrics, viewport) }
            }
            if (known.missing.isEmpty()) return scene
            known.measureMissing(base.texts)
        }
        return compileHere()
    }
}

private const val MeasureRounds = 3

/** A scene and the revision it was compiled from. */
private class Compiled(val inputs: SceneInputs, val scene: ChartScene)

/** The newest compiled scene, whichever way it was compiled. */
@Stable
private class SceneStore {
    var newest by mutableStateOf<Compiled?>(null)

    /** A first scene has been compiled, so later ones may compile off the main thread. Plain. */
    var started = false
}

/** How far a nearest-point focus reaches from the finger. */
private val FocusReach = 40.dp

/** Keys the tap-to-select gesture apart from press-to-focus, so switching modes restarts input. */
private object TapMode

private enum class ViewportGesture { Undecided, Panning, Pinching, Scrubbing, Finished }

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
        var prevCx = 0f
        var prevCy = 0f
        var currCx = 0f
        var currCy = 0f
        ongoing.forEach {
            prevCx += it.previousPosition.x
            prevCy += it.previousPosition.y
            currCx += it.position.x
            currCy += it.position.y
        }
        val n = ongoing.size.toFloat()
        val prevCentroid = Offset(prevCx / n, prevCy / n)
        val currCentroid = Offset(currCx / n, currCy / n)
        var prevSpread = 0.0
        var currSpread = 0.0
        ongoing.forEach {
            prevSpread += (it.previousPosition - prevCentroid).getDistance()
            currSpread += (it.position - currCentroid).getDistance()
        }
        val plot = scene?.plot
        val plotLeft = plot?.left ?: 0f
        val plotWidth = (plot?.width ?: size.width.toFloat()).coerceAtLeast(1f)
        val prevFraction = ((prevCentroid.x - plotLeft) / plotWidth).toDouble().coerceIn(0.0, 1.0)
        val anchor = viewport.start + prevFraction * viewport.span
        if (prevSpread > 0.5 && currSpread > 0.5) {
            val zoom = currSpread / prevSpread
            if (zoom.isFinite() && zoom > 0.0 && zoom != 1.0) {
                viewport.zoomBy(zoom, anchor)
            }
        }
        val panPx = currCentroid.x - prevCentroid.x
        if (panPx != 0f) {
            viewport.panBy(-(panPx.toDouble() / plotWidth) * viewport.span)
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

/**
 * The haptic step: a category (a column, or a row of horizontal bars) when scrubbing, a single
 * datum otherwise, by its id when it has one (each treemap leaf is its own step).
 */
private fun Focus.step(): Any =
    if (mode == FocusMode.NearestX || mode == FocusMode.NearestY) primary.datum else primary.id ?: (primary.series to primary.datum)

/** The last focus handed to `onFocusChange`. Plain, not state: writing it must not recompose. */
private class FocusReport {
    var last: Focus? = null
}

/** Holds the scene being animated from and to. Progress is read only while drawing. */
@Stable
private class SceneTransition {
    private var from by mutableStateOf<ChartScene?>(null)
    var target by mutableStateOf<ChartScene?>(null)
        private set
    var base: BaseInputs? = null
        private set
    var viewport: ViewportRange? = null
        private set
    private val progress = Animatable(1f)

    fun current(): ChartScene? {
        val to = target ?: return null
        val start = from ?: return to
        return SceneMotion.lerp(start, to, progress.value.coerceIn(0f, 1f))
    }

    /** Shows [next] as it is, with no tween. Not suspending, so it can land before a frame draws. */
    fun show(next: ChartScene, nextBase: BaseInputs? = base, nextViewport: ViewportRange? = viewport) {
        from = null
        target = next
        base = nextBase
        viewport = nextViewport
    }

    /**
     * Retargets from wherever the chart is now, so an update mid-flight never jumps. The first
     * scene grows in from the baseline when [enter] is true. Instant specs, and animations turned
     * off on the device, land on [next] at once.
     */
    suspend fun animateTo(
        next: ChartScene,
        nextBase: BaseInputs?,
        nextViewport: ViewportRange?,
        spec: FiniteAnimationSpec<Float>,
        enter: Boolean,
    ) {
        if (next === target) {
            base = nextBase
            viewport = nextViewport
            return // already shown
        }
        val first = target == null
        base = nextBase
        viewport = nextViewport
        val reduced = coroutineContext[MotionDurationScale]?.scaleFactor == 0f
        if ((first && !enter) || spec.isInstant() || reduced) {
            progress.snapTo(1f) // also stops a tween in flight
            show(next, nextBase, nextViewport)
            return
        }
        from = if (first) SceneMotion.reveal(next) else current()
        target = next
        progress.snapTo(0f)
        progress.animateTo(1f, spec)
    }
}
