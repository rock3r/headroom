package dev.sebastiano.headroom.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.sebastiano.headroom.R
import dev.sebastiano.headroom.designsystem.HeadroomIcons
import kotlinx.coroutines.launch

/**
 * A small info button for a quota Headroom does not recognise. Its tooltip explains that the quota
 * is shown for information only. The tooltip opens on hover and long press, as every tooltip does,
 * and also on a tap, because a tap is what most people try on a phone. It stays until dismissed,
 * because the text is too long to read before a short tooltip goes away.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnknownQuotaInfo(modifier: Modifier = Modifier) {
    val state = rememberTooltipState(isPersistent = true)
    val scope = rememberCoroutineScope()
    TooltipBox(
        positionProvider =
            TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = {
            PlainTooltip { Text(stringResource(R.string.detail_unknown_quota_explanation)) }
        },
        state = state,
        modifier = modifier,
    ) {
        IconButton(onClick = { scope.launch { state.show() } }) {
            Icon(
                painter = painterResource(HeadroomIcons.Info),
                contentDescription = stringResource(R.string.detail_unknown_quota_info),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
