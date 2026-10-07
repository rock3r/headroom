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
 *
 * [origin] tells apart the places one account can be opened from on the same screen, such as the
 * Resets tab's rows: the detail and the row it was opened from share keys with the same origin, so
 * the other rows of that account stay out of the transform.
 */
@Stable
class SharedElements(
    val transitionScope: SharedTransitionScope,
    val visibilityScope: AnimatedVisibilityScope,
    private val containerTransform: BoundsTransform,
    private val valueTransform: BoundsTransform,
    private val containerClip: OverlayClip,
    val origin: String = "",
) {
    /** These scopes, for elements opened from [origin]. */
    fun from(origin: String): SharedElements =
        SharedElements(
            transitionScope,
            visibilityScope,
            containerTransform,
            valueTransform,
            containerClip,
            origin,
        )

    /** The key of [accountId]'s container, from this [origin]. */
    fun card(accountId: String): String = cardKey(accountId) + originSuffix

    /** The key of [accountId]'s avatar, from this [origin]. */
    fun avatar(accountId: String): String = avatarKey(accountId) + originSuffix

    /** The key of [accountId]'s percentage, from this [origin]. */
    fun value(accountId: String): String = valueKey(accountId) + originSuffix

    private val originSuffix: String
        get() = if (origin.isEmpty()) "" else "@$origin"

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
