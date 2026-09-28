package dev.sebastiano.headroom.island

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import dev.sebastiano.headroom.designsystem.HeadroomMotion
import dev.sebastiano.headroom.designsystem.ProviderAvatar
import dev.sebastiano.headroom.designsystem.animationsEnabled
import dev.sebastiano.headroom.model.Provider
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/** The steps of one island: it grows, shows its words, hides them, and shrinks. */
internal enum class IslandStage {
    /** The size of the camera hole, and nothing to read. */
    Collapsed,
    /** The pill is growing, or has grown and the words are not there yet. */
    Growing,
    /** The pill is full size and shows the logo and the words. */
    Showing,
    /** The words have faded out and the pill is about to shrink. */
    Hiding,
}

/** How long each step of [ResetIslandHost] takes, in milliseconds. */
internal object IslandTiming {
    /** The grow spring has mostly settled by then. */
    const val GROW = 420L

    /** How long the words stay on screen. */
    const val HOLD = 4_000L

    /** The words fade out before the pill shrinks. */
    const val FADE_OUT = 180L

    /** The shrink spring has settled by then, so the window can go. */
    const val SHRINK = 420L
}

/**
 * Plays one island for [request]: grow, hold for about four seconds, shrink. It calls [onFinish]
 * when the pill is back in the cutout and the window can be removed. A new request restarts the
 * hold, and the pill stays as it is.
 */
@Composable
internal fun ResetIslandHost(
    request: IslandRequest,
    geometry: IslandGeometry,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var stage by remember { mutableStateOf(IslandStage.Collapsed) }
    val currentOnFinish by rememberUpdatedState(onFinish)
    LaunchedEffect(request.serial) {
        if (stage == IslandStage.Collapsed) {
            stage = IslandStage.Growing
            delay(IslandTiming.GROW)
        }
        stage = IslandStage.Showing
        delay(IslandTiming.HOLD)
        stage = IslandStage.Hiding
        delay(IslandTiming.FADE_OUT)
        stage = IslandStage.Collapsed
        delay(IslandTiming.SHRINK)
        currentOnFinish()
    }
    ResetIslandSurface(
        geometry = geometry,
        stage = stage,
        provider = request.provider,
        message = request.message,
        modifier = modifier,
    )
}

/**
 * The island itself: one black surface whose size follows springs between the camera hole and the
 * pill, and whose corner radius is half its height. The logo and the words fade in once the pill
 * has grown, and out before it shrinks. With motion reduced the pill does not grow: it fades in at
 * full size and fades out.
 *
 * [geometry] is in pixels, relative to the window that this fills.
 */
@Composable
internal fun ResetIslandSurface(
    geometry: IslandGeometry,
    stage: IslandStage,
    provider: Provider,
    message: String,
    modifier: Modifier = Modifier,
) {
    val open = stage != IslandStage.Collapsed
    val animate = animationsEnabled()
    val grow by
        animateFloatAsState(
            targetValue = if (open) 1f else 0f,
            animationSpec = HeadroomMotion.containerSpec(),
            label = "island-grow",
        )
    val fade by
        animateFloatAsState(
            targetValue = if (open) 1f else 0f,
            animationSpec = HeadroomMotion.effectsSpec(),
            label = "island-fade",
        )
    val contentAlpha by
        animateFloatAsState(
            targetValue = if (stage == IslandStage.Showing) 1f else 0f,
            animationSpec = HeadroomMotion.effectsSpec(),
            label = "island-content",
        )
    val hole = geometry.collapsed
    val pill = geometry.expanded
    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier =
                Modifier.offset {
                        val t = if (animate) grow.coerceAtLeast(0f) else 1f
                        IntOffset(
                            lerp(hole.left.toFloat(), pill.left.toFloat(), t).roundToInt(),
                            lerp(hole.top.toFloat(), pill.top.toFloat(), t).roundToInt(),
                        )
                    }
                    .layout { measurable, _ ->
                        val t = if (animate) grow.coerceAtLeast(0f) else 1f
                        val width = lerp(hole.width.toFloat(), pill.width.toFloat(), t).roundToInt()
                        val height =
                            lerp(hole.height.toFloat(), pill.height.toFloat(), t).roundToInt()
                        val placeable = measurable.measure(Constraints.fixed(width, height))
                        layout(width, height) { placeable.place(0, 0) }
                    }
                    .graphicsLayer { alpha = if (animate) 1f else fade }
                    // A radius of half the height, whatever the height is right now.
                    .clip(RoundedCornerShape(percent = HALF))
                    .background(Color.Black)
                    .testTag(ISLAND_SURFACE_TAG),
            contentAlignment = Alignment.Center,
        ) {
            val density = LocalDensity.current
            val full = with(density) { DpSize(pill.width.toDp(), pill.height.toDp()) }
            Box(modifier = Modifier.requiredSize(full).graphicsLayer { alpha = contentAlpha }) {
                IslandContent(provider = provider, message = message)
            }
        }
    }
}

/** The logo and the words, laid out for the full-size pill. */
@Composable
private fun IslandContent(provider: Provider, message: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxSize().padding(start = 6.dp, end = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
    ) {
        ProviderAvatar(provider = provider, size = LOGO_SIZE)
        Text(
            text = message,
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            // Short words sit next to the logo, in the middle; long words give way with an
            // ellipsis.
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

/** The black surface, for tests. */
internal const val ISLAND_SURFACE_TAG: String = "reset-island-surface"

private const val HALF = 50

private val LOGO_SIZE = 28.dp
