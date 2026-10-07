package dev.sebastiano.headroom.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The detail screen's hero ring: [progress] on the outer ring (the weekly window) and an optional
 * [innerProgress] (the session window). Both sweep in from zero when the ring first appears, on a
 * critically damped spring, so the arc never passes its value. Only the outer ring can be wavy, and
 * only when the ring is large enough for the wave to read as a wave. [animationSpec] moves both
 * arcs to a new value; a reset passes [HeadroomMotion.resetDrainSpec].
 */
@Composable
fun QuotaRing(
    progress: Float,
    modifier: Modifier = Modifier,
    innerProgress: Float? = null,
    wavy: Boolean = false,
    size: Dp = 176.dp,
    strokeWidth: Dp = 8.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    innerColor: Color = MaterialTheme.colorScheme.tertiary,
    trackColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    animationSpec: FiniteAnimationSpec<Float> = HeadroomMotion.dataSpec(),
    content: @Composable BoxScope.() -> Unit = {},
) {
    val animate = animationsEnabled()
    val spec = animationSpec
    val outerTarget = progress.coerceIn(0f, 1f)
    val innerTarget = innerProgress?.coerceIn(0f, 1f) ?: 0f
    val outer = remember { Animatable(if (animate) 0f else outerTarget) }
    val inner = remember { Animatable(if (animate) 0f else innerTarget) }
    LaunchedEffect(outerTarget) { outer.animateTo(outerTarget, spec) }
    LaunchedEffect(innerTarget) { inner.animateTo(innerTarget, spec) }

    val isWavy = wavy && animate && size >= MinWavySize
    val style = if (isWavy) IndicatorStyle.Wavy else IndicatorStyle.Flat
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        val outerModifier = Modifier.size(size).semantics { indicatorStyle = style }
        if (isWavy) {
            WavyRing(
                progress = { outer.value },
                color = color,
                trackColor = trackColor,
                strokeWidth = strokeWidth,
                modifier =
                    outerModifier.semantics {
                        progressBarRangeInfo = ProgressBarRangeInfo(outer.value, 0f..1f)
                    },
            )
        } else {
            CircularProgressIndicator(
                progress = { outer.value },
                modifier = outerModifier,
                color = color,
                strokeWidth = strokeWidth,
                trackColor = trackColor,
                strokeCap = StrokeCap.Round,
            )
        }
        if (innerProgress != null) {
            CircularProgressIndicator(
                progress = { inner.value },
                modifier = Modifier.size(size - (strokeWidth + InnerGap) * 2),
                color = innerColor,
                strokeWidth = strokeWidth,
                trackColor = trackColor,
                strokeCap = StrokeCap.Round,
            )
        }
        content()
    }
}

/** Below this size the wave reads as noise, so small rings are always flat. */
private val MinWavySize = 100.dp
private val InnerGap = 8.dp

/**
 * A gentle wave on the ring's radius, drawn here rather than with Material's wavy indicator: that
 * one only renders a partial amplitude after it has animated into it, so a ring that turns wavy at
 * rest (after a refresh, or when first drawn at its value) would stay flat. The wave eases in and
 * out at both ends of the arc, and its phase moves on the Compose clock, so it stops when
 * animations are off.
 */
@Composable
private fun WavyRing(
    progress: () -> Float,
    color: Color,
    trackColor: Color,
    strokeWidth: Dp,
    modifier: Modifier = Modifier,
) {
    val phase by
        rememberInfiniteTransition(label = "wave")
            .animateFloat(
                initialValue = 0f,
                targetValue = FULL_TURN,
                animationSpec =
                    infiniteRepeatable(tween(WAVE_PERIOD_MILLIS, easing = LinearEasing)),
                label = "wave phase",
            )
    Canvas(modifier = modifier) {
        val stroke = strokeWidth.toPx()
        val amplitude = WaveAmplitude.toPx()
        val radius = size.minDimension / 2 - stroke / 2 - amplitude
        val center = Offset(size.width / 2, size.height / 2)
        val sweep = progress().coerceIn(0f, 1f) * FULL_TURN
        val start = -HALF_PI
        val gap = (stroke + TrackGap.toPx()) / radius
        val style = Stroke(width = stroke, cap = StrokeCap.Round)
        if (sweep < FULL_TURN - gap * 2) {
            val trackStart = if (sweep > 0f) sweep + gap else 0f
            val trackSweep = FULL_TURN - trackStart - if (sweep > 0f) gap else 0f
            drawArc(
                color = trackColor,
                startAngle = Math.toDegrees((start + trackStart).toDouble()).toFloat(),
                sweepAngle = Math.toDegrees(trackSweep.toDouble()).toFloat(),
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2),
                style = style,
            )
        }
        if (sweep <= 0f) return@Canvas
        val waves = (FULL_TURN * radius / WaveLength.toPx()).roundToInt().coerceAtLeast(MIN_WAVES)
        val taper = WaveTaper.toPx() / radius
        val steps = (sweep * radius / STEP_PX).roundToInt().coerceAtLeast(MIN_STEPS)
        val path = Path()
        for (index in 0..steps) {
            val along = sweep * index / steps
            val ease = min(1f, min(along / taper, (sweep - along) / taper + TAPER_FLOOR))
            val r = radius + amplitude * ease * sin(waves * along - phase)
            val angle = start + along
            val x = center.x + r * cos(angle)
            val y = center.y + r * sin(angle)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color = color, style = style)
    }
}

private val WaveAmplitude = 1.8.dp
private val WaveLength = 28.dp
private val WaveTaper = 10.dp
private val TrackGap = 4.dp
private const val FULL_TURN = (2 * PI).toFloat()
private const val HALF_PI = (PI / 2).toFloat()
private const val TAPER_FLOOR = 0.3f
private const val STEP_PX = 1.5f
private const val MIN_STEPS = 12
private const val MIN_WAVES = 6
private const val WAVE_PERIOD_MILLIS = 1_600
