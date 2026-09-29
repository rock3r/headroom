package dev.sebastiano.headroom.ui.delights

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import dev.sebastiano.headroom.designsystem.animationsEnabled
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** The shimmer overlay, present only while the shimmer plays. */
const val SHIMMER_OVERLAY_TAG: String = "refresh-shimmer-overlay"

/** The confetti overlay, present only while confetti is in the air. */
const val CONFETTI_OVERLAY_TAG: String = "reset-confetti-overlay"

/** The anchor key of the next reset card, for confetti that bursts from it. */
const val NEXT_RESET_ANCHOR: String = "next-reset"

/**
 * Plays the delights over the whole app: the refresh shimmer and the reset confetti. Get it from
 * [LocalDelights] inside a [DelightsHost]. Each effect plays only while its switch is on and motion
 * is not reduced; otherwise asking for it does nothing.
 */
@Stable
class Delights internal constructor(private val scope: CoroutineScope) {
    internal var shimmerOn by mutableStateOf(false)
    internal var confettiOn by mutableStateOf(false)
    internal var density = 1f
    internal var host: LayoutCoordinates? = null

    /** From 0 to 1 while the shimmer sweeps across the screen. */
    internal val shimmer = Animatable(0f)
    internal var isShimmering by mutableStateOf(false)
        private set

    private var shimmerJob: Job? = null
    private var shimmerRuns = 0

    internal val bursts = mutableStateListOf<ConfettiBurst>()
    private val burstJobs = mutableListOf<Job>()
    private var burstCount = 0

    /** Where each anchor is, while it is on screen. */
    private val anchors = mutableStateMapOf<Any, LayoutCoordinates>()

    /** Sweeps the shimmer across the screen once. */
    fun playShimmer() {
        if (!shimmerOn) return
        shimmerJob?.cancel()
        isShimmering = true
        // A shimmer that replaces a running one must not be hidden when the old one stops.
        val run = ++shimmerRuns
        shimmerJob = scope.launch {
            try {
                shimmer.snapTo(0f)
                // The shader eases the sweep itself, so time moves evenly here.
                shimmer.animateTo(1f, tween(SHIMMER_MILLIS, easing = LinearEasing))
            } finally {
                if (run == shimmerRuns) isShimmering = false
            }
        }
    }

    /**
     * Bursts confetti in [colors] from the centre of the first of [anchors] that is on screen, and
     * returns true. With confetti off it plays nothing and also returns true: the burst is done
     * with. It returns false, and plays nothing, when none of the anchors is on screen yet.
     */
    fun burstFrom(anchors: List<Any>, colors: List<Color>): Boolean {
        if (!confettiOn) return true
        val host = host?.takeIf { it.isAttached } ?: return false
        val anchor =
            anchors.firstNotNullOfOrNull { key -> this.anchors[key]?.takeIf { it.isAttached } }
                ?: return false
        val centre = Offset(anchor.size.width / 2f, anchor.size.height / 2f)
        val burst =
            ConfettiBurst(
                origin = host.localPositionOf(anchor, centre),
                colors = colors,
                density = density,
                random = Random(burstCount++),
            )
        bursts += burst
        burstJobs += scope.launch {
            try {
                burst.progress.animateTo(1f, tween(CONFETTI_MILLIS, easing = LinearEasing))
            } finally {
                bursts -= burst
            }
        }
        burstJobs.removeAll { it.isCompleted }
        return true
    }

    /** Waits until one of [anchors] is on screen, or confetti is off, then bursts from it. */
    suspend fun burstWhenShown(anchors: List<Any>, colors: List<Color>) {
        snapshotFlow { !confettiOn || anchors.any(::hasAnchor) }.first { it }
        burstFrom(anchors, colors)
    }

    /** True when the shimmer would play: it is switched on and motion is not reduced. */
    val canShimmer: Boolean
        get() = shimmerOn

    /** True when confetti would burst: it is switched on and motion is not reduced. */
    val canBurst: Boolean
        get() = confettiOn

    /** True while the node marked with [Modifier.delightAnchor] and [key] is on screen. */
    fun hasAnchor(key: Any): Boolean = key in anchors

    internal fun setAnchor(key: Any, coordinates: LayoutCoordinates) {
        if (anchors[key] !== coordinates) anchors[key] = coordinates
    }

    internal fun removeAnchor(key: Any, coordinates: LayoutCoordinates) {
        if (anchors[key] === coordinates) anchors.remove(key)
    }

    /** Stops whatever plays of an effect that has just been turned off. */
    internal fun stopWhatIsOff() {
        if (!shimmerOn) shimmerJob?.cancel()
        if (!confettiOn) {
            burstJobs.forEach { it.cancel() }
            burstJobs.clear()
        }
    }

    private companion object {
        const val SHIMMER_MILLIS = 2_400
    }
}

/** How long a confetti burst stays on screen. */
internal const val CONFETTI_MILLIS = 2_000

/** The [Delights] of the enclosing [DelightsHost], or null outside one. */
// Like LocalThemeReveal: the host sits at the activity's root, and the screens that trigger an
// effect sit deep inside the app. A parameter would have to pass through every screen in between.
@Suppress("CompositionLocalAllowlist")
val LocalDelights = staticCompositionLocalOf<Delights?> { null }

/**
 * Draws [content] and, on top of it, the delights while they play. [refreshShimmer] and
 * [resetConfetti] are the user's switches; with reduced motion neither plays. The effects draw
 * only: every touch goes to [content] underneath.
 */
@Composable
fun DelightsHost(
    refreshShimmer: Boolean,
    resetConfetti: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val delights = remember(scope) { Delights(scope) }
    val animate = animationsEnabled()
    val density = LocalDensity.current.density
    SideEffect {
        delights.shimmerOn = refreshShimmer && animate
        delights.confettiOn = resetConfetti && animate
        delights.density = density
        delights.stopWhatIsOff()
    }
    Box(modifier = modifier.onPlaced { delights.host = it }) {
        CompositionLocalProvider(LocalDelights provides delights) { content() }
        if (delights.isShimmering) {
            RefreshShimmer(
                progress = { delights.shimmer.value },
                modifier = Modifier.matchParentSize().testTag(SHIMMER_OVERLAY_TAG),
            )
        }
        if (delights.bursts.isNotEmpty()) {
            Confetti(
                bursts = delights.bursts,
                modifier = Modifier.matchParentSize().testTag(CONFETTI_OVERLAY_TAG),
            )
        }
    }
}

/**
 * Marks this node as a place confetti can burst from, under [key]: an account's id, or
 * [NEXT_RESET_ANCHOR]. It does nothing outside a [DelightsHost].
 */
fun Modifier.delightAnchor(key: Any): Modifier = this then DelightAnchorElement(key)

private data class DelightAnchorElement(val key: Any) : ModifierNodeElement<DelightAnchorNode>() {
    override fun create(): DelightAnchorNode = DelightAnchorNode(key)

    override fun update(node: DelightAnchorNode) {
        node.update(key)
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "delightAnchor"
        properties["key"] = key
    }
}

private class DelightAnchorNode(private var key: Any) :
    Modifier.Node(), GlobalPositionAwareModifierNode, CompositionLocalConsumerModifierNode {
    private var registered: Pair<Delights, LayoutCoordinates>? = null

    fun update(newKey: Any) {
        if (newKey == key) return
        forget()
        key = newKey
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        val delights = currentValueOf(LocalDelights) ?: return
        registered = delights to coordinates
        delights.setAnchor(key, coordinates)
    }

    override fun onDetach() {
        forget()
    }

    private fun forget() {
        registered?.let { (delights, coordinates) -> delights.removeAnchor(key, coordinates) }
        registered = null
    }
}
