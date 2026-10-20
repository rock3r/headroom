package dev.sebastiano.headroom.designsystem

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.preferredFrameRate
import kotlinx.coroutines.delay

/**
 * Motion roles, following Material 3 Expressive and the rule that values read as data never
 * overshoot: bars, rings and numbers use the slow effects spring, which is critically damped.
 * Containers (cards, panes, the toolbar) use the expressive spatial springs.
 */
object HeadroomMotion {
    /** For values read as data: bar ends, ring arcs, numbers. Never overshoots. */
    @Composable
    @ReadOnlyComposable
    fun <T> dataSpec(): FiniteAnimationSpec<T> = MaterialTheme.motionScheme.slowEffectsSpec()

    /** For containers: cards, panes, the floating toolbar. May bounce a little. */
    @Composable
    @ReadOnlyComposable
    fun <T> containerSpec(): FiniteAnimationSpec<T> =
        MaterialTheme.motionScheme.defaultSpatialSpec()

    /**
     * The one hero moment: a weekly reset drains the bar. Slower than [dataSpec] so it reads as an
     * event, and critically damped so it never passes the new value.
     */
    fun <T> resetDrainSpec(): FiniteAnimationSpec<T> =
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = RESET_DRAIN_STIFFNESS)

    private const val RESET_DRAIN_STIFFNESS = 110f

    /**
     * For motion a gesture drives, such as the predictive back gesture scrubbing a transition. It
     * is linear, so the motion stays under the finger: a spring would run most of its way in the
     * first part of the gesture. When the finger lifts, the rest plays out over the same duration.
     */
    fun <T> scrubSpec(): FiniteAnimationSpec<T> =
        tween(durationMillis = SCRUB_DURATION_MILLIS, easing = LinearEasing)

    private const val SCRUB_DURATION_MILLIS = 350

    /**
     * For a reveal the eye should follow: a circle growing over the screen. A spring puts most of
     * the growth in the first frames, so it reads as a cut; a timed curve with a soft start does
     * not. [delayMillis] lets content follow the reveal a moment later.
     */
    fun <T> revealSpec(delayMillis: Int = 0): FiniteAnimationSpec<T> =
        tween(
            durationMillis = REVEAL_DURATION_MILLIS,
            delayMillis = delayMillis,
            easing = REVEAL_EASING,
        )

    /**
     * For the circle that opens Settings. It uses [revealSpec]'s curve, shorter: the page waits for
     * the reveal before it scrolls, so a long one gets in the way. [durationMillis] lets content
     * that starts later end with the circle.
     */
    fun <T> pageOpenSpec(
        delayMillis: Int = 0,
        durationMillis: Int = PAGE_OPEN_DURATION_MILLIS - delayMillis,
    ): FiniteAnimationSpec<T> =
        tween(durationMillis = durationMillis, delayMillis = delayMillis, easing = REVEAL_EASING)

    /** For the circle that closes Settings: a little quicker than [pageOpenSpec]. */
    fun <T> pageCloseSpec(): FiniteAnimationSpec<T> =
        tween(durationMillis = PAGE_CLOSE_DURATION_MILLIS, easing = REVEAL_EASING)

    private const val REVEAL_DURATION_MILLIS = 520
    private const val PAGE_OPEN_DURATION_MILLIS = 450
    private const val PAGE_CLOSE_DURATION_MILLIS = 400
    private val REVEAL_EASING = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

    /**
     * For a liquid ripple spreading from a tap. It is linear because a ring on water spreads at a
     * steady speed; the ripple's shader fades the ring out as it grows, which gives it its ease.
     */
    fun <T> rippleSpec(): FiniteAnimationSpec<T> =
        tween(durationMillis = RIPPLE_DURATION_MILLIS, easing = LinearEasing)

    private const val RIPPLE_DURATION_MILLIS = 800

    /**
     * How long a slow decorative motion that never stops takes to loop, such as the next reset
     * card's shape turning: see [rememberDecorativePhase]. Slow enough to notice only when you look
     * for it.
     */
    const val DRIFT_PERIOD_MILLIS: Int = 60_000

    /**
     * The frame rate that endless decorative motion asks for: the next reset card's turning shape
     * and the waves of a bar or ring that needs attention. They move so slowly that more frames
     * look no smoother, yet each frame redraws the whole window, so at the display's 120 Hz a
     * screen at rest would keep the CPU and GPU busy. See [decorativeFrameRate].
     */
    const val DECORATIVE_FRAME_RATE: Float = 30f

    /** For fades and colour changes. */
    @Composable
    @ReadOnlyComposable
    fun <T> effectsSpec(): FiniteAnimationSpec<T> = MaterialTheme.motionScheme.defaultEffectsSpec()
}

/**
 * True when the user chose to reduce motion in the app's settings. [HeadroomTheme] provides it;
 * read it through [animationsEnabled], which also covers the device's own setting.
 */
// The one app-wide motion setting reaches every screen through the theme, like the motion scheme
// does, so each animated component does not need a parameter for it.
@Suppress("CompositionLocalAllowlist")
internal val LocalReduceMotion = staticCompositionLocalOf { false }

/**
 * False when motion should be reduced: the user has turned animations off on the device (animator
 * duration scale 0), or chose to reduce motion in the app. Compose animations end at once by
 * themselves when the device has animations off; this flag is for motion that Compose does not
 * time, such as the moving wave of a wavy progress indicator, which then falls back to a flat
 * indicator of the same length, and for choosing a short crossfade over spatial motion. The device
 * value comes from the [MotionDurationScale] that the platform (or a test) installs in the
 * composition's effect context.
 */
@Composable
fun animationsEnabled(): Boolean {
    val scale = rememberCoroutineScope().coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f
    return scale > 0f && !LocalReduceMotion.current
}

/**
 * Asks the display for [HeadroomMotion.DECORATIVE_FRAME_RATE] while only this content moves, for
 * endless decorative motion. The request is a vote: anything else that moves, a scroll or a
 * transition, still gets the display's full rate. It needs a layer to vote with, so it adds one.
 *
 * Compose 1.13.0-alpha drops the vote for content inside lazy list and grid items (see
 * docs/perf/UPSTREAM-ISSUES.md, U1). There, move the content on [rememberDecorativePhase] instead.
 */
fun Modifier.decorativeFrameRate(): Modifier =
    preferredFrameRate(HeadroomMotion.DECORATIVE_FRAME_RATE).graphicsLayer()

/**
 * A phase from 0 to 1 that loops once every [periodMillis], for endless decorative motion such as
 * the next reset card's turning shape. Read it while drawing or in a layer block.
 *
 * It moves on at [HeadroomMotion.DECORATIVE_FRAME_RATE], not on every frame: each change redraws
 * the whole window, and a 120 Hz display would otherwise redraw a screen at rest 120 times a second
 * for motion too slow to look any smoother. Unlike [decorativeFrameRate], this works wherever the
 * content sits, including in lazy lists. Like any endless animation it stops in the background,
 * when Compose pauses its frame clock.
 */
@Composable
fun rememberDecorativePhase(periodMillis: Int): () -> Float {
    val phase = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(periodMillis) {
        val start = withInfiniteAnimationFrameMillis { it }
        while (true) {
            delay(DECORATIVE_FRAME_MILLIS)
            withInfiniteAnimationFrameMillis { now ->
                phase.floatValue = ((now - start) % periodMillis) / periodMillis.toFloat()
            }
        }
    }
    return remember(phase) { { phase.floatValue } }
}

private val DECORATIVE_FRAME_MILLIS = (1_000 / HeadroomMotion.DECORATIVE_FRAME_RATE).toLong()
