package dev.sebastiano.headroom.ui.delights

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/**
 * One burst of confetti: [PARTICLES] pieces thrown up and out from [origin]. They slow down in the
 * air, fall, spin, sway from side to side, and fade out before [CONFETTI_MILLIS] ends.
 */
@Stable
internal class ConfettiBurst(
    val origin: Offset,
    colors: List<Color>,
    density: Float,
    random: Random,
) {
    /** From 0 to 1 over the burst's life. */
    val progress = Animatable(0f)

    val particles: List<ConfettiParticle> =
        List(PARTICLES) { ConfettiParticle.random(random, colors, density) }
}

/**
 * One piece of confetti. Speeds are in pixels per second, and the air slows it down in proportion
 * to its speed, so it falls at a steady speed after a moment, like paper does.
 */
@Immutable
internal class ConfettiParticle(
    private val velocity: Offset,
    private val gravity: Float,
    val size: Size,
    val round: Boolean,
    val color: Color,
    private val rotation: Float,
    private val spin: Float,
    private val flipSpeed: Float,
    private val sway: Float,
    private val swaySpeed: Float,
    private val swayPhase: Float,
) {
    /** Where the piece is [seconds] after it left [origin]. */
    fun position(origin: Offset, seconds: Float): Offset {
        val slowed = (1f - exp(-DRAG * seconds)) / DRAG
        val falling = gravity / DRAG
        val swayed = sway * (sin(swaySpeed * seconds + swayPhase) - sin(swayPhase))
        return origin +
            Offset(
                x = velocity.x * slowed + swayed,
                y = falling * seconds + (velocity.y - falling) * slowed,
            )
    }

    /** The piece's angle, in degrees, [seconds] after it left. */
    fun angle(seconds: Float): Float = rotation + spin * seconds

    /** How wide the piece looks as it tumbles, from edge-on (near 0) to flat (1). */
    fun flatness(seconds: Float): Float = max(MIN_FLATNESS, abs(cos(flipSpeed * seconds)))

    companion object {
        fun random(random: Random, colors: List<Color>, density: Float): ConfettiParticle {
            // Mostly upwards, in a cone round straight up.
            val angle = -PI / 2 + (random.nextFloat() - HALF) * CONE
            val speed = random.between(MIN_SPEED, MAX_SPEED) * density
            val round = random.nextFloat() < ROUND_SHARE
            val width = random.between(MIN_WIDTH, MAX_WIDTH) * density
            return ConfettiParticle(
                velocity = Offset((cos(angle) * speed).toFloat(), (sin(angle) * speed).toFloat()),
                gravity = GRAVITY * density,
                size =
                    if (round) Size(width, width)
                    else Size(width, width * random.between(MIN_ASPECT, MAX_ASPECT)),
                round = round,
                color = colors[random.nextInt(colors.size)],
                rotation = random.nextFloat() * FULL_TURN,
                spin = random.between(-MAX_SPIN, MAX_SPIN),
                flipSpeed = random.between(MIN_FLIP, MAX_FLIP),
                sway = random.between(0f, MAX_SWAY) * density,
                swaySpeed = random.between(MIN_SWAY_SPEED, MAX_SWAY_SPEED),
                swayPhase = random.nextFloat() * TWO_PI,
            )
        }

        private fun Random.between(from: Float, to: Float): Float = from + nextFloat() * (to - from)
    }
}

/** Draws every burst in [bursts] where it is now. It only draws: touches go to the app below. */
@Composable
internal fun Confetti(bursts: List<ConfettiBurst>, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        bursts.forEach { burst ->
            val progress = burst.progress.value
            val seconds = progress * CONFETTI_MILLIS / MILLIS_PER_SECOND
            // Full colour for most of the flight, then a fade over the last part.
            val alpha = ((1f - progress) / FADE_SHARE).coerceIn(0f, 1f)
            burst.particles.forEach { drawParticle(it, burst.origin, seconds, alpha) }
        }
    }
}

private fun DrawScope.drawParticle(
    particle: ConfettiParticle,
    origin: Offset,
    seconds: Float,
    alpha: Float,
) {
    val at = particle.position(origin, seconds)
    withTransform({
        translate(at.x, at.y)
        rotate(particle.angle(seconds), pivot = Offset.Zero)
        scale(particle.flatness(seconds), 1f, pivot = Offset.Zero)
    }) {
        val size = particle.size
        if (particle.round) {
            drawCircle(particle.color, radius = size.width / 2, center = Offset.Zero, alpha = alpha)
        } else {
            drawRect(
                particle.color,
                topLeft = Offset(-size.width / 2, -size.height / 2),
                size = size,
                alpha = alpha,
            )
        }
    }
}

private const val PARTICLES = 80
private const val MILLIS_PER_SECOND = 1_000f
/** The last share of the burst's life, during which the pieces fade out. */
private const val FADE_SHARE = 0.3f
private const val HALF = 0.5
/** The width of the cone the pieces leave in, in radians: about 130 degrees. */
private const val CONE = 2.3
/** In dp per second, like the other lengths here, before they are scaled by the density. */
private const val MIN_SPEED = 260f
private const val MAX_SPEED = 720f
private const val GRAVITY = 1_100f
/** How strongly the air slows a piece down, per second. */
private const val DRAG = 2.4f
private const val ROUND_SHARE = 0.3f
private const val MIN_WIDTH = 5f
private const val MAX_WIDTH = 8f
private const val MIN_ASPECT = 1.4f
private const val MAX_ASPECT = 2.2f
private const val FULL_TURN = 360f
/** Degrees per second. */
private const val MAX_SPIN = 420f
/** Radians per second of the tumble that makes a piece look thin, then flat again. */
private const val MIN_FLIP = 4f
private const val MAX_FLIP = 11f
private const val MIN_FLATNESS = 0.08f
private const val MAX_SWAY = 14f
private const val MIN_SWAY_SPEED = 3f
private const val MAX_SWAY_SPEED = 6f
private const val TWO_PI = (2 * PI).toFloat()
