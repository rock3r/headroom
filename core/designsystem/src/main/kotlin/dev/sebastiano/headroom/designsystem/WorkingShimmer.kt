package dev.sebastiano.headroom.designsystem

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription

/**
 * Draws [content], an indicator, and marks its value as being fetched again, such as an account's
 * bars while a reset applies. While [working], a soft band of light sweeps along whatever the
 * indicator draws, and only there: the empty space around a thin bar stays empty. With motion
 * reduced the indicator only dims, a static "in progress" look. [workingDescription] tells screen
 * readers.
 */
@Composable
fun WorkingShimmer(
    working: Boolean,
    workingDescription: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val animate = animationsEnabled()
    val light = shimmerColor()
    val state =
        if (working) Modifier.semantics { stateDescription = workingDescription } else Modifier
    val look =
        when {
            !working -> Modifier
            !animate -> Modifier.graphicsLayer { alpha = STATIC_ALPHA }
            else -> {
                val transition = rememberInfiniteTransition(label = "working shimmer")
                val phase =
                    transition.animateFloat(
                        initialValue = 0f,
                        targetValue = 1f,
                        animationSpec =
                            infiniteRepeatable(tween(SWEEP_MILLIS, easing = LinearEasing)),
                        label = "working shimmer phase",
                    )
                Modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        val band = size.width * BAND_FRACTION
                        // The band travels from fully off the left edge to fully off the right.
                        val centre = -band + (size.width + band * 2) * phase.value
                        drawRect(
                            brush =
                                Brush.linearGradient(
                                    colors = listOf(Color.Transparent, light, Color.Transparent),
                                    start = Offset(centre - band, 0f),
                                    end = Offset(centre + band, 0f),
                                ),
                            blendMode = BlendMode.SrcAtop,
                        )
                    }
            }
        }
    Box(modifier = modifier.then(state).then(look)) { content() }
}

/** A light that reads on the theme's primary colour, in light and dark themes. */
@Composable
@ReadOnlyComposable
private fun shimmerColor(): Color {
    val dark = MaterialTheme.colorScheme.surface.luminance() < DARK_LUMINANCE
    return Color.White.copy(alpha = if (dark) DARK_LIGHT_ALPHA else LIGHT_ALPHA)
}

private const val SWEEP_MILLIS = 1_100
private const val BAND_FRACTION = 0.35f
private const val STATIC_ALPHA = 0.5f
private const val LIGHT_ALPHA = 0.65f
private const val DARK_LIGHT_ALPHA = 0.45f
private const val DARK_LUMINANCE = 0.5f
