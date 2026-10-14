package dev.sebastiano.headroom.ui.components

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState

/**
 * Scrolls until the item with [key] is laid out, then brings it to the top of the list. The list
 * only knows the keys of the items it has laid out, so it looks one screen at a time: down first,
 * then up, for a list that was already scrolled past the item.
 */
internal suspend fun LazyListState.scrollToKey(key: Any, animate: Boolean) {
    for (direction in listOf(1f, -1f)) {
        while (true) {
            val item = layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }
            if (item != null) {
                if (animate) animateScrollToItem(item.index) else scrollToItem(item.index)
                return
            }
            if (if (direction > 0) !canScrollForward else !canScrollBackward) break
            val screen = direction * layoutInfo.viewportSize.height
            if (animate) animateScrollBy(screen) else scrollBy(screen)
        }
    }
}
