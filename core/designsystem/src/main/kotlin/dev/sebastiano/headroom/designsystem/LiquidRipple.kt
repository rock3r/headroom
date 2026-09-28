package dev.sebastiano.headroom.designsystem

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * A drop of water on the surface: a tap sends one or two rings out from the touch point, which
 * refract the content under them as they pass, catch a faint [highlight] on their crest, and fade
 * out as they grow. [LiquidRippleState] holds the ripple and its AGSL shader.
 *
 * It only watches taps and never consumes them, so it adds nothing to what a tap already does. A
 * tap that a child handles, such as a button's click, does not ripple, and neither does a drag or a
 * scroll. It is pure delight: it has no meaning that a static screen needs to carry, so with
 * [enabled] false (animations off, or reduced motion) there is simply no ripple. At rest no effect
 * is applied at all.
 */
fun Modifier.liquidRipple(
    state: LiquidRippleState,
    enabled: Boolean = true,
    highlight: Color = Color.White,
): Modifier {
    if (!enabled) return this
    return this.graphicsLayer {
            renderEffect =
                if (state.isActive) {
                    state.renderEffect(size, this, highlight).asComposeRenderEffect()
                } else {
                    null
                }
        }
        .pointerInput(state) {
            coroutineScope { detectUnhandledTaps { position -> launch { state.play(position) } } }
        }
        .semantics { liquidRippleActive = state.isActive }
}

/** Exposes whether a [liquidRipple] is spreading, to tests and tools. */
val LiquidRippleActiveKey: SemanticsPropertyKey<Boolean> =
    SemanticsPropertyKey("LiquidRippleActive")

var SemanticsPropertyReceiver.liquidRippleActive: Boolean by LiquidRippleActiveKey

/**
 * Calls [onTap] for each tap that nothing else handled. It reads events in the final pass, after
 * children and scrolling parents had their turn: a child that handles the press consumes the down,
 * and a scroll consumes the moves. A pointer that travels past the touch slop is not a tap either.
 */
private suspend fun PointerInputScope.detectUnhandledTaps(onTap: (Offset) -> Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Final)
        if (down.isConsumed) return@awaitEachGesture
        val slop = viewConfiguration.touchSlop
        while (true) {
            val change =
                awaitPointerEvent(PointerEventPass.Final).changes.firstOrNull { it.id == down.id }
                    ?: return@awaitEachGesture
            if (change.changedToUpIgnoreConsumed()) {
                if (!change.isConsumed) onTap(change.position)
                return@awaitEachGesture
            }
            val travelled = (change.position - down.position).getDistance()
            if (change.isConsumed || travelled > slop) return@awaitEachGesture
        }
    }
}
