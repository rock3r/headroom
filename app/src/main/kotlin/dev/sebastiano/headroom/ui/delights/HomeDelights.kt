package dev.sebastiano.headroom.ui.delights

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.Color
import dev.sebastiano.headroom.designsystem.providerColors
import dev.sebastiano.headroom.model.FreshDataTrigger
import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.ResetBurst
import dev.sebastiano.headroom.ui.home.HomeUiState

/**
 * Plays the delights that [home] asks for, through the enclosing [DelightsHost]: the shimmer when a
 * refresh brings new data (see [FreshDataTrigger]), and confetti for each of the resets in
 * [HomeUiState.resetBursts]. Confetti bursts from the next reset card when it was counting down to
 * that reset, and otherwise from the account's card; it waits until that card is on screen.
 * [onBurstFinish] runs once a burst has played, or was skipped because confetti is off.
 */
@Composable
fun HomeDelights(home: HomeUiState, onBurstFinish: (String) -> Unit) {
    val delights = LocalDelights.current
    val state by rememberUpdatedState(home)
    val shown by rememberUpdatedState(onBurstFinish)
    if (delights == null) {
        // Nothing can play without a host, so no burst is left waiting.
        val bursts = home.resetBursts
        SideEffect { bursts.forEach { shown(it.accountId) } }
        return
    }
    LaunchedEffect(delights) {
        val trigger = FreshDataTrigger()
        snapshotFlow { state.isRefreshing to state.lastSyncedAt }
            .collect { (refreshing, syncedAt) ->
                if (trigger.update(refreshing, syncedAt)) delights.playShimmer()
            }
    }
    home.resetBursts.forEach { burst ->
        key(burst) {
            val provider =
                home.accountsInYourOrder.firstOrNull { it.id == burst.accountId }?.provider
            val colors = confettiColors(provider)
            LaunchedEffect(delights, burst) {
                // A surface in front, such as the redeem sheet, already celebrated this reset.
                if (!delights.takeClaim(burst.accountId, burst.windowId)) {
                    delights.burstWhenShown(burst.anchors, colors)
                }
                shown(burst.accountId)
            }
        }
    }
}

private val ResetBurst.anchors: List<Any>
    get() = listOfNotNull(NEXT_RESET_ANCHOR.takeIf { fromNextReset }, accountId)

/** The provider's colour, twice as often as each theme colour, then the theme's colours. */
@Composable
internal fun confettiColors(provider: Provider?): List<Color> {
    val scheme = MaterialTheme.colorScheme
    val accent = provider?.let { providerColors(it).accent }
    return listOfNotNull(accent, accent, scheme.primary, scheme.secondary, scheme.tertiary)
}
