package dev.sebastiano.headroom.ui.detail

import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos

/**
 * Scrolls the Resets card that [requester] is attached to into view once, after it was laid out,
 * while [reveal] asks for it. [onReveal] is called once it is in view.
 */
@Composable
internal fun RevealResetsEffect(
    reveal: Boolean,
    requester: BringIntoViewRequester,
    onReveal: () -> Unit,
) {
    val currentOnReveal by rememberUpdatedState(onReveal)
    LaunchedEffect(reveal) {
        if (!reveal) return@LaunchedEffect
        // The card has no position until its first layout, and a request before then does nothing.
        withFrameNanos {}
        requester.bringIntoView()
        currentOnReveal()
    }
}
