package dev.sebastiano.headroom.island

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

/**
 * The touches of the overlay pill: a tap calls [onTap], and a swipe up calls [onSwipeUp]. A swipe
 * down does nothing.
 *
 * The pointer input is keyed on `Unit` on purpose. A new pair of lambdas on each recomposition
 * would restart the input, and cancel a touch that is in progress. The callers pass functions of a
 * window that lives as long as the touch does.
 */
internal fun Modifier.islandTouch(onTap: () -> Unit, onSwipeUp: () -> Unit): Modifier =
    this.pointerInput(Unit) { detectTapGestures(onTap = { onTap() }) }
        .pointerInput(Unit) {
            var travelled = 0f
            detectVerticalDragGestures(
                onDragStart = { travelled = 0f },
                onVerticalDrag = { _, amount -> travelled += amount },
                onDragEnd = { if (travelled < 0f) onSwipeUp() },
                onDragCancel = {},
            )
        }
