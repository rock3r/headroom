package dev.sebastiano.headroom.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SharedTransitionScope.SharedContentState
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.sebastiano.headroom.R
import dev.sebastiano.headroom.designsystem.HeadroomIcons
import dev.sebastiano.headroom.designsystem.HeadroomMotion
import kotlin.math.hypot
import kotlin.math.max

/**
 * The circular reveal between the overview and Settings. Settings grows out of the settings button
 * in a circle; its title comes out of the button, and the button turns into Settings' close button.
 * Closing plays it backwards, and the predictive back gesture scrubs it.
 *
 * [origin] is the centre of the settings button in root coordinates, recorded while the overview
 * shows it.
 */
@Stable
class SettingsReveal(val transitionScope: SharedTransitionScope) {
    var origin: Offset by mutableStateOf(Offset.Unspecified)
}

/**
 * The reveal as one page sees it. [visibility] is that page's enter and exit. [revealing] is true
 * while the pages move between the overview and Settings; [animate] is false when motion is off or
 * reduced, and then nothing is shared or turned. [scrubbing] is true while the back gesture drives
 * the pages: the reveal then moves in step with the finger instead of on springs, which would run
 * far ahead of it.
 */
@Stable
class PageReveal(
    val reveal: SettingsReveal,
    val visibility: AnimatedVisibilityScope,
    val revealing: Boolean,
    val animate: Boolean,
    val scrubbing: Boolean,
) {
    /** The spec for the reveal's spatial parts: the radius, the shared bounds and the turn. */
    @Composable
    @ReadOnlyComposable
    fun <T> spatialSpec(): FiniteAnimationSpec<T> =
        if (scrubbing) HeadroomMotion.scrubSpec() else MaterialTheme.motionScheme.slowSpatialSpec()

    /** The spec for the shared elements' fades. */
    @Composable
    @ReadOnlyComposable
    fun <T> effectsSpec(): FiniteAnimationSpec<T> =
        if (scrubbing) HeadroomMotion.scrubSpec() else MaterialTheme.motionScheme.slowEffectsSpec()
}

/**
 * Settings' content arriving with the reveal: it fades in while moving down a little into place,
 * just behind the circle. Leaving, it only fades. Without the reveal (reduced motion, or another
 * page) nothing extra happens.
 */
@Composable
internal fun Modifier.revealContentEntrance(page: PageReveal?): Modifier {
    if (page == null || !page.animate || !page.revealing) return this
    val offset = with(LocalDensity.current) { CONTENT_DROP.roundToPx() }
    val fade = page.effectsSpec<Float>()
    val move = page.spatialSpec<IntOffset>()
    return with(page.visibility) {
        animateEnterExit(
            enter =
                fadeIn(if (page.scrubbing) fade else delayed(CONTENT_DELAY_MILLIS)) +
                    slideInVertically(move) { -offset },
            exit = fadeOut(fade),
        )
    }
}

/** A fade that waits a moment, so the circle leads and the content follows. */
private fun delayed(delayMillis: Int): FiniteAnimationSpec<Float> =
    tween(durationMillis = CONTENT_FADE_MILLIS, delayMillis = delayMillis)

private val CONTENT_DROP = 24.dp
private const val CONTENT_DELAY_MILLIS = 120
private const val CONTENT_FADE_MILLIS = 320

private const val BUTTON_KEY = "settings-button"
private const val TITLE_KEY = "settings-title"

/** The cog turns this far clockwise as it leaves; the close button arrives from the other side. */
private const val COG_TURN_DEGREES = 90f
private const val CLOSE_TURN_DEGREES = -90f

/**
 * The overview's settings button. It records where it is for [reveal], and becomes Settings' close
 * button.
 */
@Composable
internal fun SettingsButton(
    onClick: () -> Unit,
    reveal: PageReveal?,
    modifier: Modifier = Modifier,
) {
    val settings = reveal?.reveal
    RevealButton(
        icon = HeadroomIcons.SettingsFilled,
        label = stringResource(R.string.action_settings),
        onClick = onClick,
        reveal = reveal,
        hiddenTurn = COG_TURN_DEGREES,
        titleOrigin = true,
        modifier = modifier.onGloballyPositioned { settings?.origin = it.boundsInRoot().center },
    )
}

/** Settings' close button, in the place of the back arrow. It turns back into the cog. */
@Composable
internal fun CloseSettingsButton(
    onClick: () -> Unit,
    reveal: PageReveal?,
    modifier: Modifier = Modifier,
) {
    RevealButton(
        icon = HeadroomIcons.Close,
        label = stringResource(R.string.action_close_settings),
        onClick = onClick,
        reveal = reveal,
        hiddenTurn = CLOSE_TURN_DEGREES,
        titleOrigin = false,
        modifier = modifier,
    )
}

/** Settings' title, which grows out of the settings button. */
@Composable
internal fun SettingsTitle(text: String, reveal: PageReveal?, modifier: Modifier = Modifier) {
    val shared = rememberRevealElement(reveal, TITLE_KEY)
    Text(
        text = text,
        style = MaterialTheme.typography.headlineLarge,
        modifier = modifier.sharedReveal(shared).semantics { heading() },
    )
}

/**
 * An icon button whose icon is shared with the other side of the reveal and turns as it goes. With
 * [titleOrigin], Settings' title starts from the icon, at the icon's size.
 */
@Composable
private fun RevealButton(
    @DrawableRes icon: Int,
    label: String,
    onClick: () -> Unit,
    reveal: PageReveal?,
    hiddenTurn: Float,
    titleOrigin: Boolean,
    modifier: Modifier = Modifier,
) {
    val turn = rememberTurn(reveal, hiddenTurn)
    val shared = rememberRevealElement(reveal, BUTTON_KEY)
    val title = rememberRevealElement(reveal.takeIf { titleOrigin }, TITLE_KEY)
    IconButton(onClick = onClick, modifier = modifier.semantics { contentDescription = label }) {
        Box {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier.sharedReveal(shared).graphicsLayer { rotationZ = turn.value },
            )
            Spacer(Modifier.matchParentSize().sharedReveal(title))
        }
    }
}

/** How far the button is turned: none at rest, [hiddenTurn] when its page is hidden. */
@Composable
private fun rememberTurn(page: PageReveal?, hiddenTurn: Float): State<Float> {
    if (page == null || !page.animate) return remember { mutableFloatStateOf(0f) }
    val spec = page.spatialSpec<Float>()
    return page.visibility.transition.animateFloat(transitionSpec = { spec }, label = "turn") {
        state ->
        if (state == EnterExitState.Visible || !page.revealing) 0f else hiddenTurn
    }
}

/** One element shared with the other page under a key, with the specs it moves and fades on. */
@Stable
private class RevealElement(
    val page: PageReveal,
    val state: SharedContentState,
    val spatial: FiniteAnimationSpec<Rect>,
    val effects: FiniteAnimationSpec<Float>,
)

/** The element shared under [key], or null when nothing is shared. */
@Composable
private fun rememberRevealElement(page: PageReveal?, key: String): RevealElement? {
    if (page == null || !page.animate) return null
    val state = with(page.reveal.transitionScope) { rememberSharedContentState(key) }
    val spatial = page.spatialSpec<Rect>()
    val effects = page.effectsSpec<Float>()
    return remember(page, state, spatial, effects) { RevealElement(page, state, spatial, effects) }
}

/** Shares this element's bounds with the other page, on the spatial spring. */
private fun Modifier.sharedReveal(element: RevealElement?): Modifier {
    if (element == null) return this
    return with(element.page.reveal.transitionScope) {
        this@sharedReveal.sharedBounds(
            sharedContentState = element.state,
            animatedVisibilityScope = element.page.visibility,
            enter = fadeIn(element.effects),
            exit = fadeOut(element.effects),
            boundsTransform = BoundsTransform { _, _ -> element.spatial },
        )
    }
}

/**
 * The circle a page is clipped to while it is revealed. [progress] follows the page's enter and
 * exit, so the circle grows as Settings opens, shrinks as it closes, and follows the back gesture.
 */
@Stable
internal class RevealClip(val reveal: SettingsReveal, val progress: State<Float>) {
    /** Where the page sits in the root. Written after layout and read while drawing. */
    var topLeft: Offset = Offset.Zero
}

/** The reveal clip for a page, or null when [enabled] is false or nothing is revealed. */
@Composable
internal fun rememberRevealClip(page: PageReveal?, enabled: Boolean): RevealClip? {
    if (page == null || !page.animate || !enabled) return null
    val spec = page.spatialSpec<Float>()
    val progress =
        page.visibility.transition.animateFloat(transitionSpec = { spec }, label = "reveal") { state
            ->
            if (state == EnterExitState.Visible || !page.revealing) 1f else 0f
        }
    return remember(page.reveal, progress) { RevealClip(page.reveal, progress) }
}

/** Clips to [clip]'s circle round the settings button. Outside the reveal nothing is clipped. */
internal fun Modifier.revealClip(clip: RevealClip?): Modifier {
    if (clip == null) return this
    return onGloballyPositioned { clip.topLeft = it.positionInRoot() }
        .graphicsLayer {
            val fraction = clip.progress.value
            if (fraction < 1f) {
                this.clip = true
                shape = RevealShape(clip.reveal.origin - clip.topLeft, fraction)
            } else {
                this.clip = false
            }
        }
}

/**
 * A circle round [center] (the top end corner if unknown) that covers [fraction] of the way to the
 * farthest corner of the page.
 */
private class RevealShape(private val center: Offset, private val fraction: Float) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val origin =
            if (center.isSpecified) center
            else if (layoutDirection == LayoutDirection.Ltr) Offset(size.width, 0f) else Offset.Zero
        val farthest =
            max(
                max(hypot(origin.x, origin.y), hypot(size.width - origin.x, origin.y)),
                max(
                    hypot(origin.x, size.height - origin.y),
                    hypot(size.width - origin.x, size.height - origin.y),
                ),
            )
        val radius = farthest * fraction.coerceAtLeast(0f)
        return Outline.Rounded(
            RoundRect(
                left = origin.x - radius,
                top = origin.y - radius,
                right = origin.x + radius,
                bottom = origin.y + radius,
                cornerRadius = CornerRadius(radius),
            )
        )
    }
}
