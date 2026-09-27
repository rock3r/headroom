package dev.sebastiano.headroom.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SharedTransitionScope.OverlayClip
import androidx.compose.animation.SharedTransitionScope.SharedContentState
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier

/**
 * The scopes a card and the detail screen share for the container transform. It exists only in
 * single-pane layouts; in the two-pane layout the card and the detail are both on screen, and the
 * detail fades through instead.
 */
@Stable
class SharedElements(
    val transitionScope: SharedTransitionScope,
    val visibilityScope: AnimatedVisibilityScope,
    private val containerTransform: BoundsTransform,
    private val valueTransform: BoundsTransform,
    private val containerClip: OverlayClip,
) {
    /** The card or detail container: a container transform on the expressive spatial spring. */
    fun Modifier.sharedContainer(state: SharedContentState): Modifier =
        with(transitionScope) {
            this@sharedContainer.sharedBounds(
                sharedContentState = state,
                animatedVisibilityScope = visibilityScope,
                boundsTransform = containerTransform,
                clipInOverlayDuringTransition = containerClip,
            )
        }

    /** The avatar: moves with the container. */
    fun Modifier.sharedAvatar(state: SharedContentState): Modifier =
        with(transitionScope) {
            this@sharedAvatar.sharedElement(
                sharedContentState = state,
                animatedVisibilityScope = visibilityScope,
                boundsTransform = containerTransform,
            )
        }

    /** The percentage: a value read as data, so it moves without overshoot. */
    fun Modifier.sharedValue(state: SharedContentState): Modifier =
        with(transitionScope) {
            this@sharedValue.sharedBounds(
                sharedContentState = state,
                animatedVisibilityScope = visibilityScope,
                boundsTransform = valueTransform,
            )
        }

    companion object {
        fun cardKey(accountId: String): String = "card-$accountId"

        fun avatarKey(accountId: String): String = "avatar-$accountId"

        fun valueKey(accountId: String): String = "value-$accountId"
    }
}
