package dev.sebastiano.headroom.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.sebastiano.headroom.designsystem.animationsEnabled
import dev.sebastiano.headroom.ui.detail.DetailScreen
import dev.sebastiano.headroom.ui.home.DetailUiState

/**
 * The account detail beside the list, in the expanded window or in its own panel in Full Space. It
 * fades through to a newly selected account.
 */
@Composable
internal fun DetailPane(
    detail: DetailUiState,
    details: DetailActions,
    modifier: Modifier = Modifier,
) {
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val fast = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val animate = animationsEnabled()
    AnimatedContent(
        targetState = detail,
        contentKey = { it.account.id },
        transitionSpec = {
            if (!animate) fadeIn(fast) togetherWith fadeOut(fast)
            else
                (fadeIn(effects) + scaleIn(effects, initialScale = PANE_SCALE)) togetherWith
                    fadeOut(fast)
        },
        modifier = modifier,
        label = "detail pane",
    ) { shown ->
        DetailScreen(
            state = shown,
            formatter = details.formatter,
            onAlertChange = details.onAlertChange,
            onChartWindowChange = details.onChartWindowChange,
            resets = details.resets,
            resetHandlers = details.resetHandlers,
            onSignInAgain = details.onSignInAgain,
            revealResets = details.revealResetsOf == shown.account.id,
            onRevealResets = details.onRevealResets,
        )
    }
}

private const val PANE_SCALE = 0.98f
