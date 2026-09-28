package dev.sebastiano.headroom.designsystem

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.MotionDurationScale

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

    private const val REVEAL_DURATION_MILLIS = 520
    private val REVEAL_EASING = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

    /**
     * For a liquid ripple spreading from a tap. It is linear because a ring on water spreads at a
     * steady speed; the ripple's shader fades the ring out as it grows, which gives it its ease.
     */
    fun <T> rippleSpec(): FiniteAnimationSpec<T> =
        tween(durationMillis = RIPPLE_DURATION_MILLIS, easing = LinearEasing)

    private const val RIPPLE_DURATION_MILLIS = 800

    /**
     * For a slow decorative drift that never stops, such as the tilt of the next reset card's
     * shape. It runs one linear turn of a phase per period, and the caller turns the phase into
     * motion with a sine, which eases both ends of every swing.
     */
    fun <T> driftSpec(): InfiniteRepeatableSpec<T> =
        infiniteRepeatable(tween(durationMillis = DRIFT_PERIOD_MILLIS, easing = LinearEasing))

    private const val DRIFT_PERIOD_MILLIS = 24_000

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
