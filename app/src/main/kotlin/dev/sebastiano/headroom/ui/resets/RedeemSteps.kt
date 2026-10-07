package dev.sebastiano.headroom.ui.resets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.sebastiano.headroom.R
import dev.sebastiano.headroom.designsystem.HeadroomIcons
import dev.sebastiano.headroom.designsystem.QuotaBar
import dev.sebastiano.headroom.model.QuotaDisplay
import dev.sebastiano.headroom.model.RedeemStep
import dev.sebastiano.headroom.model.ResetPool
import dev.sebastiano.headroom.model.ResetPoolStatus
import dev.sebastiano.headroom.model.ResetTiming
import dev.sebastiano.headroom.model.redeemsResetsExperimentally
import dev.sebastiano.headroom.ui.ResetFormatter
import dev.sebastiano.headroom.ui.asFraction
import dev.sebastiano.headroom.ui.components.percentDescription
import dev.sebastiano.headroom.ui.home.AccountSummary
import kotlin.math.roundToInt

/** A step's title: a heading that screen readers announce when the step changes. */
@Composable
internal fun StepTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineSmall,
        modifier =
            modifier.semantics {
                heading()
                liveRegion = LiveRegionMode.Polite
            },
    )
}

@Composable
internal fun Body(text: String, modifier: Modifier = Modifier) {
    Text(text = text, style = MaterialTheme.typography.bodyLarge, modifier = modifier)
}

@Composable
internal fun ChoosePool(
    step: RedeemStep.ChoosePool,
    summary: AccountSummary,
    display: QuotaDisplay,
    actions: RedeemActions,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        StepTitle(stringResource(R.string.redeem_choose_title))
        step.pools.forEach { pool ->
            PoolChoice(pool, summary, display, onClick = { actions.onChoose(pool.id) })
        }
        TextButton(onClick = actions.onClose, modifier = Modifier.align(Alignment.End)) {
            Text(stringResource(R.string.redeem_not_now))
        }
    }
}

/** A large card for one pool, with the current usage of the limits it resets. */
@Composable
internal fun PoolChoice(
    pool: ResetPool,
    summary: AccountSummary,
    display: QuotaDisplay,
    onClick: () -> Unit,
) {
    val available =
        pluralStringResource(R.plurals.resets_pool_available, pool.available, pool.available)
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().testTag(poolChoiceTag(pool.id)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.redeem_choose_pool, pool.label, available),
                style = MaterialTheme.typography.titleMedium,
            )
            affectedWindows(summary, pool).forEach { window ->
                val percent = display.percent(window.usedPercent)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = window.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(92.dp),
                    )
                    QuotaBar(progress = percent.asFraction(), modifier = Modifier.weight(1f))
                    Text(
                        text = percentDescription(percent.roundToInt(), display),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun Confirm(
    step: RedeemStep.Confirm,
    summary: AccountSummary,
    formatter: ResetFormatter,
    actions: RedeemActions,
) {
    val pool = step.pool
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (summary.provider.redeemsResetsExperimentally) ExperimentalLabel()
        StepTitle(
            pluralStringResource(R.plurals.redeem_confirm_title, pool.available, pool.available)
        )
        Body(ResetCopy.scope(summary.provider, pool))
        val notes =
            listOfNotNull(
                pool.soonestExpiry?.let { at ->
                    stringResource(R.string.redeem_confirm_soonest, formatter.long(at))
                },
                ResetCopy.confirmNote(summary.provider)?.let { stringResource(it) },
            )
        notes.forEach { note ->
            Text(
                text = note,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // When the resets expire at different times, the list shows which ones are left after.
        if (expiryLines(pool).size > 1) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) { ExpiryList(pool, formatter) }
        }
        if (pool.timing == ResetTiming.AnyTime) {
            Warning(stringResource(R.string.redeem_any_time))
        }
        if (pool.status == ResetPoolStatus.WaitingForLimit) {
            Warning(stringResource(R.string.resets_waiting_note))
        }
        if (pool.status == ResetPoolStatus.NotUsableYet) {
            Warning(stringResource(R.string.resets_not_yet_note, summary.provider.displayName))
        }
        ButtonRow {
            TextButton(onClick = if (step.canGoBack) actions.onBack else actions.onClose) {
                Text(
                    stringResource(
                        if (step.canGoBack) R.string.redeem_back else R.string.redeem_not_now
                    )
                )
            }
            Button(
                onClick = actions.onConfirm,
                enabled = pool.canUseNow,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.testTag(REDEEM_CONFIRM_TAG),
            ) {
                Icon(
                    painter = painterResource(HeadroomIcons.Replay),
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Text(
                    text = stringResource(R.string.redeem_use),
                    modifier = Modifier.padding(start = ButtonDefaults.IconSpacing),
                )
            }
        }
    }
}

/** Says that using this provider's resets is experimental: see [redeemsResetsExperimentally]. */
@Composable
private fun ExperimentalLabel() {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Text(
            text = stringResource(R.string.redeem_experimental),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

/** A line the user must not miss before confirming, on a tinted panel with an icon. */
@Composable
internal fun Warning(text: String) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                painter = painterResource(HeadroomIcons.Error),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Text(text = text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
internal fun ButtonRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content()
    }
}

/** The provider is working: the expressive loading indicator and one line. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun Working(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
    ) {
        LoadingIndicator(modifier = Modifier.size(48.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}
