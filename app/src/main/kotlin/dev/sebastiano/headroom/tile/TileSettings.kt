package dev.sebastiano.headroom.tile

import android.content.Context
import androidx.core.content.edit
import dev.sebastiano.headroom.model.TileSubtitleMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What the tile's subtitle shows: the next reset by default, or the tightest quota. The user picks
 * it in Settings. It is kept in shared preferences because the tile service reads it outside the
 * app's UI. A stored value the app no longer knows reads as the default.
 */
class TileSettings(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val state =
        MutableStateFlow(
            prefs.getString(KEY_SUBTITLE, null)?.let { stored ->
                TileSubtitleMode.entries.firstOrNull { it.name == stored }
            } ?: TileSubtitleMode.NextReset
        )

    val subtitle: StateFlow<TileSubtitleMode> = state.asStateFlow()

    fun setSubtitle(mode: TileSubtitleMode) {
        prefs.edit { putString(KEY_SUBTITLE, mode.name) }
        state.value = mode
    }

    private companion object {
        const val PREFS = "tile"
        const val KEY_SUBTITLE = "subtitle"
    }
}
