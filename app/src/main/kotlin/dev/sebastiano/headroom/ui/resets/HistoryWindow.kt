package dev.sebastiano.headroom.ui.resets

import androidx.compose.runtime.Immutable

/** One window in the history chart. [title] names the account and the window. */
@Immutable
internal data class HistoryWindow(
    val key: String,
    val title: String,
    /** Used percent at each past reset, oldest first. */
    val past: List<Double>,
    /** Used percent of the window that is running now. */
    val current: Double,
)
