package dev.sebastiano.headroom.ui.resets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.sebastiano.headroom.R
import dev.sebastiano.headroom.designsystem.HeadroomIcons
import dev.sebastiano.headroom.designsystem.ProviderAvatar
import dev.sebastiano.headroom.model.QuotaDisplay
import dev.sebastiano.headroom.ui.ResetFormatter
import dev.sebastiano.headroom.ui.components.ListCard
import dev.sebastiano.headroom.ui.components.ScreenHeader
import dev.sebastiano.headroom.ui.components.SectionLabel
import dev.sebastiano.headroom.ui.components.StatusBarBlurBox
import dev.sebastiano.headroom.ui.home.AccountSummary
import dev.sebastiano.headroom.ui.home.HomeUiState
import dev.sebastiano.headroom.ui.home.WindowSummary

const val RESETS_TAG: String = "resets"

fun resetRowTag(accountId: String, windowId: String): String = "reset-row-$accountId-$windowId"

fun resetAlertTag(accountId: String, windowId: String): String = "reset-alert-$accountId-$windowId"

/**
 * Upcoming resets with their alert switches, and how full each window was when it reset: how much
 * was used, or how much was left, as the state's display says. Tapping a reset opens its account.
 */
@Composable
fun ResetsScreen(
    state: HomeUiState,
    formatter: ResetFormatter,
    onOpenAccount: (String) -> Unit,
    onAlertChange: (accountId: String, windowId: String, enabled: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 0.dp,
) {
    val upcoming =
        state.accounts
            .flatMap { account ->
                account.windows
                    .filter { it.canAlert && it.resetsAt?.isAfter(state.now) == true }
                    .map { account to it }
            }
            .sortedBy { (_, window) -> window.resetsAt }
    val sharedNames = state.accounts.groupingBy { it.name }.eachCount().filterValues { it > 1 }.keys
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val listState = rememberLazyListState()
    StatusBarBlurBox(scrollState = listState, modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier =
                Modifier.fillMaxSize()
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)
                    )
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
                        UpcomingRow(
                            account = account,
                            window = window,
                            title = windowTitle(account, window, sharedNames),
                            state = state,
                            formatter = formatter,
                            onOpen = { onOpenAccount(account.id) },
                            onAlertChange = { onAlertChange(account.id, window.id, it) },
                        )
                    }
                }
            }
            item {
                SectionLabel(
                    stringResource(
                        when (state.display) {
                            QuotaDisplay.Used -> R.string.resets_history
                            QuotaDisplay.Left -> R.string.resets_history_left
                        }
                    ),
                    width,
                )
            }
            item {
                ResetHistoryCard(
                    windows = historyWindows(state.accountsInYourOrder, sharedNames),
                    display = state.display,
                    modifier = width,
                )
            }
        }
    }
}

/**
 * The windows the history chart shows: every window that can alert, or the main window of an
 * account that has none.
 */
@Composable
private fun historyWindows(
    accounts: List<AccountSummary>,
    sharedNames: Set<String>,
): List<HistoryWindow> = accounts.flatMap { account ->
    account.windows
        .filter { it.canAlert }
        .ifEmpty { listOfNotNull(account.primary) }
        .map { window ->
            HistoryWindow(
                key = "${account.id}/${window.id}",
                title = windowTitle(account, window, sharedNames),
                past = window.pastResets,
                current = window.usedPercent,
            )
        }
}

/**
 * "Account · window". When another account has the same name, the account's label (such as its
 * email address) tells the two apart.
 */
@Composable
@ReadOnlyComposable
private fun windowTitle(
    account: AccountSummary,
    window: WindowSummary,
    sharedNames: Set<String>,
): String {
    val name =
        if (account.name in sharedNames && account.label != account.name) {
            stringResource(R.string.resets_account_with_label, account.name, account.label)
        } else {
            account.name
        }
    return stringResource(R.string.resets_row_title, name, window.label)
}

@Composable
private fun UpcomingRow(
    account: AccountSummary,
    window: WindowSummary,
    title: String,
    state: HomeUiState,
    formatter: ResetFormatter,
    onOpen: () -> Unit,
    onAlertChange: (Boolean) -> Unit,
) {
    val at = window.resetsAt ?: return
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .testTag(resetRowTag(account.id, window.id))
                .clickable(
                    onClickLabel = stringResource(R.string.resets_open_details),
                    onClick = onOpen,
                )
                .heightIn(min = 64.dp)
                .padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProviderAvatar(provider = account.provider, size = 30.dp)
        Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
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
        // The list only holds windows that can alert. Any other window gets no switch at all: a
        // switch that can never be turned on only raises the question why.
        if (window.canAlert) {
            AlertToggle(
                checked = window.alertEnabled,
                description = stringResource(R.string.resets_alert_toggle, title),
                onCheckedChange = onAlertChange,
                modifier = Modifier.testTag(resetAlertTag(account.id, window.id)),
            )
        }
    }
}

/** The bell: a toggle button that fills and changes shape when the alert is on. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AlertToggle(
    checked: Boolean,
    description: String,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    FilledTonalIconToggleButton(
        checked = checked,
        onCheckedChange = onCheckedChange,
        shapes = IconButtonDefaults.toggleableShapes(),
        colors =
            IconButtonDefaults.filledTonalIconToggleButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                checkedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                checkedContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
        modifier = modifier.semantics { contentDescription = description },
    ) {
        Icon(
            painter =
                painterResource(
                    if (checked) HeadroomIcons.NotificationsActiveFilled
                    else HeadroomIcons.NotificationsOff
                ),
            contentDescription = null,
        )
    }
}
