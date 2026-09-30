package dev.sebastiano.headroom.ui.overview

import androidx.compose.animation.core.AnimationSpec
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.sebastiano.headroom.R
import dev.sebastiano.headroom.designsystem.HeadroomMotion
import dev.sebastiano.headroom.designsystem.PaceChip
import dev.sebastiano.headroom.designsystem.ProviderAvatar
import dev.sebastiano.headroom.designsystem.QuotaBar
import dev.sebastiano.headroom.designsystem.stale
import dev.sebastiano.headroom.model.QuotaDisplay
import dev.sebastiano.headroom.ui.ResetFormatter
import dev.sebastiano.headroom.ui.SharedElements
import dev.sebastiano.headroom.ui.asFraction
import dev.sebastiano.headroom.ui.components.SignInExpiredRow
import dev.sebastiano.headroom.ui.components.errorText
import dev.sebastiano.headroom.ui.components.quotaLabel
import dev.sebastiano.headroom.ui.components.windowKindLabel
import dev.sebastiano.headroom.ui.formatBalance
import dev.sebastiano.headroom.ui.home.AccountSummary
import dev.sebastiano.headroom.ui.home.WindowSummary
import java.time.Instant
import kotlin.math.roundToInt

/**
 * One account on the overview: the big number, the primary window's bar with its pace tick, the
 * session bar, and the pace chip. The primary bar is wavy only when the account needs attention.
 * The number and the bars show how much is used or how much is left, as [display] says.
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
    display: QuotaDisplay = QuotaDisplay.Used,
    onSignIn: () -> Unit = {},
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
            CardTop(account, sharedElements, display)
            // On the highlighted card the default track colour would vanish into the container.
            val track =
                if (selected) MaterialTheme.colorScheme.surfaceContainerLowest
                else MaterialTheme.colorScheme.secondaryContainer
            // Stale numbers are faded; the call to action below them is not.
            Column(
                modifier = Modifier.stale(account.signInExpired),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CardMeters(account, now, formatter, track, display)
            }
            if (account.signInExpired) {
                SignInExpiredRow(account.id, account.dataFrom, now, onSignIn)
            } else {
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
}

/** The balance or the bars, the session bar and the pace chip. */
@Composable
private fun CardMeters(
    account: AccountSummary,
    now: Instant,
    formatter: ResetFormatter,
    track: Color,
    display: QuotaDisplay,
) {
    val primary = account.primary
    val balance = account.balance
    if (primary == null && balance != null) {
        Text(
            text =
                stringResource(
                    R.string.account_balance,
                    formatBalance(balance, LocalLocale.current.platformLocale),
                ),
            style = MaterialTheme.typography.titleMedium,
        )
    } else if (account.allowances.isNotEmpty()) {
        AllowanceRows(account, now, formatter, track, display)
    } else if (primary == null) {
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
            trailing = resetLabel(primary, now, account.signInExpired) { formatter.short(it, now) },
            trackColor = track,
            display = display,
            draining = account.justReset,
        )
    }
    account.session?.let { session ->
        MeterRow(
            label = windowKindLabel(session.kind),
            window = session,
            wavy = false,
            showPace = false,
            trailing =
                resetLabel(session, now, account.signInExpired) { formatter.countdown(now, it) },
            trackColor = track,
            display = display,
        )
    }
    account.pace?.let { PaceChip(it) }
}

fun accountCardTag(accountId: String): String = "account-card-$accountId"

/**
 * When [window] resets, as [format] says. A stale window whose reset time has passed says it has
 * reset instead, because its numbers are from before that reset.
 */
@Composable
private fun resetLabel(
    window: WindowSummary,
    now: Instant,
    stale: Boolean,
    format: (Instant) -> String,
): String? {
    val resetsAt = window.resetsAt ?: return null
    return if (stale && !resetsAt.isAfter(now)) stringResource(R.string.stale_window_reset)
    else format(resetsAt)
}

/** One row per allowance, named after it. Only the primary one can be wavy or drain. */
@Composable
private fun AllowanceRows(
    account: AccountSummary,
    now: Instant,
    formatter: ResetFormatter,
    trackColor: Color,
    display: QuotaDisplay,
) {
    account.allowances.forEach { window ->
        val isPrimary = window.id == account.primary?.id
        MeterRow(
            label = window.label,
            window = window,
            wavy = isPrimary && account.needsAttention,
            trailing = resetLabel(window, now, account.signInExpired) { formatter.short(it, now) },
            trackColor = trackColor,
            display = display,
            draining = isPrimary && account.justReset,
            labelWidth = ALLOWANCE_LABEL_WIDTH,
        )
    }
}

private val KIND_LABEL_WIDTH = 58.dp

// Allowance names such as "JetBrains Team" are longer than "Weekly".
private val ALLOWANCE_LABEL_WIDTH = 96.dp

@Composable
private fun CardTop(
    account: AccountSummary,
    sharedElements: SharedElements?,
    display: QuotaDisplay,
) {
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
                text = account.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            account.plan
                ?.takeIf { it != account.name }
                ?.let {
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
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.stale(account.signInExpired),
            ) {
                AnimatedPercent(
                    percent = display.percent(primary.usedPercent),
                    draining = account.justReset,
                    display = display,
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
                    text = quotaLabel(primary.kind, display),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * A percentage that moves to a new value without overshoot. With [draining], a weekly reset (a drop
 * in used mode, a rise in left mode, see [display]) moves on the slower reset spec, like the bar
 * next to it.
 */
@Composable
fun AnimatedPercent(
    percent: Double,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.headlineMedium,
    draining: Boolean = false,
    display: QuotaDisplay = QuotaDisplay.Used,
) {
    val target = percent.toFloat()
    val animated by
        animateFloatAsState(
            targetValue = target,
            animationSpec = valueSpec(target, draining, display),
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
    trackColor: Color,
    display: QuotaDisplay,
    showPace: Boolean = true,
    draining: Boolean = false,
    labelWidth: Dp = KIND_LABEL_WIDTH,
) {
    val progress = display.percent(window.usedPercent).asFraction()
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(labelWidth),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        QuotaBar(
            progress = progress,
            wavy = wavy,
            trackColor = trackColor,
            animationSpec = valueSpec(progress, draining, display),
            paceFraction =
                if (showPace) window.expectedPercent?.let { display.percent(it).asFraction() }
                else null,
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

/**
 * The spec for a value moving to [target]: the reset drain when the account just reset and the
 * value moved the way a reset moves it (down in used mode, up in left mode), the regular data spec
 * otherwise. Only that move itself drains, once per reset.
 */
@Composable
private fun valueSpec(
    target: Float,
    draining: Boolean,
    display: QuotaDisplay,
): AnimationSpec<Float> {
    val previous = remember { mutableFloatStateOf(target) }
    val reset =
        when (display) {
            QuotaDisplay.Used -> target < previous.floatValue
            QuotaDisplay.Left -> target > previous.floatValue
        }
    SideEffect { previous.floatValue = target }
    return if (draining && reset) HeadroomMotion.resetDrainSpec() else HeadroomMotion.dataSpec()
}
