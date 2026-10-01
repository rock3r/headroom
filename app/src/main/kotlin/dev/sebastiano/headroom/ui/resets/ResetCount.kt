package dev.sebastiano.headroom.ui.resets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import dev.sebastiano.headroom.R
import dev.sebastiano.headroom.model.ResetAvailability
import kotlinx.coroutines.launch

/**
 * How many resets an account has: the ones available now, with the queued ones in brackets, as "2
 * (+5)", or just "2" when none is queued. Screen readers hear it in words.
 *
 * With some queued, a tooltip explains the brackets. It shows on mouse hover and on long press
 * (both handled by [TooltipBox]), and on a plain tap, since the count then acts as a button. The
 * tooltip's own text is hidden from screen readers: the count's description already says it all.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResetCount(
    availability: ResetAvailability,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleSmall,
) {
    val description = resetCountDescription(availability)
    val text = resetCountText(availability)
    if (availability.queued == 0) {
        Text(
            text = text,
            style = style,
            modifier = modifier.semantics { contentDescription = description },
        )
        return
    }
    val tip = resetCountTooltip(availability)
    val showLabel = stringResource(R.string.resets_count_explain)
    val scope = rememberCoroutineScope()
    val tooltipState = rememberTooltipState()
    TooltipBox(
        positionProvider =
            TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = {
            PlainTooltip(
                modifier = Modifier.clearAndSetSemantics {},
                maxWidth = TooltipMaxWidth,
            ) {
                Text(tip)
            }
        },
        state = tooltipState,
        modifier = modifier,
    ) {
        Text(
            text = text,
            style = style,
            modifier =
                Modifier.testTag(RESET_COUNT_TAG)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(role = Role.Button, onClickLabel = showLabel) {
                        scope.launch { tooltipState.show() }
                    }
                    .padding(horizontal = 4.dp)
                    .semantics { contentDescription = description },
        )
    }
}

const val RESET_COUNT_TAG: String = "reset-count"

private val TooltipMaxWidth = 240.dp

/** The tooltip's words: "2 resets available now. 5 more are queued: ..." */
@Composable
@ReadOnlyComposable
private fun resetCountTooltip(availability: ResetAvailability): String {
    val now = availability.availableNow
    val queued = availability.queued
    return stringResource(
        R.string.resets_count_tooltip,
        pluralStringResource(R.plurals.resets_count_now_description, now, now),
        pluralStringResource(R.plurals.resets_count_tooltip_queued, queued, queued),
    )
}

/** "2 (+5)", or "2" when nothing is queued. */
@Composable
@ReadOnlyComposable
fun resetCountText(availability: ResetAvailability): String {
    val now = availability.availableNow
    val queued = availability.queued
    return if (queued > 0) stringResource(R.string.resets_count_queued, now, queued)
    else now.toString()
}

/** "2 resets available now, 5 more queued", or only the first half when nothing is queued. */
@Composable
@ReadOnlyComposable
fun resetCountDescription(availability: ResetAvailability): String {
    val now = availability.availableNow
    val queued = availability.queued
    val nowText = pluralStringResource(R.plurals.resets_count_now_description, now, now)
    if (queued == 0) return nowText
    val queuedText = pluralStringResource(R.plurals.resets_count_queued_description, queued, queued)
    return stringResource(R.string.resets_count_description, nowText, queuedText)
}
