package dev.sebastiano.headroom.island

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.sebastiano.headroom.R
import dev.sebastiano.headroom.designsystem.HeadroomMotion
import dev.sebastiano.headroom.designsystem.ProviderAvatar
import dev.sebastiano.headroom.designsystem.animationsEnabled
import dev.sebastiano.headroom.model.Provider
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/** The steps of one island: it grows, shows what reset, hides it, and shrinks. */
internal enum class IslandStage {
    /** The size of the camera hole, and nothing to see. */
    Collapsed,
    /** The pill is growing, or has grown and shows nothing yet. */
    Growing,
    /** The pill is full size: the logo shows, the ring fills up, then "reset" appears. */
    Showing,
    /** The content has faded out and the pill is about to shrink. */
    Hiding,
}

/** How long each step of [ResetIslandHost] takes, in milliseconds. */
internal object IslandTiming {
    /** The grow spring has mostly settled by then. */
    const val GROW = 420L

    /** How long the full island stays: the ring fills, then "reset" shows. */
    const val HOLD = 3_200L

    /** The ring fills up during the first part of the hold. */
    const val RING_FILL = 900L

    /** The content fades out before the pill shrinks. */
    const val FADE_OUT = 180L

    /** The shrink spring has settled by then, so the window can go. */
    const val SHRINK = 420L
}

/**
 * Plays one island for [request]: grow, hold for a few seconds, shrink. It calls [onFinish] when
 * the pill is back in the cutout and the window can be removed. A new request restarts the hold,
 * and the pill stays as it is.
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
    ResetIslandSurface(geometry = geometry, stage = stage, request = request, modifier = modifier)
}

/**
 * The island itself: one black surface whose corner radius is half its height. It grows out of the
 * camera hole into a capsule around it, then sideways into the full pill, and shrinks back the same
 * way. Left of the hole is the logo of the account that reset, with its badge letter; right of it,
 * a ring that fills up to full, then the word "reset". With motion reduced the pill does not grow:
 * it fades in at full size, the ring is already full, and it fades out.
 *
 * [geometry] is in pixels, relative to the window that this fills.
 */
@Composable
internal fun ResetIslandSurface(
    geometry: IslandGeometry,
    stage: IslandStage,
    request: IslandRequest,
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
    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier =
                Modifier.islandBounds(geometry) { if (animate) grow else 1f }
                    .graphicsLayer { alpha = if (animate) 1f else fade }
                    // A radius of half the height, whatever the height is right now.
                    .clip(RoundedCornerShape(percent = HALF))
                    .background(Color.Black)
                    .semantics { contentDescription = request.description }
                    .testTag(ISLAND_SURFACE_TAG),
            contentAlignment = Alignment.Center,
        ) {
            val density = LocalDensity.current
            val pill = geometry.expanded
            val full = with(density) { DpSize(pill.width.toDp(), pill.height.toDp()) }
            Box(modifier = Modifier.requiredSize(full).graphicsLayer { alpha = contentAlpha }) {
                IslandContent(
                    request = request,
                    geometry = geometry,
                    filling = stage == IslandStage.Showing,
                )
            }
        }
    }
}

/**
 * Places and sizes the surface for the grow [progress]: from the hole to the capsule during the
 * first part, then from the capsule to the full pill. A spring that passes 1 stretches the pill a
 * little past its size, which is the overshoot the window has room for.
 */
private fun Modifier.islandBounds(geometry: IslandGeometry, progress: () -> Float): Modifier =
    offset {
        val rect = geometry.at(progress())
        IntOffset(rect.left.roundToInt(), rect.top.roundToInt())
    }
    .layout { measurable, _ ->
        val rect = geometry.at(progress())
        val width = rect.width.roundToInt().coerceAtLeast(0)
        val height = rect.height.roundToInt().coerceAtLeast(0)
        val placeable = measurable.measure(Constraints.fixed(width, height))
        layout(width, height) { placeable.place(0, 0) }
    }

/** The island's bounds at grow [progress], from 0 (the hole) to 1 (the full pill). */
private fun IslandGeometry.at(progress: Float): Rect {
    val t = progress.coerceAtLeast(0f)
    return if (t < CAPSULE_SHARE) {
        lerp(collapsed.toRect(), capsule.toRect(), t / CAPSULE_SHARE)
    } else {
        lerp(capsule.toRect(), expanded.toRect(), (t - CAPSULE_SHARE) / (1 - CAPSULE_SHARE))
    }
}

private fun PxRect.toRect() = Rect(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())

/**
 * What the full pill shows, laid out around the hole: the logo hugs the left end, and the ring and
 * "reset" hug the right one. The ring fills up while [filling].
 */
@Composable
private fun IslandContent(
    request: IslandRequest,
    geometry: IslandGeometry,
    filling: Boolean,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val pill = geometry.expanded
    val hole = geometry.collapsed
    // Content sits as far in from each end as it sits from the top, so it follows the rounded end.
    val inset = with(density) { ((pill.height.toDp() - LOGO_SIZE) / 2).coerceAtLeast(MIN_INSET) }
    val leftWidth = with(density) { (hole.left - pill.left).toDp() }
    val rightWidth = with(density) { (pill.right - hole.right).toDp() }
    Row(modifier = modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.width(leftWidth).padding(start = inset),
            contentAlignment = Alignment.CenterStart,
        ) {
            BadgedLogo(provider = request.provider, badge = request.badge)
        }
        Spacer(modifier = Modifier.weight(1f))
        Row(
            modifier = Modifier.width(rightWidth).padding(end = inset),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
        ) {
            RefillRing(from = request.leftBefore, filling = filling)
            ResetLabel(visible = filling)
        }
    }
}

/** The provider's logo, with the account's [badge] letter on its corner when there is one. */
@Composable
private fun BadgedLogo(provider: Provider, badge: String?, modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(LOGO_SIZE)) {
        ProviderAvatar(provider = provider, size = LOGO_SIZE)
        if (badge != null) {
            Box(
                modifier =
                    Modifier.align(Alignment.BottomEnd)
                        .offset(x = 3.dp, y = 3.dp)
                        .size(BADGE_SIZE)
                        .clip(CircleShape)
                        .background(Color.White),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = badge,
                    color = Color.Black,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 8.sp,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * A ring of the quota left: it starts at [from] and, once [filling], fills up to full, like a
 * battery charging. With motion reduced it is full at once.
 */
@Composable
private fun RefillRing(from: Float, filling: Boolean, modifier: Modifier = Modifier) {
    val animate = animationsEnabled()
    val level = remember { Animatable(if (animate) from else 1f) }
    LaunchedEffect(filling, animate) {
        if (!filling) return@LaunchedEffect
        if (animate) {
            level.animateTo(1f, tween(IslandTiming.RING_FILL.toInt(), easing = FastOutSlowInEasing))
        } else {
            level.snapTo(1f)
        }
    }
    Canvas(modifier = modifier.size(RING_SIZE)) {
        val stroke = RING_STROKE.toPx()
        val inset = stroke / 2
        val arcSize = Size(size.width - stroke, size.height - stroke)
        drawArc(
            color = Color.White.copy(alpha = TRACK_ALPHA),
            startAngle = 0f,
            sweepAngle = FULL_TURN,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(width = stroke),
        )
        drawArc(
            color = RefillGreen,
            startAngle = -QUARTER_TURN,
            sweepAngle = FULL_TURN * level.value,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
    }
}

/** The word "reset", which slides in once the ring is full. */
@Composable
private fun ResetLabel(visible: Boolean, modifier: Modifier = Modifier) {
    val animate = animationsEnabled()
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(visible) {
        if (visible) {
            if (animate) delay(IslandTiming.RING_FILL)
            shown = true
        }
    }
    val appear by
        animateFloatAsState(
            targetValue = if (shown) 1f else 0f,
            animationSpec = HeadroomMotion.effectsSpec(),
            label = "island-label",
        )
    // A fixed size, like the logo and the ring: the pill has no room to grow with the font scale.
    // A screen reader reads the island's description instead.
    val fixedSize = with(LocalDensity.current) { LABEL_SIZE.toSp() }
    Text(
        text = stringResource(R.string.reset_island_reset),
        color = Color.White,
        style =
            MaterialTheme.typography.labelMedium.copy(fontSize = fixedSize, lineHeight = fixedSize),
        maxLines = 1,
        softWrap = false,
        modifier =
            modifier.padding(start = 5.dp).graphicsLayer {
                alpha = appear
                translationX = (1 - appear) * LABEL_SLIDE.toPx()
            },
    )
}

/** The black surface, for tests. */
internal const val ISLAND_SURFACE_TAG: String = "reset-island-surface"

private const val HALF = 50

/** How much of the grow takes the hole to the capsule, before the pill spreads sideways. */
private const val CAPSULE_SHARE = 0.35f

private const val FULL_TURN = 360f
private const val QUARTER_TURN = 90f
private const val TRACK_ALPHA = 0.22f

private val LOGO_SIZE = 22.dp
private val BADGE_SIZE = 11.dp
private val RING_SIZE = 18.dp
private val RING_STROKE = 2.5.dp
private val LABEL_SLIDE = (-6).dp
private val LABEL_SIZE = 12.dp

/** Content never gets closer than this to a rounded end. */
private val MIN_INSET = 10.dp

/** A charging green, bright enough on black. */
private val RefillGreen = Color(0xFF4ADE80)
