package dev.sebastiano.headroom.ui.settings

import androidx.compose.runtime.Immutable

/** What tapping a [SettingsRow] does. It also sets the row's role for screen readers. */
@Immutable
internal sealed interface RowAction {
    /** Opens or starts something. */
    data class Click(val onClick: () -> Unit) : RowAction

    /** One option of a group, like a radio button. */
    data class Select(val selected: Boolean, val onSelect: () -> Unit) : RowAction

    /** Turns something on or off, like a switch. */
    data class Toggle(val checked: Boolean, val onCheckedChange: (Boolean) -> Unit) : RowAction
}
