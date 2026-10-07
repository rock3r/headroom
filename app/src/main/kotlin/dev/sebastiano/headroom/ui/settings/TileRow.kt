package dev.sebastiano.headroom.ui.settings

import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import dev.sebastiano.headroom.R
import dev.sebastiano.headroom.designsystem.HeadroomIcons
import dev.sebastiano.headroom.tile.TileAddResult
import dev.sebastiano.headroom.tile.TileSubtitleMode

/** Adds Headroom's tile to Quick Settings, and says how the last request went. */
@Composable
internal fun TileRow(
    status: TileAddResult?,
    onAdd: () -> Unit,
    count: Int,
    modifier: Modifier = Modifier,
) {
    SettingsRow(
        headline = stringResource(R.string.settings_tile_add),
        action = RowAction.Click(onAdd),
        index = 0,
        count = count,
        supporting =
            stringResource(
                when (status) {
                    null -> R.string.settings_tile_body
                    TileAddResult.Added -> R.string.settings_tile_added
                    TileAddResult.AlreadyAdded -> R.string.settings_tile_already_added
                    TileAddResult.NotAdded -> R.string.settings_tile_not_added
                    TileAddResult.Failed -> R.string.settings_tile_failed
                }
            ),
        leading = {
            Icon(
                painter = painterResource(R.drawable.ic_tile_headroom),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        trailing = {
            Icon(painter = painterResource(HeadroomIcons.Add), contentDescription = null)
        },
        modifier = modifier.testTag(ADD_TILE_TAG),
    )
}

fun tileSubtitleTag(mode: TileSubtitleMode): String = "tile-subtitle-${mode.name}"

/** One choice of what the tile's subtitle shows, picked like a radio button. */
@Composable
internal fun TileSubtitleRow(
    mode: TileSubtitleMode,
    selected: Boolean,
    onSelect: () -> Unit,
    index: Int,
    count: Int,
    modifier: Modifier = Modifier,
) {
    SettingsRow(
        headline =
            stringResource(
                when (mode) {
                    TileSubtitleMode.NextReset -> R.string.settings_tile_next_reset
                    TileSubtitleMode.TightestQuota -> R.string.settings_tile_tightest
                }
            ),
        action = RowAction.Select(selected, onSelect),
        index = index,
        count = count,
        supporting =
            stringResource(
                when (mode) {
                    TileSubtitleMode.NextReset -> R.string.settings_tile_next_reset_body
                    TileSubtitleMode.TightestQuota -> R.string.settings_tile_tightest_body
                }
            ),
        leading = { RadioButton(selected = selected, onClick = null) },
        modifier = modifier.testTag(tileSubtitleTag(mode)),
    )
}
