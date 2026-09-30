package dev.sebastiano.headroom.ui.overview

import androidx.compose.animation.core.FiniteAnimationSpec
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.sebastiano.headroom.R
import dev.sebastiano.headroom.designsystem.HeadroomMotion
import dev.sebastiano.headroom.designsystem.animationsEnabled
import dev.sebastiano.headroom.model.OverviewSort
import dev.sebastiano.headroom.ui.PageReveal
import dev.sebastiano.headroom.ui.ResetFormatter
import dev.sebastiano.headroom.ui.SettingsButton
import dev.sebastiano.headroom.ui.SharedElements
import dev.sebastiano.headroom.ui.components.ScreenHeader
import dev.sebastiano.headroom.ui.components.SectionLabel
import dev.sebastiano.headroom.ui.components.StatusBarBlurBox
import dev.sebastiano.headroom.ui.delights.NEXT_RESET_ANCHOR
import dev.sebastiano.headroom.ui.delights.delightAnchor
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
    onSortChange: (OverviewSort) -> Unit,
    modifier: Modifier = Modifier,
    columns: Int = 1,
    selectedAccountId: String? = null,
    bottomPadding: androidx.compose.ui.unit.Dp = 0.dp,
    sharedElements: SharedElements? = null,
    settingsReveal: PageReveal? = null,
    playEntrance: Boolean = false,
    onEntranceStart: () -> Unit = {},
    onSignInAgain: (accountId: String) -> Unit = {},
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
        val placement = cardPlacementSpec(state.sortLoaded)
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
                fullWidth("header") { OverviewHeader(state, onOpenSettings, settingsReveal) }
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
                            modifier = Modifier.delightAnchor(NEXT_RESET_ANCHOR),
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
                    fullWidth("section") { SectionHeader(state, onSortChange) }
                    itemsIndexed(state.accounts, key = { _, account -> account.id }) {
                        index,
                        account ->
                        StaggeredEntrance(
                            index = index,
                            play = playEntrance,
                            modifier =
                                Modifier.animateItem(
                                    fadeInSpec = null,
                                    placementSpec = placement,
                                    fadeOutSpec = null,
                                ),
                        ) {
                            AccountCard(
                                account = account,
                                now = state.now,
                                formatter = formatter,
                                onClick = { onOpenAccount(account.id) },
                                modifier = Modifier.delightAnchor(account.id),
                                selected = account.id == selectedAccountId,
                                sharedElements = sharedElements,
                                display = state.display,
                                onSignIn = { onSignInAgain(account.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * How a card moves to its new place when the order changes: the container spring, as for every
 * card. Null means the cards snap: when motion is reduced, and until the stored sort is known, so
 * opening the app never plays a reorder from the repository's order into the stored one. The effect
 * turns the motion on only after the frame that first has the stored sort is laid out.
 */
@Composable
private fun cardPlacementSpec(sortLoaded: Boolean): FiniteAnimationSpec<IntOffset>? {
    var settled by remember { mutableStateOf(sortLoaded) }
    SideEffect { if (sortLoaded) settled = true }
    return if (settled && animationsEnabled()) HeadroomMotion.containerSpec() else null
}

/** "This week", with the sort control at the end when there is more than one card to sort. */
@Composable
private fun SectionHeader(state: HomeUiState, onSortChange: (OverviewSort) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        SectionLabel(stringResource(R.string.overview_this_week))
        if (state.accounts.size > 1) {
            OverviewSortControl(
                sort = state.overviewSort,
                display = state.display,
                onSortChange = onSortChange,
            )
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
    onOpenSettings: () -> Unit,
    settingsReveal: PageReveal?,
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
        SettingsButton(onClick = onOpenSettings, reveal = settingsReveal)
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
