package dev.sebastiano.headroom.ui.resets

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.sebastiano.headroom.R
import dev.sebastiano.headroom.designsystem.HeadroomMotion
import dev.sebastiano.headroom.designsystem.WorkingShimmer
import dev.sebastiano.headroom.designsystem.animationsEnabled
import dev.sebastiano.headroom.model.QuotaDisplay
import dev.sebastiano.headroom.ui.asFraction
import dev.sebastiano.headroom.ui.components.percentDescription
import dev.sebastiano.headroom.ui.delights.BarGloss
import dev.sebastiano.headroom.ui.delights.LocalDelights
import dev.sebastiano.headroom.ui.home.WindowSummary
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/**
 * The sheet's usage visual: one bar per window of the account, in used or left mode. The windows a
 * reset restores ([cleared]) are drawn in full; the others are dimmed, so the scope reads at a
 * glance. While [working], the cleared bars shimmer.
 *
 * When new usage arrives and a cleared bar moves the way a reset moves it (down in used mode, up in
 * left mode), the bars refill one after the other, a short stagger apart, on the critically damped
 * reset spring, so the number never passes its new value. Once a bar settles, a gloss sweeps it
 * once and a few stars twinkle, when the reset confetti delight is on: see [BarGloss]. With motion
 * reduced the new values crossfade in, with no spring, no stagger and no gloss.
 */
@Composable
internal fun RefillBars(
    windows: List<WindowSummary>,
    cleared: Set<String>,
    display: QuotaDisplay,
    working: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // The cleared bars refill in order, a stagger apart; the others keep their place.
        val ranks =
            windows.filter { it.id in cleared }.withIndex().associate { it.value.id to it.index }
        windows.forEach { window ->
            val isCleared = window.id in cleared
            RefillBar(
                window = window,
                display = display,
                working = working && isCleared,
                dimmed = !isCleared,
                staggerIndex = ranks[window.id] ?: 0,
            )
        }
    }
}

@Composable
private fun RefillBar(
    window: WindowSummary,
    display: QuotaDisplay,
    working: Boolean,
    dimmed: Boolean,
    staggerIndex: Int,
) {
    val target = display.percent(window.usedPercent).asFraction()
    val description =
        stringResource(
            R.string.redeem_usage_description,
            window.label,
            percentDescription(display.percent(window.usedPercent).roundToInt(), display),
        )
    val workingText = stringResource(R.string.redeem_bar_working)
    Box(
        modifier =
            Modifier.fillMaxWidth()
                .graphicsLayer { alpha = if (dimmed) DIMMED_ALPHA else 1f }
                .clearAndSetSemantics { contentDescription = description }
    ) {
        if (animationsEnabled()) {
            AnimatedBar(window.label, target, display, working, workingText, staggerIndex)
        } else {
            // Reduced motion: the new value crossfades in, with no spring and no stagger.
            Crossfade(
                targetState = target,
                animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
                label = "refill bar",
            ) { shown ->
                BarWithLabel(window.label, shown, display, working, workingText, gloss = { 0f })
            }
        }
    }
}

@Composable
private fun AnimatedBar(
    label: String,
    target: Float,
    display: QuotaDisplay,
    working: Boolean,
    workingText: String,
    staggerIndex: Int,
) {
    val fill = remember { Animatable(target) }
    val gloss = remember { Animatable(0f) }
    val refillSpec = HeadroomMotion.resetDrainSpec<Float>()
    val dataSpec = HeadroomMotion.dataSpec<Float>()
    // The gloss belongs to the reset's celebration: it plays only when the reset confetti would.
    val shiny = LocalDelights.current?.canBurst == true
    LaunchedEffect(target) {
        val from = fill.value
        val refill =
            abs(target - from) > MIN_REFILL &&
                when (display) {
                    QuotaDisplay.Used -> target < from
                    QuotaDisplay.Left -> target > from
                }
        if (!refill) {
            fill.animateTo(target, dataSpec)
            return@LaunchedEffect
        }
        delay(staggerIndex * STAGGER_MILLIS)
        fill.animateTo(target, refillSpec)
        // The bar has settled: a gloss sweeps it once, and a few stars twinkle as it passes.
        if (shiny) {
            gloss.snapTo(0f)
            gloss.animateTo(1f, tween(GLOSS_MILLIS, easing = LinearEasing))
        }
    }
    BarWithLabel(label, fill.value, display, working, workingText, gloss = { gloss.value })
}

@Composable
private fun BarWithLabel(
    label: String,
    fraction: Float,
    display: QuotaDisplay,
    working: Boolean,
    workingText: String,
    gloss: () -> Float,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = percentDescription((fraction * FULL_PERCENT).roundToInt(), display),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        WorkingShimmer(working = working, workingDescription = workingText) {
            Bar(fraction = fraction, gloss = gloss)
        }
    }
}

/** A thick rounded bar, and the gloss that sweeps it once after a reset refills it. */
@Composable
private fun Bar(fraction: Float, gloss: () -> Float) {
    val fillColor = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.secondaryContainer
    Box(
        modifier =
            Modifier.fillMaxWidth().height(BarHeight).drawBehind {
                val radius = CornerRadius(size.height / 2)
                val filled = size.width * fraction.coerceIn(0f, 1f)
                drawRoundRect(color = track, cornerRadius = radius)
                if (filled > 0f) {
                    drawRoundRect(
                        color = fillColor,
                        size = Size(filled.coerceAtLeast(size.height), size.height),
                        cornerRadius = radius,
                    )
                }
            }
    ) {
        BarGloss(progress = gloss, modifier = Modifier.matchParentSize())
    }
}

private val BarHeight = 12.dp
private const val FULL_PERCENT = 100
private const val DIMMED_ALPHA = 0.45f
private const val MIN_REFILL = 0.02f
private const val STAGGER_MILLIS = 160L
/** One gloss sweep, with its stars, per bar. */
private const val GLOSS_MILLIS = 800
