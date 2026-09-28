package dev.sebastiano.headroom.ui.resets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import dev.sebastiano.headroom.R
import dev.sebastiano.headroom.designsystem.HeadroomIcons
import dev.sebastiano.headroom.designsystem.ProviderAvatar
import dev.sebastiano.headroom.ui.ResetFormatter
import dev.sebastiano.headroom.ui.components.ListCard
import dev.sebastiano.headroom.ui.components.ScreenHeader
import dev.sebastiano.headroom.ui.components.SectionLabel
import dev.sebastiano.headroom.ui.home.AccountSummary
import dev.sebastiano.headroom.ui.home.HomeUiState
import dev.sebastiano.headroom.ui.home.WindowSummary
import kotlin.math.roundToInt

const val RESETS_TAG: String = "resets"

/** Upcoming resets with their alert state, and how full each window was when it last reset. */
@Composable
fun ResetsScreen(
    state: HomeUiState,
    formatter: ResetFormatter,
    modifier: Modifier = Modifier,
    bottomPadding: androidx.compose.ui.unit.Dp = 0.dp,
) {
    val upcoming =
        state.accounts
            .flatMap { account ->
                account.windows
                    .filter { it.canAlert && it.resetsAt?.isAfter(state.now) == true }
                    .map { account to it }
            }
            .sortedBy { (_, window) -> window.resetsAt }
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    LazyColumn(
        modifier =
            modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                .testTag(RESETS_TAG),
        contentPadding =
            PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = insets.calculateTopPadding() + 8.dp,
                bottom = insets.calculateBottomPadding() + bottomPadding + 16.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val width = Modifier.widthIn(max = 600.dp).fillMaxWidth()
        item {
            ScreenHeader(
                title = stringResource(R.string.resets_title),
                subtitle =
                    pluralStringResource(
                        R.plurals.resets_subtitle,
                        upcoming.size,
                        upcoming.count { it.second.alertEnabled },
                        upcoming.size,
                    ),
                modifier = width,
            )
        }
        if (upcoming.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.resets_none),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = width,
                )
            }
            return@LazyColumn
        }
        item { SectionLabel(stringResource(R.string.resets_upcoming), width) }
        item {
            ListCard(width) {
                upcoming.forEachIndexed { index, (account, window) ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.surface)
                    UpcomingRow(account, window, state, formatter)
                }
            }
        }
        item { SectionLabel(stringResource(R.string.resets_history), width) }
        item { HistoryCard(state.accounts, width) }
    }
}

@Composable
private fun UpcomingRow(
    account: AccountSummary,
    window: WindowSummary,
    state: HomeUiState,
    formatter: ResetFormatter,
) {
    val at = window.resetsAt ?: return
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProviderAvatar(provider = account.provider, size = 30.dp)
        Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(
                text =
                    stringResource(
                        R.string.resets_row_title,
                        account.name,
                        window.label,
                    ),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text =
                    stringResource(
                        R.string.resets_row_when,
                        formatter.long(at),
                        formatter.countdown(state.now, at),
                    ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            painter =
                painterResource(
                    if (window.alertEnabled) HeadroomIcons.NotificationsActiveFilled
                    else HeadroomIcons.NotificationsOff
                ),
            contentDescription =
                stringResource(
                    if (window.alertEnabled) R.string.resets_bell_on else R.string.resets_bell_off
                ),
            tint =
                if (window.alertEnabled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HistoryCard(accounts: List<AccountSummary>, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            accounts.forEach { account ->
                val current = account.primary?.usedPercent ?: return@forEach
                HistoryRow(account, current)
            }
            Text(
                text = stringResource(R.string.resets_history_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HistoryRow(account: AccountSummary, current: Double) {
    val values = account.pastResets + current
    val description =
        stringResource(
            R.string.resets_history_description,
            account.name,
            account.pastResets.joinToString { "${it.roundToInt()}%" },
            current.roundToInt(),
        )
    Row(
        modifier =
            Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text = account.name,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.width(96.dp),
            maxLines = 1,
        )
        Row(
            modifier = Modifier.weight(1f).height(BarAreaHeight),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            values.forEachIndexed { index, value ->
                val isCurrent = index == values.lastIndex
                val hitLimit = !isCurrent && value >= FULL
                val color =
                    when {
                        hitLimit -> MaterialTheme.colorScheme.error
                        isCurrent -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.primary.copy(alpha = PAST_ALPHA)
                    }
                Box(
                    Modifier.weight(1f)
                        .fillMaxHeight((value / FULL).toFloat().coerceIn(MIN_BAR, 1f))
                        .background(
                            color,
                            RoundedCornerShape(
                                topStart = 4.dp,
                                topEnd = 4.dp,
                                bottomStart = 2.dp,
                                bottomEnd = 2.dp,
                            ),
                        )
                )
            }
        }
    }
}

private const val FULL = 100.0
private const val PAST_ALPHA = 0.45f
private const val MIN_BAR = 0.08f
private val BarAreaHeight = 36.dp
