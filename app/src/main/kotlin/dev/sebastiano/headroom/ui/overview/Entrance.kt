package dev.sebastiano.headroom.ui.overview

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import dev.sebastiano.headroom.designsystem.animationsEnabled

/** How long one card takes to fade and rise in. */
const val ENTRANCE_DURATION_MILLIS: Int = 180

private const val STAGGER_STEP_MILLIS = 40
private const val STAGGER_MAX_DELAY_MILLIS = 120
private val EntranceOffset = 16.dp

/**
 * The delay before card [index] starts to enter. Steps are capped, so the whole cascade ends within
 * about 300 ms however many cards there are: past that, the last card would feel late.
 */
fun staggerDelayMillis(index: Int): Int =
    (index * STAGGER_STEP_MILLIS).coerceAtMost(STAGGER_MAX_DELAY_MILLIS)

/**
 * Fades and lifts [content] in when [play] is true, after [staggerDelayMillis] for [index]. Only
 * the cards composed on the first open play it, so it never runs on scroll; with animations off the
 * content is simply there.
 */
@Composable
fun StaggeredEntrance(
    index: Int,
    play: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val animate = play && animationsEnabled()
    val progress = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (progress.value < 1f) {
            progress.animateTo(
                targetValue = 1f,
                animationSpec =
                    tween(
                        durationMillis = ENTRANCE_DURATION_MILLIS,
                        delayMillis = staggerDelayMillis(index),
                        easing = EmphasizedDecelerate,
                    ),
            )
        }
    }
    val offset = with(LocalDensity.current) { EntranceOffset.toPx() }
    Box(
        modifier =
            modifier.graphicsLayer {
                alpha = progress.value
                translationY = (1f - progress.value) * offset
            }
    ) {
        content()
    }
}

/** Material's emphasized decelerate curve: quick to arrive, gentle to settle. */
private val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
