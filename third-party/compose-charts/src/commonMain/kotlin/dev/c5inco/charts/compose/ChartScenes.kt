package dev.c5inco.charts.compose

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.unit.IntSize
import dev.c5inco.charts.core.ChartMetrics
import dev.c5inco.charts.core.ChartScene
import dev.c5inco.charts.core.ChartSpec
import dev.c5inco.charts.core.Extent
import dev.c5inco.charts.core.SceneMotion
import dev.c5inco.charts.core.SceneTween
import dev.c5inco.charts.core.ViewportRange
import dev.c5inco.charts.core.compile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

/**
 * A system trace section around [block], for Perfetto and macrobenchmarks: `Chart:compile` and
 * `Chart:description`. With tracing off it costs a cached flag check; on desktop, nothing.
 */
internal inline fun <T> trace(name: String, block: () -> T): T {
    beginTraceSection(name)
    try {
        return block()
    } finally {
        endTraceSection()
    }
}

/**
 * Where background compiles run. Internal so tests can hold or step them; [Dispatchers.Default]
 * everywhere else.
 */
internal val LocalSceneCompileContext = staticCompositionLocalOf<CoroutineContext> { Dispatchers.Default }

/**
 * The spec, size, metrics and text cache a scene's geometry depends on, apart from any viewport, and
 * the cache's [fonts] revision ([TextCache.fontsRevision]): a font arriving after text was laid out
 * in its fallback is a new revision, so label sizes and the gutters made of them are measured again.
 */
internal data class BaseInputs(
    val spec: ChartSpec,
    val size: Extent,
    val metrics: ChartMetrics,
    val texts: TextCache,
    val fonts: Int,
) {
    companion object {
        /** Null until the chart has been measured: there is nothing to compile for. */
        fun of(spec: ChartSpec, size: IntSize, metrics: ChartMetrics, texts: TextCache, fonts: Int): BaseInputs? =
            if (size.width == 0 || size.height == 0) null
            else BaseInputs(spec, Extent(size.width.toFloat(), size.height.toFloat()), metrics, texts, fonts)
    }
}

/** What a scene's geometry depends on: the revision it is compiled from. */
internal data class SceneInputs(val base: BaseInputs, val viewport: ViewportRange?) {
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
internal class Compiled(val inputs: SceneInputs, val scene: ChartScene)

/**
 * The scenes one composition works with, given its inputs and the newest compiled scene.
 *
 * [target] is what is drawn and tweened toward: the newest scene, even while a newer revision
 * compiles. [fresh] is what focus, the tooltip and `onFocusChange` may use: only the scene of this
 * composition's inputs, so null while that scene compiles.
 */
internal class SceneFrame(private val compiled: Compiled?, val fresh: ChartScene?) {
    val target: ChartScene? get() = compiled?.scene
    val targetBase: BaseInputs? get() = compiled?.inputs?.base
    val targetViewport: ViewportRange? get() = compiled?.inputs?.viewport
}

/**
 * The newest compiled scene, whichever way it was compiled, and where the next one compiles.
 *
 * [newest] is state, read in composition: a background compile landing recomposes the chart.
 * [started] is plain.
 */
@Stable
internal class SceneStore {
    var newest by mutableStateOf<Compiled?>(null)
        private set

    /** A first scene has been compiled, so later ones may compile off the main thread. Plain. */
    var started = false
        private set

    /**
     * Whether [inputs] compile in place, in the composition that receives them, rather than off the
     * main thread. In place: the first scene, so the first frame has it; every scene when asked to;
     * a viewport pan/zoom on the current spec/size/style so the chart never lags behind the finger;
     * and, by default, a scene arriving while a focus is shown ([focusShown]), so the tooltip and
     * onFocusChange never pause.
     */
    fun compilesInPlace(inputs: SceneInputs, compile: SceneCompile, focusShown: Boolean): Boolean {
        // Unobserved: this decides only for the inputs at hand (Chart makes it once per revision, in
        // a remember), so a later [newest] cannot change it. Observed, the read would subscribe the
        // composition to the very write its own [adopt] makes after it applies, and compose the chart
        // a second time for nothing on every in-place compile (every frame of a viewport pan). A
        // background compile landing still recomposes: [frame] reads [newest] whenever nothing
        // compiled in place, which is the only time one can be in flight.
        val newest = Snapshot.withoutReadObservation { newest }
        val viewportOnly = newest?.let {
            it.inputs.base == inputs.base && it.inputs.viewport != inputs.viewport
        } == true
        return viewportOnly || when (compile) {
            SceneCompile.Background -> !started || focusShown
            SceneCompile.BackgroundEvenWhileFocused -> !started
            SceneCompile.InPlace -> true
        }
    }

    /**
     * The scene for [inputs] compiled here and now when [compilesInPlace], else null: there is
     * nothing to compile yet, or it compiles off the main thread ([compileInBackground]).
     */
    fun compileInPlace(inputs: SceneInputs?, compile: SceneCompile, focusShown: Boolean): Compiled? =
        if (inputs != null && compilesInPlace(inputs, compile, focusShown)) Compiled(inputs, inputs.compileHere()) else null

    /** Reads [newest]: what the composition with [inputs] draws, and may focus on. */
    fun frame(inputs: SceneInputs?, inPlace: Compiled?): SceneFrame {
        // The newest scene, in place or landed from a background compile, and the revision it is for.
        val compiled = inPlace ?: newest
        return SceneFrame(compiled, fresh = compiled?.takeIf { inputs != null && it.inputs == inputs }?.scene)
    }

    /** After the composition that compiled [inPlace] applies: it is the newest scene now. */
    fun adopt(inPlace: Compiled?) {
        if (inPlace != null) {
            started = true
            // Not observed by the composition that compiled it (see compilesInPlace and frame), so
            // this write does not compose the chart again.
            if (newest !== inPlace) newest = inPlace
        }
    }

    /**
     * Compiles [inputs] off the main thread, on [context], and lands the scene as [newest]. Run it
     * from an effect keyed on [inputs]: restarted by a newer revision, and cancelled by it or by
     * leaving composition before it can land (withContext drops the result of a cancelled compile),
     * so only the latest scene lands.
     */
    suspend fun compileInBackground(inputs: SceneInputs, context: CoroutineContext) {
        if (newest?.inputs == inputs) return // back to the revision it has
        newest = Compiled(inputs, inputs.compileOffMain(context))
    }
}

/** Holds the scene being animated from and to. Progress is read only while drawing. */
@Stable
internal class SceneTransition {
    private var from by mutableStateOf<ChartScene?>(null)
    var target by mutableStateOf<ChartScene?>(null)
        private set
    var base: BaseInputs? = null
        private set
    var viewport: ViewportRange? = null
        private set
    private val progress = Animatable(1f)

    // The transition in flight, paired once (a SceneTween, made on the first frame that draws it),
    // and the last scene it gave: the marks and the focus layer both draw each frame, and the second
    // asks for the same progress. Plain: main thread only (drawing, and a retarget reading where the
    // chart is). Matched to the scenes in flight by identity, so a retarget (which starts from a new,
    // interpolated scene) or a new target pairs afresh, and dropped once nothing tweens, so it holds
    // on to no scene the tween is done with.
    private var tween: SceneTween? = null
    private var tweenT = Float.NaN
    private var tweened: ChartScene? = null

    /** For tests: the scene the transition started from (null when none is in flight) and its progress. */
    internal val origin: ChartScene? get() = from
    internal val fraction: Float get() = progress.value.coerceIn(0f, 1f)

    fun current(): ChartScene? {
        val to = target ?: return null
        val start = from
        val t = progress.value.coerceIn(0f, 1f)
        if (start == null || t >= 1f) {
            // Not tweening, or landed: SceneMotion.lerp's own answer, and nothing to keep.
            if (tween != null) {
                tween = null
                tweened = null
                tweenT = Float.NaN
            }
            return to
        }
        val paired = tween?.takeIf { it.from === start && it.to === to } ?: SceneTween(start, to).also {
            tween = it
            tweened = null
            tweenT = Float.NaN
        }
        tweened?.let { if (t == tweenT) return it }
        return paired.at(t).also {
            tweenT = t
            tweened = it
        }
    }

    /**
     * Whether a scene for [nextBase] and [nextViewport] only moves the viewport window (including
     * attaching or removing a viewport) on the spec, size and style already shown. Plain reads, made
     * in composition for the [showUntweened] that follows it.
     */
    fun movesViewportOnly(nextBase: BaseInputs?, nextViewport: ViewportRange?): Boolean =
        nextBase != null && base == nextBase && viewport != nextViewport

    /**
     * Shows [frame]'s target at once if it does not tween: an [instant] spec, the entrance disabled
     * on the first scene, or a viewport window change ([viewportMoved], from [movesViewportOnly]).
     * Called while the composition applies, so the scene is drawn in the same frame; [animateTo]
     * then finds it already in place.
     */
    fun showUntweened(frame: SceneFrame, instant: Boolean, viewportMoved: Boolean, animateEntrance: Boolean) {
        val next = frame.target
        if (next != null && next !== target && (instant || viewportMoved || (target == null && !animateEntrance))) {
            show(next, frame.targetBase, frame.targetViewport)
        } else if (next != null && next !== target && frame.targetBase?.fonts != base?.fonts && next == target) {
            // A font arrived and the scene compiled again to the one already shown (its labels'
            // sizes moved nothing): nothing to animate, and LaunchedEffect(target), keyed on an
            // equal scene, does not run [animateTo]. It is the new revision's scene now, so a
            // viewport pan after it moves the viewport only. Scenes are compared only on a font
            // revision, which is rare: never on a pan's or a data stream's frames.
            base = frame.targetBase
            viewport = frame.targetViewport
        }
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
