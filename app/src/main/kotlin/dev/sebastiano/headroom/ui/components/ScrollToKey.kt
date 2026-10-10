package dev.sebastiano.headroom.ui.components

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState

/**
 * Scrolls down until the item with [key] is laid out, then brings it to the top of the list. The
 * list only knows the keys of the items it has laid out, so it looks one screen at a time.
 */
internal suspend fun LazyListState.scrollToKey(key: Any, animate: Boolean) {
    while (true) {
        val item = layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }
        if (item != null) {
            if (animate) animateScrollToItem(item.index) else scrollToItem(item.index)
            return
        }
        if (!canScrollForward) return
        val screen = layoutInfo.viewportSize.height.toFloat()
        if (animate) animateScrollBy(screen) else scrollBy(screen)
    }
}
