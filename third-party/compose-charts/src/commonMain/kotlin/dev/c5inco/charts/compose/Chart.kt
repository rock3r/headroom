package dev.c5inco.charts.compose

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.SnapSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import dev.c5inco.charts.core.CartesianChart
import dev.c5inco.charts.core.ChartScene
import dev.c5inco.charts.core.ChartSpec
import dev.c5inco.charts.core.Focus
import dev.c5inco.charts.core.NumericAxis
import dev.c5inco.charts.core.TimeAxis

/** Motion tokens. Literal specs live here and nowhere else. */
public object ChartMotion {
    /**
     * Marks are data: their position is read as value, so they settle without bounce. Matches
     * Material's standard slow spatial spring.
     */
    public val Reveal: FiniteAnimationSpec<Float> = spring(dampingRatio = 0.9f, stiffness = 300f)

    /**
     * Critically damped spring for [ChartViewport.animateTo]: settles without ever overshooting
     * the requested domain window.
     */
    public val Viewport: FiniteAnimationSpec<Float> = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 400f)

    /**
     * No tween: each new scene is drawn as it is, from the frame it is compiled. For small charts
     * that update often and are read at a glance (sparklines in a scrolling list), where a tween
     * per update costs frames and says nothing. With the default `animateEntrance`, the first
     * scene also appears at once: there is nothing to grow in over.
     */
    public val Instant: FiniteAnimationSpec<Float> = snap()
}

/**
 * Where a [Chart] compiles its scenes after the first. Whatever the policy, a `Chart` instance's
 * first scene compiles in place, in the composition that first has a size, so its first frame (and
 * any screenshot) has it.
 */
public enum class SceneCompile {
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
 * @param style how the chart looks: [ChartStyle.clean] unless given.
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
 *   starts on the chart is just a scroll. Resolve the tap by [dev.c5inco.charts.core.FocusDetail.Tile.id]
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
public fun Chart(
    spec: ChartSpec,
    modifier: Modifier = Modifier,
    style: ChartStyle = DefaultStyle,
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
    val host = remember { ChartHost() }
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    // Only the style's geometry is part of a scene's revision; the rest is paint, read while drawing.
    val textStyles = remember(style) { style.textStyles() }
    val texts = remember(measurer, textStyles) { TextCache(measurer, textStyles) }
    val painter = rememberScenePainter()
    val metrics = remember(style, density) { style.metrics(density) }
    val size = host.size
    val viewportRange = viewport?.range
    // Text laid out while its font was still loading is laid out again when the font arrives, and
    // the scene with it: this read subscribes the composition to the loading fonts.
    val fonts = texts.fontsRevision()
    val baseInputs = remember(spec, size, metrics, texts, fonts) { BaseInputs.of(spec, size, metrics, texts, fonts) }
    val inputs = remember(baseInputs, viewportRange) {
        baseInputs?.let { SceneInputs(it, viewportRange) }
    }

    // Scenes: compiled here when they must be (see SceneStore.compilesInPlace), else off the main
    // thread, the newest revision winning.
    val scenes = host.scenes
    val focus = host.focus
    val inPlace = remember(inputs, compile) {
        scenes.compileInPlace(inputs, compile, focusShown = focus.reported != null)
    }
    val frame = scenes.frame(inputs, inPlace)
    SideEffect { scenes.adopt(inPlace) }
    if (inputs != null && inPlace == null) {
        val context = LocalSceneCompileContext.current
        LaunchedEffect(inputs) { scenes.compileInBackground(inputs, context) }
    }
    val target = frame.target

    // A scene that does not tween (instant spec, entrance disabled on first scene, or a viewport
    // window change — including attaching or removing a viewport — on the same spec/size/style) is
    // shown while this composition applies, so it is drawn in the same frame; the effect below then
    // finds it already in place.
    val transition = host.transition
    val instant = animationSpec.isInstant()
    val viewportMoved = transition.movesViewportOnly(frame.targetBase, frame.targetViewport)
    SideEffect { transition.showUntweened(frame, instant, viewportMoved, animateEntrance) }
    LaunchedEffect(target) {
        if (target != null) transition.animateTo(target, frame.targetBase, frame.targetViewport, animationSpec, animateEntrance)
    }

    // The finger is gesture state; focus is derived from it and the scene compiled above, never
    // stored, so it cannot outlive the data it came from. It is read only where it is shown (the
    // report below, the tooltip and the focus layer's drawing), never in this body, so a new focus
    // recomposes the report and the tooltip and redraws the focus layer, not the chart.
    val fresh = frame.fresh
    val reach = with(density) { FocusReach.toPx() }
    val focused = remember(fresh, interactive, reach) { focus.focusOn(fresh, interactive, reach) }

    // Report after every composition whose focus differs from the last one reported.
    val focusChanged by rememberUpdatedState(onFocusChange)
    ReportFocus(focus, focused, onFocusChange)
    DisposableEffect(Unit) {
        onDispose { focus.dispose(focusChanged) }
    }

    val gestures = rememberChartGestures(host, fresh = fresh, newest = target, onTap = onTap)
    val description = host.description(spec)
    Box(
        modifier
            .onSizeChanged { host.size = it }
            .semantics {
                contentDescription = description
                if (viewport != null) viewportSemantics(viewport, interactive) { gestures.plotWidth() }
            }
            .then(
                if (!interactive) Modifier
                else if (viewport != null) Modifier
                    .pointerInput(viewport) { with(gestures) { viewportWheel(viewport) } }
                    .pointerInput(viewport, onTap != null) { with(gestures) { viewportGestures(viewport, tapToSelect = onTap != null) } }
                else if (onTap != null) Modifier.pointerInput(TapMode) { with(gestures) { tapToSelect() } }
                else Modifier.pointerInput(Unit) { with(gestures) { pressToFocus() } },
            )
            // The scene, reading the focus only when some mark's paint depends on it, in a layer of
            // its own: its own recording boundary, so the marks are drawn again only when something
            // they read changes, never because something else on screen (a sibling, an ancestor)
            // draws. No clip: marks may overhang the chart.
            .graphicsLayer()
            .drawBehind { transition.current()?.let { with(painter) { paint(it, style, { focused.value }, texts) } } }
            // The needle and focus markers, over the scene, in a layer of their own (with the
            // overlay and tooltip): a new focus redraws them without recording the marks again.
            .graphicsLayer()
            .drawBehind {
                val shown = focused.value ?: return@drawBehind painter.focusCleared()
                transition.current()?.let {
                    painter.focusShownOver(it)
                    drawFocus(it, style, shown)
                }
            },
    ) {
        if (inputs != null && target != null && overlay != null) overlay(target)
        if (fresh != null && tooltip != null) {
            FocusTooltip(focused, fresh, tooltipPlacement, style, pointerY = { focus.touchY() }, tooltip)
        }
    }
}

/**
 * Reports the focus [focused] holds to [onFocusChange] after every composition whose focus differs
 * from the last one reported. Its own scope: the focus is read here, not in [Chart]'s body.
 */
@Composable
private fun ReportFocus(focus: ChartFocus, focused: State<Focus?>, onFocusChange: ((Focus?) -> Unit)?) {
    val shown = focused.value
    SideEffect { focus.report(shown, onFocusChange) }
}

/** Places [tooltip] for the focus [focused] holds, when there is one. Its own scope, as [ReportFocus]. */
@Composable
private fun FocusTooltip(
    focused: State<Focus?>,
    fresh: ChartScene,
    placement: TooltipPlacement,
    style: ChartStyle,
    pointerY: () -> Float,
    tooltip: @Composable (Focus, ChartScene) -> Unit,
) {
    val shown = focused.value ?: return
    PlaceTooltip(
        shown, fresh, placement, gap = 12.dp,
        fingerClearance = style.tooltip.fingerClearance,
        pointerY = pointerY,
    ) { tooltip(shown, fresh) }
}

/**
 * [Chart]'s default style, [ChartStyle.clean], built once: a style built in the call would be a new
 * instance in every composition, compared field by field against the last one by each `remember`
 * keyed on it.
 */
internal val DefaultStyle: ChartStyle = ChartStyle.clean()

/** Keys the tap-to-select gesture apart from press-to-focus, so switching modes restarts input. */
private object TapMode
