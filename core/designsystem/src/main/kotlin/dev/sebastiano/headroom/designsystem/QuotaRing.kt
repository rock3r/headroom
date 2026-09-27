package dev.sebastiano.headroom.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The detail screen's hero ring: [progress] on the outer ring (the weekly window) and an optional
 * [innerProgress] (the session window). Both sweep in from zero when the ring first appears, on a
 * critically damped spring, so the arc never passes its value. Only the outer ring can be wavy, and
 * only when the ring is large enough for the wave to read as a wave.
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
    content: @Composable BoxScope.() -> Unit = {},
) {
    val animate = animationsEnabled()
    val spec = HeadroomMotion.dataSpec<Float>()
    val outerTarget = progress.coerceIn(0f, 1f)
    val innerTarget = innerProgress?.coerceIn(0f, 1f) ?: 0f
    val outer = remember { Animatable(if (animate) 0f else outerTarget) }
    val inner = remember { Animatable(if (animate) 0f else innerTarget) }
    LaunchedEffect(outerTarget) { outer.animateTo(outerTarget, spec) }
    LaunchedEffect(innerTarget) { inner.animateTo(innerTarget, spec) }

    val isWavy = wavy && animate && size >= MinWavySize
    val style = if (isWavy) IndicatorStyle.Wavy else IndicatorStyle.Flat
    val strokePx = with(LocalDensity.current) { strokeWidth.toPx() }
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        val outerModifier = Modifier.size(size).semantics { indicatorStyle = style }
        if (isWavy) {
            val stroke = remember(strokePx) { Stroke(width = strokePx, cap = StrokeCap.Round) }
            CircularWavyProgressIndicator(
                progress = { outer.value },
                modifier = outerModifier,
                color = color,
                trackColor = trackColor,
                stroke = stroke,
                trackStroke = stroke,
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
