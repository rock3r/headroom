package dev.sebastiano.headroom.ui.overview

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.sebastiano.headroom.R
import dev.sebastiano.headroom.designsystem.HeadroomMotion
import dev.sebastiano.headroom.designsystem.PaceChip
import dev.sebastiano.headroom.designsystem.ProviderAvatar
import dev.sebastiano.headroom.designsystem.QuotaBar
import dev.sebastiano.headroom.ui.ResetFormatter
import dev.sebastiano.headroom.ui.SharedElements
import dev.sebastiano.headroom.ui.asFraction
import dev.sebastiano.headroom.ui.components.errorText
import dev.sebastiano.headroom.ui.components.usedLabel
import dev.sebastiano.headroom.ui.components.windowKindLabel
import dev.sebastiano.headroom.ui.home.AccountSummary
import dev.sebastiano.headroom.ui.home.WindowSummary
import java.time.Instant
import kotlin.math.roundToInt

/**
 * One account on the overview: the big number, the primary window's bar with its pace tick, the
 * session bar, and the pace chip. The primary bar is wavy only when the account needs attention.
 */
@Composable
fun AccountCard(
    account: AccountSummary,
    now: Instant,
    formatter: ResetFormatter,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    sharedElements: SharedElements? = null,
) {
    val container =
        sharedElements?.run {
            Modifier.sharedContainer(
                transitionScope.rememberSharedContentState(SharedElements.cardKey(account.id))
            )
        } ?: Modifier
    Surface(
        onClick = onClick,
        modifier = modifier.then(container).testTag(accountCardTag(account.id)),
        shape = RoundedCornerShape(24.dp),
        color =
            if (selected) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CardTop(account, sharedElements)
            val primary = account.primary
            if (primary == null) {
                Text(
                    text = stringResource(R.string.account_no_data),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                MeterRow(
                    label = windowKindLabel(primary.kind),
                    window = primary,
                    wavy = account.needsAttention,
                    trailing = primary.resetsAt?.let { formatter.short(it, now) },
                )
            }
            account.session?.let { session ->
                MeterRow(
                    label = windowKindLabel(session.kind),
                    window = session,
                    wavy = false,
                    showPace = false,
                    trailing = session.resetsAt?.let { formatter.countdown(now, it) },
                )
            }
            account.pace?.let { PaceChip(it) }
            account.error?.let {
                Text(
                    text = errorText(it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

fun accountCardTag(accountId: String): String = "account-card-$accountId"

@Composable
private fun CardTop(account: AccountSummary, sharedElements: SharedElements?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        val avatar =
            sharedElements?.run {
                Modifier.sharedAvatar(
                    transitionScope.rememberSharedContentState(SharedElements.avatarKey(account.id))
                )
            } ?: Modifier
        ProviderAvatar(provider = account.provider, modifier = avatar)
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                text = account.provider.displayName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            account.plan?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        account.primary?.let { primary ->
            Column(horizontalAlignment = Alignment.End) {
                AnimatedPercent(
                    percent = primary.usedPercent,
                    modifier =
                        sharedElements?.run {
                            Modifier.sharedValue(
                                transitionScope.rememberSharedContentState(
                                    SharedElements.valueKey(account.id)
                                )
                            )
                        } ?: Modifier,
                )
                Text(
                    text = usedLabel(primary.kind),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** A percentage that moves to a new value without overshoot. */
@Composable
fun AnimatedPercent(
    percent: Double,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.headlineMedium,
) {
    val animated by
        animateFloatAsState(
            targetValue = percent.toFloat(),
            animationSpec = HeadroomMotion.dataSpec(),
            label = "percent",
        )
    Text(
        text = stringResource(R.string.percent, animated.roundToInt()),
        style = style,
        modifier = modifier,
    )
}

@Composable
private fun MeterRow(
    label: String,
    window: WindowSummary,
    wavy: Boolean,
    trailing: String?,
    showPace: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(58.dp),
            maxLines = 1,
        )
        QuotaBar(
            progress = window.usedPercent.asFraction(),
            wavy = wavy,
            paceFraction = if (showPace) window.expectedPercent?.asFraction() else null,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = trailing.orEmpty(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            maxLines = 1,
            modifier = Modifier.widthIn(min = 64.dp),
        )
    }
}
