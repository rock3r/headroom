package dev.sebastiano.headroom.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/**
 * One row of a settings group, number [index] of [count]: the look of Material's segmented list
 * item, without its animations. `SegmentedListItem` sets up seven animations, a shape animation and
 * interaction tracking for every row, and that was the biggest cost of composing the Settings list
 * (see docs/TRACING.md). These rows never animate, so a plain row draws the same for much less. A
 * selected or checked row takes Material's selected colours and shape.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SettingsRow(
    headline: String,
    action: RowAction,
    index: Int,
    count: Int,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    below: (@Composable () -> Unit)? = null,
) {
    val colors =
        ListItemDefaults.segmentedColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    val shapes = ListItemDefaults.segmentedShapes(index = index, count = count)
    val highlighted =
        when (action) {
            is RowAction.Select -> action.selected
            is RowAction.Toggle -> action.checked
            is RowAction.Click -> false
        }
    val shape = if (highlighted) shapes.selectedShape else shapes.shape
    val container = if (highlighted) colors.selectedContainerColor else colors.containerColor
    val content = if (highlighted) colors.selectedContentColor else colors.contentColor
    val supportingColor =
        if (highlighted) colors.selectedSupportingContentColor else colors.supportingContentColor
    CompositionLocalProvider(LocalContentColor provides content) {
        Row(
            modifier =
                modifier
                    .fillMaxWidth()
                    .heightIn(min = if (supporting != null) TWO_LINE_HEIGHT else ONE_LINE_HEIGHT)
                    .clip(shape)
                    .background(container)
                    .rowAction(action)
                    .padding(horizontal = SIDE_PADDING, vertical = VERTICAL_PADDING),
            // Centred, and top-aligned once a row is tall, as Material's list items are.
            verticalAlignment = ListItemDefaults.verticalAlignment(),
            horizontalArrangement = Arrangement.spacedBy(CONTENT_GAP),
        ) {
            leading?.invoke()
            Column(modifier = Modifier.weight(1f)) {
                Text(text = headline, style = MaterialTheme.typography.bodyLarge)
                if (supporting != null) {
                    Text(
                        text = supporting,
                        style = MaterialTheme.typography.bodyMedium,
                        color = supportingColor,
                    )
                }
                below?.invoke()
            }
            trailing?.invoke()
        }
    }
}

/** Makes the row react to taps as [action] says, with the matching role. */
private fun Modifier.rowAction(action: RowAction): Modifier =
    when (action) {
        is RowAction.Click -> clickable(role = Role.Button, onClick = action.onClick)
        is RowAction.Select ->
            selectable(
                selected = action.selected,
                role = Role.RadioButton,
                onClick = action.onSelect,
            )
        is RowAction.Toggle ->
            toggleable(
                value = action.checked,
                role = Role.Switch,
                onValueChange = action.onCheckedChange,
            )
    }

// Material's list item tokens: a row is at least 56 dp high, or 72 dp with supporting text, with
// 16 dp at the sides, 10 dp above and below, and 12 dp between the leading content, the text and
// the trailing content.
private val ONE_LINE_HEIGHT = 56.dp
private val TWO_LINE_HEIGHT = 72.dp
private val SIDE_PADDING = 16.dp
private val VERTICAL_PADDING = 10.dp
private val CONTENT_GAP = 12.dp
