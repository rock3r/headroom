package dev.sebastiano.headroom.ui.resets

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.sebastiano.headroom.R
import dev.sebastiano.headroom.designsystem.HeadroomIcons
import dev.sebastiano.headroom.designsystem.animationsEnabled
import dev.sebastiano.headroom.model.AskOutcome
import dev.sebastiano.headroom.model.RedeemOutcome
import dev.sebastiano.headroom.model.RedeemStep
import dev.sebastiano.headroom.ui.ResetFormatter
import dev.sebastiano.headroom.ui.home.AccountSummary

/** How an outcome looks: its badge, and its words. */
private class OutcomeLook(
    @DrawableRes val icon: Int,
    val container: Color,
    val onContainer: Color,
    val title: String,
    val body: String?,
)

/** An outcome: a badge, a title and a line, and the buttons that close or go on. */
@Composable
private fun OutcomeBlock(
    look: OutcomeLook,
    working: Boolean? = null,
    buttons: @Composable RowScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(shape = CircleShape, color = look.container, modifier = Modifier.size(44.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(look.icon),
                        contentDescription = null,
                        tint = look.onContainer,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            StepTitle(look.title, Modifier.weight(1f))
        }
        look.body?.let { Body(it) }
        // While the new usage comes in after a success. The line keeps its place once the usage
        // is in, only fading out, so the buttons do not jump as the bars refill.
        if (working != null) {
            val alpha by
                animateFloatAsState(
                    targetValue = if (working) 1f else 0f,
                    animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                    label = "updating line",
                )
            Text(
                text = stringResource(R.string.redeem_updating),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier =
                    Modifier.graphicsLayer { this.alpha = alpha }
                        .then(
                            if (working) Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                            else Modifier.clearAndSetSemantics {}
                        ),
            )
        }
        ButtonRow(buttons)
    }
}

/** A Close button, then the button that goes on. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RowScope.CloseAnd(
    @StringRes label: Int,
    onClick: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    @StringRes closeLabel: Int = R.string.redeem_close,
) {
    TextButton(onClick = onClose) { Text(stringResource(closeLabel)) }
    Button(onClick = onClick, shapes = ButtonDefaults.shapes(), modifier = modifier) {
        Text(stringResource(label))
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DoneButton(onClick: () -> Unit, @StringRes label: Int = R.string.redeem_done) {
    Button(onClick = onClick, shapes = ButtonDefaults.shapes()) { Text(stringResource(label)) }
}

/**
 * What a redeem did, and what the user can do next. A failure offers Try again with the same key;
 * an unconfirmed attempt offers Check again, which reads the status instead of sending it again.
 */
@Composable
internal fun Finished(
    step: RedeemStep.Finished,
    provider: String,
    canAskForMore: Boolean,
    formatter: ResetFormatter,
    actions: RedeemActions,
    refreshing: Boolean = false,
) {
    val outcome = step.outcome
    OutcomeBlock(
        outcomeLook(outcome, provider, formatter),
        working = refreshing.takeIf { outcome is RedeemOutcome.Success },
    ) {
        when {
            outcome is RedeemOutcome.Failed || outcome is RedeemOutcome.RateLimited ->
                CloseAnd(
                    R.string.redeem_try_again,
                    actions.onTryAgain,
                    actions.onClose,
                    Modifier.testTag(REDEEM_TRY_AGAIN_TAG),
                )
            outcome == RedeemOutcome.Unconfirmed ->
                CloseAnd(
                    R.string.redeem_check_again,
                    actions.onCheckAgain,
                    actions.onClose,
                    Modifier.testTag(REDEEM_CHECK_AGAIN_TAG),
                )
            outcome == RedeemOutcome.SignInAgain ->
                CloseAnd(
                    R.string.redeem_sign_in_again_title,
                    actions.onSignInAgain,
                    actions.onClose,
                )
            outcome == RedeemOutcome.NoCredit && canAskForMore ->
                CloseAnd(R.string.resets_ask, actions.onAsk, actions.onClose)
            else -> DoneButton(actions.onClose)
        }
    }
}

@Composable
private fun outcomeLook(
    outcome: RedeemOutcome,
    provider: String,
    formatter: ResetFormatter,
): OutcomeLook {
    val colors = MaterialTheme.colorScheme
    return when (outcome) {
        is RedeemOutcome.Success ->
            OutcomeLook(
                HeadroomIcons.Check,
                colors.primaryContainer,
                colors.onPrimaryContainer,
                stringResource(R.string.redeem_success_title),
                outcome.resetsLeft?.let { left ->
                    if (left == 0) stringResource(R.string.redeem_success_none_left)
                    else pluralStringResource(R.plurals.redeem_success_left, left, left)
                },
            )
        RedeemOutcome.NothingToReset ->
            OutcomeLook(
                HeadroomIcons.Check,
                colors.secondaryContainer,
                colors.onSecondaryContainer,
                stringResource(R.string.redeem_nothing_title),
                stringResource(R.string.redeem_nothing_body),
            )
        RedeemOutcome.NoCredit ->
            OutcomeLook(
                HeadroomIcons.Replay,
                colors.surfaceContainerHighest,
                colors.onSurfaceVariant,
                stringResource(R.string.redeem_no_credit_title),
                stringResource(R.string.redeem_no_credit_body),
            )
        RedeemOutcome.Cooldown ->
            OutcomeLook(
                HeadroomIcons.EventRepeat,
                colors.secondaryContainer,
                colors.onSecondaryContainer,
                stringResource(R.string.redeem_cooldown_title),
                stringResource(R.string.redeem_cooldown_body),
            )
        RedeemOutcome.Ineligible ->
            OutcomeLook(
                HeadroomIcons.Error,
                colors.surfaceContainerHighest,
                colors.onSurfaceVariant,
                stringResource(R.string.redeem_ineligible_title),
                stringResource(R.string.redeem_ineligible_body),
            )
        RedeemOutcome.Unconfirmed ->
            OutcomeLook(
                HeadroomIcons.Sync,
                colors.tertiaryContainer,
                colors.onTertiaryContainer,
                stringResource(R.string.redeem_unconfirmed_title),
                stringResource(R.string.redeem_unconfirmed_body, provider),
            )
        is RedeemOutcome.RateLimited ->
            OutcomeLook(
                HeadroomIcons.Error,
                colors.surfaceContainerHighest,
                colors.onSurfaceVariant,
                stringResource(R.string.redeem_rate_limited_title),
                listOfNotNull(
                        stringResource(R.string.redeem_rate_limited_body, provider),
                        outcome.retryAfter?.let {
                            stringResource(R.string.redeem_rate_limited_retry, formatter.long(it))
                        },
                    )
                    .joinToString(" "),
            )
        RedeemOutcome.SignInAgain ->
            OutcomeLook(
                HeadroomIcons.Key,
                colors.errorContainer,
                colors.onErrorContainer,
                stringResource(R.string.redeem_sign_in_again_title),
                stringResource(R.string.redeem_sign_in_again_body, provider),
            )
        is RedeemOutcome.Failed ->
            OutcomeLook(
                HeadroomIcons.Error,
                colors.errorContainer,
                colors.onErrorContainer,
                stringResource(R.string.redeem_failed_title),
                stringResource(R.string.redeem_failed_body, provider),
            )
        RedeemOutcome.Unsupported -> unsupportedLook()
    }
}

@Composable
private fun unsupportedLook(): OutcomeLook =
    OutcomeLook(
        HeadroomIcons.Error,
        MaterialTheme.colorScheme.surfaceContainerHighest,
        MaterialTheme.colorScheme.onSurfaceVariant,
        stringResource(R.string.redeem_unsupported_title),
        stringResource(R.string.redeem_unsupported_body),
    )

/** The provider's answer to "Ask for a reset card". A granted card can be used at once. */
@Composable
internal fun Answered(
    answer: AskOutcome,
    provider: String,
    formatter: ResetFormatter,
    actions: RedeemActions,
) {
    OutcomeBlock(answerLook(answer, provider, formatter)) {
        if (answer is AskOutcome.Granted) {
            CloseAnd(
                R.string.redeem_use_now,
                actions.onUseNow,
                actions.onClose,
                closeLabel = R.string.redeem_later,
            )
        } else {
            DoneButton(actions.onClose, R.string.redeem_close)
        }
    }
}

@Composable
private fun answerLook(
    answer: AskOutcome,
    provider: String,
    formatter: ResetFormatter,
): OutcomeLook {
    val colors = MaterialTheme.colorScheme
    return when (answer) {
        is AskOutcome.Granted ->
            OutcomeLook(
                HeadroomIcons.Add,
                colors.primaryContainer,
                colors.onPrimaryContainer,
                stringResource(R.string.redeem_granted_title),
                stringResource(R.string.redeem_granted_body),
            )
        is AskOutcome.NotYet ->
            OutcomeLook(
                HeadroomIcons.EventRepeat,
                colors.secondaryContainer,
                colors.onSecondaryContainer,
                stringResource(R.string.redeem_not_yet_title),
                listOfNotNull(
                        stringResource(R.string.redeem_not_yet_body, provider),
                        answer.retryAfter?.let {
                            stringResource(R.string.redeem_not_yet_retry, formatter.long(it))
                        },
                    )
                    .joinToString(" "),
            )
        AskOutcome.Throttled ->
            OutcomeLook(
                HeadroomIcons.Error,
                colors.surfaceContainerHighest,
                colors.onSurfaceVariant,
                stringResource(R.string.redeem_throttled_title),
                stringResource(R.string.redeem_throttled_body, provider),
            )
        is AskOutcome.Failed ->
            OutcomeLook(
                HeadroomIcons.Error,
                colors.errorContainer,
                colors.onErrorContainer,
                stringResource(R.string.redeem_ask_failed_title),
                stringResource(R.string.redeem_ask_failed_body, provider),
            )
        AskOutcome.Unsupported -> unsupportedLook()
    }
}

/**
 * The provider needs its own sign-in before resets can be seen or used, as Z.AI's ZCode. The
 * prototype's button only says that sign-in is not built yet.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SignIn(summary: AccountSummary, signInNote: Boolean, actions: RedeemActions) {
    val service = ResetCopy.signInService(summary.provider)
    val animate = animationsEnabled()
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<IntSize>()
    Column {
        OutcomeBlock(
            OutcomeLook(
                HeadroomIcons.Key,
                MaterialTheme.colorScheme.tertiaryContainer,
                MaterialTheme.colorScheme.onTertiaryContainer,
                stringResource(R.string.redeem_sign_in_title, service),
                stringResource(R.string.redeem_sign_in_body, summary.provider.displayName, service),
            )
        ) {
            TextButton(onClick = actions.onClose) { Text(stringResource(R.string.redeem_not_now)) }
            Button(onClick = actions.onSignIn, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.redeem_sign_in_button, service))
            }
        }
        // The note opens its space, gap included, on the sheet's spring, so the sheet grows
        // smoothly instead of jumping by the note's height.
        AnimatedVisibility(
            visible = signInNote,
            enter =
                if (animate) {
                    fadeIn(effects) + expandVertically(spatial, Alignment.Top, clip = false)
                } else {
                    fadeIn(effects)
                },
        ) {
            Text(
                text = stringResource(R.string.redeem_sign_in_stub),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier =
                    Modifier.padding(top = 12.dp).semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}
