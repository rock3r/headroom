package dev.sebastiano.headroom.ui.accounts

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import dev.sebastiano.headroom.designsystem.HeadroomMotion
import dev.sebastiano.headroom.designsystem.ProviderAvatar
import dev.sebastiano.headroom.designsystem.animationsEnabled
import dev.sebastiano.headroom.designsystem.providerColors
import dev.sebastiano.headroom.designsystem.providerShape
import dev.sebastiano.headroom.model.Provider
import kotlin.math.PI
import kotlin.math.sin

const val SIGN_IN_FINISHING_TAG: String = "sign-in-finishing"

/**
 * The top of every sign-in step: the provider's avatar. While the sign-in is [finishing], the
 * Headroom mark joins it, and a stream of small shapes flows from the provider to Headroom: each
 * one leaves in the provider's shape and colour and arrives as the round dot at the centre of the
 * Headroom mark, in the theme's primary colour. The ring of the mark turns slowly with the stream.
 *
 * With animations off the same picture stands still: three shapes caught at different points of the
 * way, and the ring in the pose of the app icon. The words below it carry the meaning too.
 */
@Composable
internal fun SignInHeader(provider: Provider, finishing: Boolean, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        ProviderAvatar(provider, size = AvatarSize, modifier = Modifier.padding(8.dp))
        // The mark arrives from the avatar's side, and the row re-centres around the pair.
        AnimatedVisibility(
            visible = finishing,
            enter =
                expandHorizontally(HeadroomMotion.containerSpec(), expandFrom = Alignment.Start) +
                    fadeIn(HeadroomMotion.effectsSpec()),
            exit =
                shrinkHorizontally(
                    HeadroomMotion.containerSpec(),
                    shrinkTowards = Alignment.Start,
                ) + fadeOut(HeadroomMotion.effectsSpec()),
        ) {
            val motion = rememberFinishingMotion()
            Row(
                modifier = Modifier.testTag(SIGN_IN_FINISHING_TAG),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ShapeStream(provider, motion.stream, Modifier.size(StreamWidth, StreamHeight))
                HeadroomMark(motion.turn, Modifier.padding(8.dp).size(AvatarSize))
            }
        }
    }
}

/** Where the stream and the ring are, as fractions of one cycle. Read only while drawing. */
@Immutable private class FinishingMotion(val stream: () -> Float, val turn: () -> Float)

private val StillMotion = FinishingMotion(stream = { 0f }, turn = { 0f })

@Composable
private fun rememberFinishingMotion(): FinishingMotion {
    // An endless animation is not timed by the duration scale: with animations off it is not
    // started at all, and the still pose is drawn instead.
    if (!animationsEnabled()) return StillMotion
    val transition = rememberInfiniteTransition(label = "finishing sign-in")
    val stream = transition.animateFloat(0f, 1f, StreamSpec, label = "stream")
    val turn = transition.animateFloat(0f, 1f, TurnSpec, label = "ring")
    return remember(stream, turn) { FinishingMotion({ stream.value }, { turn.value }) }
}

/**
 * Small shapes crossing from the provider (start) to Headroom (end). Each one morphs from the
 * provider's shape to a circle and from the provider's colour to the theme's primary colour, and
 * fades in and out at the ends so the stream seems to come out of one avatar and into the other.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ShapeStream(provider: Provider, progress: () -> Float, modifier: Modifier = Modifier) {
    val departure = providerColors(provider).accent
    val arrival = MaterialTheme.colorScheme.primary
    val morph = remember(provider) { Morph(providerShape(provider), MaterialShapes.Circle) }
    val path = remember { Path() }
    Canvas(modifier) {
        val largest = size.height
        val start = largest / 2
        val travel = size.width - largest
        repeat(PARTICLES) { index ->
            // Spread evenly, offset by half a gap so the still pose shows three whole shapes.
            val along = (progress() + (index + HALF) / PARTICLES) % 1f
            val presence = sin(PI * along).toFloat()
            val side = largest * (SMALLEST + (1f - SMALLEST) * presence)
            val offset = start + travel * along
            val x = if (layoutDirection == LayoutDirection.Rtl) size.width - offset else offset
            morph.toPath(along, path)
            withTransform({
                translate(x - side / 2, center.y - side / 2)
                scale(side, side, pivot = Offset.Zero)
            }) {
                drawPath(path, lerp(departure, arrival, along).copy(alpha = presence))
            }
        }
    }
}

/**
 * The Headroom mark, as on the app icon: a quota ring with its headroom left open and a dot at the
 * centre, on a primary circle. [turn] rotates the ring; at 0 it is in the icon's pose.
 */
@Composable
private fun HeadroomMark(turn: () -> Float, modifier: Modifier = Modifier) {
    val background = MaterialTheme.colorScheme.primary
    val ink = MaterialTheme.colorScheme.onPrimary
    Canvas(modifier) {
        val radius = size.minDimension / 2
        val ring = radius * RING_RADIUS
        val width = size.minDimension * RING_WIDTH
        drawCircle(background, radius)
        drawCircle(ink.copy(alpha = TRACK_ALPHA), ring, style = Stroke(width))
        rotate(turn() * FULL_TURN) {
            drawArc(
                color = ink,
                startAngle = TWELVE_O_CLOCK,
                sweepAngle = RING_SWEEP,
                useCenter = false,
                topLeft = center - Offset(ring, ring),
                size = Size(ring * 2, ring * 2),
                style = Stroke(width, cap = StrokeCap.Round),
            )
        }
        drawCircle(ink, radius * DOT_RADIUS)
    }
}

private val AvatarSize = 72.dp
private val StreamWidth = 72.dp
private val StreamHeight = 14.dp

/** One shape crosses in this time. Linear, so the stream is steady rather than a gesture. */
private const val STREAM_MILLIS = 1800

private val StreamSpec: InfiniteRepeatableSpec<Float> =
    infiniteRepeatable(tween(STREAM_MILLIS, easing = LinearEasing))

/** The ring turns once every two shapes, so ring and stream keep one rhythm. */
private val TurnSpec: InfiniteRepeatableSpec<Float> =
    infiniteRepeatable(tween(STREAM_MILLIS * 2, easing = LinearEasing))

private const val PARTICLES = 3
private const val HALF = 0.5f

/** The size of a shape at the very ends of the stream, as a share of its size in the middle. */
private const val SMALLEST = 0.4f

// The mark's proportions, taken from the icon: on its 72-unit visible circle, the ring has a
// radius of 23 and a width of 9, and the dot a radius of 7. The open part is a quarter turn.
private const val RING_RADIUS = 23f / 36f
private const val RING_WIDTH = 9f / 72f
private const val DOT_RADIUS = 7f / 36f
private const val TRACK_ALPHA = 0.35f
private const val TWELVE_O_CLOCK = -90f
private const val RING_SWEEP = 270f
private const val FULL_TURN = 360f
