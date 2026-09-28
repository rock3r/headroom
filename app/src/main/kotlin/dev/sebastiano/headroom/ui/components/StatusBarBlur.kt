package dev.sebastiano.headroom.ui.components

import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import dev.sebastiano.headroom.designsystem.HeadroomMotion

const val STATUS_BAR_BLUR_TAG: String = "status-bar-blur"

/**
 * Scrolling [content] that draws edge to edge, with a frosted blur behind the status bar so the
 * clock and icons stay readable over it.
 *
 * The blur fades in once [scrollState] has scrolled content under the status bar, and out again at
 * the top of the list, where nothing sits under the status bar. It covers the status bar and a
 * short margin below it, where it fades out progressively. It covers only the part of the status
 * bar that overlaps this content, so where none does, for example in the detail pane of the
 * two-pane layout, it draws nothing.
 */
@Composable
fun StatusBarBlurBox(
    scrollState: ScrollableState,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val hazeState = rememberHazeState()
    val density = LocalDensity.current
    // The screens keep this inset clear at rest; content only reaches it when it scrolls.
    val statusBar = WindowInsets.safeDrawing.getTop(density)
    var topInWindow by remember { mutableFloatStateOf(0f) }
    val visibility by
        animateFloatAsState(
            targetValue = if (scrollState.canScrollBackward) 1f else 0f,
            animationSpec = HeadroomMotion.effectsSpec(),
            label = "status bar blur",
        )
    Box(modifier = modifier.onPlaced { topInWindow = it.positionInWindow().y }) {
        // How much of the status bar overlaps this content, while the blur shows at all.
        val covered = if (visibility > 0f) statusBar - topInWindow else 0f
        // Haze joins in only while the blur shows. At rest the content draws as if Haze were not
        // there: no capture, and no Haze nodes in the lookahead layout of the list and detail
        // panes.
        val source = if (covered > 0f) Modifier.hazeSource(hazeState) else Modifier
        Box(modifier = Modifier.fillMaxSize().then(source), content = content)
        if (covered > 0f) {
            val surface = MaterialTheme.colorScheme.surface
            val style =
                remember(surface, covered, visibility) {
                    statusBarBlurStyle(surface, covered, visibility)
                }
            Spacer(
                modifier =
                    Modifier.fillMaxWidth()
                        .height(with(density) { covered.toDp() } + FadeMargin)
                        .testTag(STATUS_BAR_BLUR_TAG)
                        .hazeBlur(input = HazeInput.Sources(hazeState), style = style)
            )
        }
    }
}

/**
 * A frosted surface: the blurred content on the theme's surface colour, so it works in light and
 * dark themes and with dynamic colour. It is at full strength behind the status bar, down to
 * [fadeStart] pixels, and fades out below it.
 */
private fun statusBarBlurStyle(surface: Color, fadeStart: Float, visibility: Float): HazeBlurStyle =
    HazeBlurStyle {
        // Opaque behind the blur, so the sharp content under it does not show through.
        backgroundColor(surface)
        colorEffects(listOf(HazeColorEffect.tint(surface.copy(alpha = TINT_ALPHA))))
        // Where blur is unavailable, a stronger scrim alone keeps the status bar readable.
        fallbackColorEffect(HazeColorEffect.tint(surface.copy(alpha = FALLBACK_ALPHA)))
        progressive(
            HazeProgressive.verticalGradient(
                easing = EaseInOut,
                startY = fadeStart,
                startIntensity = 1f,
                endIntensity = 0f,
            )
        )
        alpha(visibility)
    }

/** How far below the status bar the blur reaches while it fades out. */
private val FadeMargin = 24.dp
private const val TINT_ALPHA = 0.7f
private const val FALLBACK_ALPHA = 0.9f
