package dev.sebastiano.headroom.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.sebastiano.headroom.R
import dev.sebastiano.headroom.ui.delights.LocalDelights
import dev.sebastiano.headroom.ui.delights.delightAnchor

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
        val delights = LocalDelights.current
        val colours =
            listOf(
                MaterialTheme.colorScheme.primary,
                MaterialTheme.colorScheme.secondary,
                MaterialTheme.colorScheme.tertiary,
            )
        DelightRow(
            title = R.string.settings_refresh_shimmer,
            body = R.string.settings_refresh_shimmer_body,
            checked = refreshShimmer,
            onChange = onRefreshShimmerChange,
            index = 0,
            tryLabel = R.string.settings_try_refresh_shimmer,
            canTry = delights?.canShimmer == true,
            onTry = { delights?.playShimmer() },
            modifier = Modifier.testTag(REFRESH_SHIMMER_TAG),
        )
        DelightRow(
            title = R.string.settings_reset_confetti,
            body = R.string.settings_reset_confetti_body,
            checked = resetConfetti,
            onChange = onResetConfettiChange,
            index = 1,
            tryLabel = R.string.settings_try_reset_confetti,
            canTry = delights?.canBurst == true,
            onTry = { delights?.burstFrom(listOf(TRY_CONFETTI_ANCHOR), colours) },
            tryModifier = Modifier.delightAnchor(TRY_CONFETTI_ANCHOR),
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
    @StringRes tryLabel: Int,
    canTry: Boolean,
    onTry: () -> Unit,
    modifier: Modifier = Modifier,
    tryModifier: Modifier = Modifier,
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
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Plays the delight once, so it can be seen without waiting for a refresh or a
                // reset.
                val description = stringResource(tryLabel)
                TextButton(
                    onClick = onTry,
                    enabled = canTry,
                    modifier = tryModifier.semantics { contentDescription = description },
                ) {
                    Text(stringResource(R.string.settings_try_delight))
                }
                Switch(checked = checked, onCheckedChange = null)
            }
        },
        modifier = modifier,
    ) {
        Text(stringResource(title))
    }
}

private const val DELIGHTS = 2

/** Where the confetti of the "Try" button bursts from: the button itself. */
private const val TRY_CONFETTI_ANCHOR = "try-confetti"
