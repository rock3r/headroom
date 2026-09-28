package dev.sebastiano.headroom.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import dev.sebastiano.headroom.designsystem.HeadroomMotion
import kotlin.math.hypot
import kotlin.math.max
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** How the old frame gives way to the new theme. */
enum class RevealStyle {
    /** A circle of the new theme grows from where the user tapped. */
    Circle,
    /** The old frame fades out, for reduced motion. */
    Fade,
}

/**
 * Changes the theme behind a still of the old frame, then uncovers the new theme. Get it from
 * [LocalThemeReveal] inside a [ThemeRevealHost].
 */
@Stable
class ThemeReveal
internal constructor(
    private val scope: CoroutineScope,
    private val layer: GraphicsLayer,
    private val themeKey: () -> Any,
) {
    private var still by mutableStateOf<ImageBitmap?>(null)
    private var center by mutableStateOf(Offset.Zero)
    private val progress = Animatable(0f)
    private var job: Job? = null

    /** True while the old frame is on screen. */
    val isRunning: Boolean
        get() = still != null

    var style: RevealStyle by mutableStateOf(RevealStyle.Circle)
        private set

    /**
     * Takes a still of the screen, runs [change], and once the new theme is on screen uncovers it
     * from [center] (in the host's coordinates), or fades the still when [animate] is false. A
     * change that never reaches the screen drops the still after a short wait.
     */
    fun start(center: Offset, animate: Boolean, change: () -> Unit) {
        job?.cancel()
        job = scope.launch {
            val before = themeKey()
            val image = runCatching { layer.toImageBitmap() }.getOrNull()
            if (image == null) {
                change()
                return@launch
            }
            this@ThemeReveal.center = center
            style = if (animate) RevealStyle.Circle else RevealStyle.Fade
            progress.snapTo(0f)
            still = image
            try {
                change()
                val shown =
                    withTimeoutOrNull(THEME_WAIT_MILLIS) {
                        snapshotFlow { themeKey() }.first { it != before }
                    }
                if (shown != null) {
                    progress.animateTo(
                        1f,
                        if (animate) HeadroomMotion.revealSpec() else tween(FADE_MILLIS),
                    )
                }
            } finally {
                still = null
            }
        }
    }

    internal fun DrawScope.drawStill() {
        val image = still ?: return
        when (style) {
            RevealStyle.Fade -> drawImage(image, alpha = 1f - progress.value)
            RevealStyle.Circle -> {
                // The circle reaches the farthest corner when the reveal ends.
                val farthest =
                    max(
                        max(hypot(center.x, center.y), hypot(size.width - center.x, center.y)),
                        max(
                            hypot(center.x, size.height - center.y),
                            hypot(size.width - center.x, size.height - center.y),
                        ),
                    )
                val hole = Path().apply { addOval(Rect(center, farthest * progress.value)) }
                clipPath(hole, ClipOp.Difference) { drawImage(image) }
            }
        }
    }

    private companion object {
        const val THEME_WAIT_MILLIS = 1_000L
        const val FADE_MILLIS = 200
    }
}

/** The [ThemeReveal] of the enclosing [ThemeRevealHost], or null outside one. */
// The host sits above the theme at the activity's root, like the theme itself, and only the
// appearance pickers deep in Settings read it. A parameter would have to pass through every screen
// in between.
@Suppress("CompositionLocalAllowlist")
val LocalThemeReveal = staticCompositionLocalOf<ThemeReveal?> { null }

/**
 * Draws [content] and, during a theme change, the still of the old frame on top. [themeKey] is
 * whatever changes when the theme does, such as the palette and the light or dark choice.
 */
@Composable
fun ThemeRevealHost(themeKey: Any, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val scope = rememberCoroutineScope()
    val layer = rememberGraphicsLayer()
    val key by rememberUpdatedState(themeKey)
    val reveal = remember(scope, layer) { ThemeReveal(scope, layer) { key } }
    CompositionLocalProvider(LocalThemeReveal provides reveal) {
        Box(
            modifier =
                modifier.drawWithContent {
                    // Every frame is recorded, so a still of it is ready the moment a change
                    // starts.
                    layer.record { this@drawWithContent.drawContent() }
                    drawLayer(layer)
                    with(reveal) { drawStill() }
                }
        ) {
            content()
        }
    }
}
