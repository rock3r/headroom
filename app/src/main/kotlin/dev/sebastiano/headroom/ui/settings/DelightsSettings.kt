package dev.sebastiano.headroom.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import dev.sebastiano.headroom.R

const val REFRESH_SHIMMER_TAG: String = "refresh-shimmer"

const val RESET_CONFETTI_TAG: String = "reset-confetti"

/** The two delights, each with its switch: the refresh shimmer and the reset confetti. */
@Composable
internal fun DelightsList(
    refreshShimmer: Boolean,
    resetConfetti: Boolean,
    onRefreshShimmerChange: (Boolean) -> Unit,
    onResetConfettiChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
        DelightRow(
            title = R.string.settings_refresh_shimmer,
            body = R.string.settings_refresh_shimmer_body,
            checked = refreshShimmer,
            onChange = onRefreshShimmerChange,
            index = 0,
            modifier = Modifier.testTag(REFRESH_SHIMMER_TAG),
        )
        DelightRow(
            title = R.string.settings_reset_confetti,
            body = R.string.settings_reset_confetti_body,
            checked = resetConfetti,
            onChange = onResetConfettiChange,
            index = 1,
            modifier = Modifier.testTag(RESET_CONFETTI_TAG),
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DelightRow(
    @StringRes title: Int,
    @StringRes body: Int,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    index: Int,
    modifier: Modifier = Modifier,
) {
    SegmentedListItem(
        checked = checked,
        onCheckedChange = onChange,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = DELIGHTS),
        colors =
            ListItemDefaults.segmentedColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ),
        supportingContent = { Text(stringResource(body)) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        modifier = modifier,
    ) {
        Text(stringResource(title))
    }
}

private const val DELIGHTS = 2
