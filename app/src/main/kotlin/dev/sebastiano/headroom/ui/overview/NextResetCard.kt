package dev.sebastiano.headroom.ui.overview

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.sebastiano.headroom.R
import dev.sebastiano.headroom.designsystem.HeadroomIcons
import dev.sebastiano.headroom.model.WindowKind
import dev.sebastiano.headroom.ui.ResetFormatter
import dev.sebastiano.headroom.ui.home.NextResetSummary
import java.time.Instant

const val NEXT_RESET_ALERT_TAG: String = "next-reset-alert"

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
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = colors.primaryContainer,
        contentColor = colors.onPrimaryContainer,
    ) {
        Box {
            Box(
                modifier =
                    Modifier.align(Alignment.TopEnd)
                        .offset(x = 26.dp, y = (-22).dp)
                        .size(150.dp)
                        .clip(MaterialShapes.Cookie9Sided.toShape())
                        .background(
                            colors.primary
                                .copy(alpha = DECO_ALPHA)
                                .compositeOver(colors.primaryContainer)
                        )
            )
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text =
                        stringResource(
                            if (next.kind == WindowKind.Weekly) R.string.overview_next_weekly_reset
                            else R.string.overview_next_reset
                        ),
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    text = formatter.countdown(now, next.resetsAt),
                    style = MaterialTheme.typography.displayMedium,
                )
                Text(
                    text =
                        stringResource(
                            R.string.overview_next_reset_when,
                            next.provider.displayName,
                            formatter.long(next.resetsAt),
                        ),
                    style = MaterialTheme.typography.bodyMedium,
                )
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

private const val DECO_ALPHA = 0.22f
