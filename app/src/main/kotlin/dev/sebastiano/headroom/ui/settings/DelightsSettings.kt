package dev.sebastiano.headroom.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import dev.sebastiano.headroom.R
import dev.sebastiano.headroom.island.ResetIslandStatus
import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.tracing.tracedItem
import dev.sebastiano.headroom.ui.delights.LocalDelights
import dev.sebastiano.headroom.ui.delights.delightAnchor

const val REFRESH_SHIMMER_TAG: String = "refresh-shimmer"

const val RESET_CONFETTI_TAG: String = "reset-confetti"

const val RESET_ISLAND_TAG: String = "reset-island"

/**
 * The delights, each with its switch, as one list item per row: the refresh shimmer, the reset
 * confetti and the experimental reset island. The island's row also shows its [islandStatus], and
 * its Try button needs [canTryIsland], which is true when the accessibility service is connected or
 * Display over other apps is allowed. The first row gets [firstModifier], the others [modifier].
 */
@Suppress(
    "LongParameterList"
) // Each delight's state and actions; grouping them would only hide it.
internal fun LazyListScope.delightRows(
    refreshShimmer: Boolean,
    resetConfetti: Boolean,
    resetIsland: Boolean,
    islandStatus: ResetIslandStatus,
    canTryIsland: Boolean,
    onRefreshShimmerChange: (Boolean) -> Unit,
    onResetConfettiChange: (Boolean) -> Unit,
    onResetIslandChange: (Boolean) -> Unit,
    onTryResetIsland: (Provider, String) -> Unit,
    firstModifier: Modifier,
    modifier: Modifier,
) {
    tracedItem("Settings: DelightRow shimmer", key = "delight-shimmer") {
        val delights = LocalDelights.current
        DelightRow(
            title = R.string.settings_refresh_shimmer,
            body = R.string.settings_refresh_shimmer_body,
            checked = refreshShimmer,
            onChange = onRefreshShimmerChange,
            index = 0,
            tryLabel = R.string.settings_try_refresh_shimmer,
            canTry = delights?.canShimmer == true,
            onTry = { delights?.playShimmer() },
            modifier = firstModifier.testTag(REFRESH_SHIMMER_TAG),
        )
    }
    tracedItem("Settings: DelightRow confetti", key = "delight-confetti") {
        val delights = LocalDelights.current
        val colours =
            listOf(
                MaterialTheme.colorScheme.primary,
                MaterialTheme.colorScheme.secondary,
                MaterialTheme.colorScheme.tertiary,
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
            modifier = modifier.testTag(RESET_CONFETTI_TAG),
        )
    }
    tracedItem("Settings: DelightRow island", key = "delight-island") {
        val demoMessage = stringResource(R.string.reset_island_demo_message)
        DelightRow(
            title = R.string.settings_reset_island,
            body = R.string.settings_reset_island_body,
            checked = resetIsland,
            onChange = onResetIslandChange,
            index = 2,
            tryLabel = R.string.settings_try_reset_island,
            canTry = canTryIsland,
            onTry = { onTryResetIsland(Provider.Claude, demoMessage) },
            status = islandStatus,
            modifier = modifier.testTag(RESET_ISLAND_TAG),
        )
    }
}

@Composable
private fun IslandStatusLine(status: ResetIslandStatus, modifier: Modifier = Modifier) {
    val (text, colour) =
        when (status) {
            ResetIslandStatus.Off ->
                R.string.settings_reset_island_off to MaterialTheme.colorScheme.onSurfaceVariant
            ResetIslandStatus.NeedsPermission ->
                R.string.settings_reset_island_needs_permission to MaterialTheme.colorScheme.error
            ResetIslandStatus.ReadyAccessibility ->
                R.string.settings_reset_island_ready_accessibility to
                    MaterialTheme.colorScheme.primary
            ResetIslandStatus.ReadyOverlay ->
                R.string.settings_reset_island_ready_overlay to MaterialTheme.colorScheme.primary
        }
    Text(
        text = stringResource(text),
        style = MaterialTheme.typography.labelLarge,
        color = colour,
        modifier = modifier,
    )
}

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
    status: ResetIslandStatus? = null,
) {
    SettingsRow(
        headline = stringResource(title),
        action = RowAction.Toggle(checked, onChange),
        index = index,
        count = DELIGHTS,
        supporting = stringResource(body),
        below = status?.let { { IslandStatusLine(it, Modifier.padding(top = 4.dp)) } },
        trailing = {
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
    )
}

private const val DELIGHTS = 3

/** Where the confetti of the "Try" button bursts from: the button itself. */
private const val TRY_CONFETTI_ANCHOR = "try-confetti"
