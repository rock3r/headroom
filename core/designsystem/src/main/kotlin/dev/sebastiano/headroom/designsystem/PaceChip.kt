package dev.sebastiano.headroom.designsystem

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/**
 * Says in words how a window compares with even pace. The words are the static carrier of the
 * "needs attention" signal, so the wavy indicators are never the only one.
 */
@Composable
fun PaceChip(state: PaceChipState, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val (container, content) =
        when (state) {
            is PaceChipState.Over -> colors.tertiaryContainer to colors.onTertiaryContainer
            PaceChipState.JustReset -> colors.primary to colors.onPrimary
            else -> colors.secondaryContainer to colors.onSecondaryContainer
        }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = container,
        contentColor = content,
    ) {
        Text(
            text = paceChipText(state),
            style = MaterialTheme.typography.labelMedium,
            modifier =
                Modifier.heightIn(min = 24.dp)
                    .wrapContentHeight()
                    .padding(horizontal = 10.dp, vertical = 3.dp),
        )
    }
}

/** The chip's words, also used in content descriptions. */
@Composable
@ReadOnlyComposable
fun paceChipText(state: PaceChipState): String =
    when (state) {
        is PaceChipState.Over ->
            pluralStringResource(R.plurals.designsystem_pace_over, state.points, state.points)
        is PaceChipState.Under ->
            pluralStringResource(R.plurals.designsystem_pace_under, state.points, state.points)
        PaceChipState.OnPace -> stringResource(R.string.designsystem_pace_on)
        PaceChipState.JustReset -> stringResource(R.string.designsystem_pace_just_reset)
    }
