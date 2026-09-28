package dev.sebastiano.headroom.ui.overview

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.sebastiano.headroom.R
import dev.sebastiano.headroom.designsystem.HeadroomIcons
import dev.sebastiano.headroom.designsystem.HeadroomMotion
import dev.sebastiano.headroom.designsystem.animationsEnabled
import dev.sebastiano.headroom.designsystem.liquidRipple
import dev.sebastiano.headroom.designsystem.rememberLiquidRippleState
import dev.sebastiano.headroom.model.WindowKind
import dev.sebastiano.headroom.ui.ResetFormatter
import dev.sebastiano.headroom.ui.home.NextResetSummary
import java.time.Instant
import kotlin.math.PI
import kotlin.math.sin

const val NEXT_RESET_ALERT_TAG: String = "next-reset-alert"
const val NEXT_RESET_CARD_TAG: String = "next-reset-card"

/** Exposes whether the card's decorative shape is drifting, to tests and tools. */
val NextResetDecorationDriftKey: SemanticsPropertyKey<Boolean> =
    SemanticsPropertyKey("NextResetDecorationDrift")

private var SemanticsPropertyReceiver.decorationDrift: Boolean by NextResetDecorationDriftKey

/** The hero card: how long until the next weekly reset, with its alert switch. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NextResetCard(
    next: NextResetSummary,
    now: Instant,
    formatter: ResetFormatter,
    onAlertChange: (Boolean) -> Unit,
    onAllResets: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val animate = animationsEnabled()
    val drift = rememberDecorationDrift(animate)
    val ripple = rememberLiquidRippleState()
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = colors.primaryContainer,
        contentColor = colors.onPrimaryContainer,
    ) {
        // The ripple refracts the content, not the card itself, so the card's edge stays still.
        Box(
            Modifier.testTag(NEXT_RESET_CARD_TAG)
                .semantics { decorationDrift = animate }
                .liquidRipple(ripple, enabled = animate)
        ) {
            Box(
                modifier =
                    Modifier.align(Alignment.TopEnd)
                        .offset(x = 26.dp, y = (-22).dp)
                        .size(150.dp)
                        .graphicsLayer { rotationZ = DECO_TILT_DEGREES + drift() }
                        .clip(MaterialShapes.Cookie9Sided.toShape())
                        .background(
                            colors.primary
                                .copy(alpha = DECO_ALPHA)
                                .compositeOver(colors.primaryContainer)
                        )
            )
            Column(modifier = Modifier.padding(16.dp)) {
                NextResetText(next, now, formatter)
                FlowRow(
                    modifier = Modifier.padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ToggleButton(
                        checked = next.alertEnabled,
                        onCheckedChange = onAlertChange,
                        modifier = Modifier.testTag(NEXT_RESET_ALERT_TAG),
                        colors =
                            ToggleButtonDefaults.colors(
                                containerColor = colors.secondaryContainer,
                                contentColor = colors.onSecondaryContainer,
                            ),
                    ) {
                        Icon(
                            painter =
                                painterResource(
                                    if (next.alertEnabled) HeadroomIcons.NotificationsActiveFilled
                                    else HeadroomIcons.NotificationsOff
                                ),
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.IconSize),
                        )
                        Text(
                            text =
                                stringResource(
                                    if (next.alertEnabled) R.string.overview_alert_on
                                    else R.string.overview_alert_off
                                ),
                            modifier = Modifier.padding(start = ButtonDefaults.IconSpacing),
                        )
                    }
                    FilledTonalButton(onClick = onAllResets) {
                        Icon(
                            painter = painterResource(HeadroomIcons.EventRepeat),
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.IconSize),
                        )
                        Text(
                            text = stringResource(R.string.overview_all_resets),
                            modifier = Modifier.padding(start = ButtonDefaults.IconSpacing),
                        )
                    }
                }
            }
        }
    }
}

/**
 * What resets and when. When the next reset becomes a different window (after a reset, or a new
 * account), the text fades through to it; the countdown ticking down does not animate.
 */
@Composable
private fun NextResetText(next: NextResetSummary, now: Instant, formatter: ResetFormatter) {
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val fast = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    AnimatedContent(
        targetState = next,
        contentKey = { Triple(it.accountId, it.windowId, it.resetsAt) },
        transitionSpec = {
            (fadeIn(effects) + scaleIn(effects, initialScale = FADE_THROUGH_SCALE)) togetherWith
                fadeOut(fast)
        },
        label = "next reset",
    ) { shown ->
        Column {
            Text(
                text =
                    stringResource(
                        if (shown.kind == WindowKind.Weekly) R.string.overview_next_weekly_reset
                        else R.string.overview_next_reset
                    ),
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                text = formatter.countdown(now, shown.resetsAt),
                style = MaterialTheme.typography.displayMedium,
            )
            Text(
                text =
                    stringResource(
                        R.string.overview_next_reset_when,
                        shown.name,
                        formatter.long(shown.resetsAt),
                    ),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/**
 * How far the card's shape has drifted from its resting tilt, in degrees. It is read only while
 * drawing, so the drift redraws the shape's layer and never recomposes the card. The drift is a
 * sine over one long period, so each swing eases in and out, and it starts at the resting tilt.
 *
 * An endless animation is not timed by the duration scale, so with animations off or motion reduced
 * it is not started at all and the shape rests at its tilt. It costs nothing when the card is not
 * on screen: the overview's list disposes items it scrolls away, and Compose pauses its frame clock
 * while the app is in the background.
 */
@Composable
private fun rememberDecorationDrift(animate: Boolean): () -> Float {
    if (!animate) return NoDrift
    val phase =
        rememberInfiniteTransition(label = "next reset shape drift")
            .animateFloat(0f, 1f, HeadroomMotion.driftSpec(), label = "drift phase")
    return remember(phase) { { DECO_DRIFT_DEGREES * sin(2 * PI.toFloat() * phase.value) } }
}

private val NoDrift: () -> Float = { 0f }

private const val FADE_THROUGH_SCALE = 0.96f
private const val DECO_ALPHA = 0.22f

/** The shape's resting tilt: enough to read as placed by hand, not enough to look knocked over. */
private const val DECO_TILT_DEGREES = 15f

/** How far the shape drifts either side of its tilt over one period of the drift. */
private const val DECO_DRIFT_DEGREES = 6f
