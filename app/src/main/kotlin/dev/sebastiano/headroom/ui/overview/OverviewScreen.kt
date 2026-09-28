package dev.sebastiano.headroom.ui.overview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.sebastiano.headroom.R
import dev.sebastiano.headroom.designsystem.HeadroomIcons
import dev.sebastiano.headroom.ui.ResetFormatter
import dev.sebastiano.headroom.ui.SharedElements
import dev.sebastiano.headroom.ui.components.ScreenHeader
import dev.sebastiano.headroom.ui.components.SectionLabel
import dev.sebastiano.headroom.ui.components.StatusBarBlurBox
import dev.sebastiano.headroom.ui.home.HomeUiState
import java.time.Duration
import java.time.Instant

const val OVERVIEW_LIST_TAG: String = "overview-list"
const val DEMO_BANNER_TAG: String = "demo-banner"

/**
 * The overview: title, demo banner, the next reset, and one card per account. Pull down to refresh;
 * the expressive loading indicator shows while the sync runs.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OverviewScreen(
    state: HomeUiState,
    formatter: ResetFormatter,
    onRefresh: () -> Unit,
    onOpenAccount: (String) -> Unit,
    onNextResetAlertChange: (Boolean) -> Unit,
    onAllResets: () -> Unit,
    onOpenAccounts: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    columns: Int = 1,
    selectedAccountId: String? = null,
    bottomPadding: androidx.compose.ui.unit.Dp = 0.dp,
    sharedElements: SharedElements? = null,
    playEntrance: Boolean = false,
    onEntranceStart: () -> Unit = {},
) {
    // Cards composed from now on (on scroll, or on coming back) appear without an entrance.
    SideEffect { if (playEntrance) onEntranceStart() }
    val pullState = rememberPullToRefreshState()
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = onRefresh,
        state = pullState,
        modifier = modifier.fillMaxSize(),
        indicator = {
            PullToRefreshDefaults.LoadingIndicator(
                state = pullState,
                isRefreshing = state.isRefreshing,
                modifier =
                    Modifier.align(Alignment.TopCenter).padding(top = insets.calculateTopPadding()),
            )
        },
    ) {
        val gridState = rememberLazyGridState()
        StatusBarBlurBox(scrollState = gridState, modifier = Modifier.fillMaxSize()) {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(columns),
                modifier =
                    Modifier.fillMaxSize()
                        .windowInsetsPadding(
                            WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)
                        )
                        .testTag(OVERVIEW_LIST_TAG),
                contentPadding =
                    PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = insets.calculateTopPadding() + 8.dp,
                        bottom = insets.calculateBottomPadding() + bottomPadding + 16.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                fullWidth("header") { OverviewHeader(state, onOpenAccounts, onOpenSettings) }
                if (state.isDemo) {
                    fullWidth("demo") { DemoBanner(onAddAccount = onOpenAccounts) }
                }
                state.nextReset?.let { next ->
                    fullWidth("next") {
                        NextResetCard(
                            next = next,
                            now = state.now,
                            formatter = formatter,
                            onAlertChange = onNextResetAlertChange,
                            onAllResets = onAllResets,
                        )
                    }
                }
                if (state.accounts.isEmpty()) {
                    fullWidth("empty") {
                        Text(
                            text = stringResource(R.string.overview_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(8.dp),
                        )
                    }
                } else {
                    fullWidth("section") {
                        SectionLabel(stringResource(R.string.overview_this_week))
                    }
                    itemsIndexed(state.accounts, key = { _, account -> account.id }) {
                        index,
                        account ->
                        StaggeredEntrance(index = index, play = playEntrance) {
                            AccountCard(
                                account = account,
                                now = state.now,
                                formatter = formatter,
                                onClick = { onOpenAccount(account.id) },
                                selected = account.id == selectedAccountId,
                                sharedElements = sharedElements,
                                display = state.display,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.grid.LazyGridScope.fullWidth(
    key: String,
    content: @Composable () -> Unit,
) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }
}

@Composable
private fun OverviewHeader(
    state: HomeUiState,
    onOpenAccounts: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val accounts =
        pluralStringResource(R.plurals.overview_accounts, state.accounts.size, state.accounts.size)
    ScreenHeader(
        title = stringResource(R.string.app_name),
        subtitle =
            stringResource(
                R.string.overview_subtitle,
                accounts,
                syncedText(state.lastSyncedAt, state.now),
            ),
    ) {
        val settingsLabel = stringResource(R.string.action_settings)
        IconButton(
            onClick = onOpenSettings,
            modifier = Modifier.semantics { contentDescription = settingsLabel },
        ) {
            Icon(painter = painterResource(HeadroomIcons.SettingsFilled), contentDescription = null)
        }
        val accountsLabel = stringResource(R.string.action_accounts)
        FilledTonalIconButton(
            onClick = onOpenAccounts,
            modifier = Modifier.semantics { contentDescription = accountsLabel },
            shape = CircleShape,
        ) {
            val initial = state.accounts.firstOrNull()?.label?.firstOrNull()?.uppercaseChar() ?: '+'
            Text(text = initial.toString(), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
@ReadOnlyComposable
private fun syncedText(lastSyncedAt: Instant?, now: Instant): String {
    if (lastSyncedAt == null) return stringResource(R.string.overview_never_synced)
    val minutes = Duration.between(lastSyncedAt, now).toMinutes().coerceAtLeast(0)
    return when {
        minutes < 1 -> stringResource(R.string.overview_synced_just_now)
        minutes < MINUTES_PER_HOUR ->
            pluralStringResource(
                R.plurals.overview_synced_minutes,
                minutes.toInt(),
                minutes.toInt(),
            )
        else -> {
            val hours = (minutes / MINUTES_PER_HOUR).toInt()
            pluralStringResource(R.plurals.overview_synced_hours, hours, hours)
        }
    }
}

private const val MINUTES_PER_HOUR = 60L

@Composable
private fun DemoBanner(onAddAccount: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().testTag(DEMO_BANNER_TAG),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.overview_demo_label),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = stringResource(R.string.overview_demo_body),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            TextButton(onClick = onAddAccount) {
                Text(stringResource(R.string.overview_demo_action))
            }
        }
    }
}
