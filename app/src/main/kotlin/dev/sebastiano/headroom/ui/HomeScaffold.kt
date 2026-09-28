package dev.sebastiano.headroom.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.PaneAdaptedValue
import androidx.compose.material3.adaptive.layout.calculateDefaultEnterTransition
import androidx.compose.material3.adaptive.layout.calculateDefaultExitTransition
import androidx.compose.material3.adaptive.navigation.NavigableListDetailPaneScaffold
import androidx.compose.material3.adaptive.navigation.ThreePaneScaffoldNavigator
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteItem
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.sebastiano.headroom.R
import dev.sebastiano.headroom.designsystem.HeadroomIcons
import dev.sebastiano.headroom.designsystem.animationsEnabled
import dev.sebastiano.headroom.model.OverviewSort
import dev.sebastiano.headroom.ui.detail.DetailScreen
import dev.sebastiano.headroom.ui.home.DetailUiState
import dev.sebastiano.headroom.ui.home.HomeUiState
import dev.sebastiano.headroom.ui.overview.OverviewScreen
import dev.sebastiano.headroom.ui.resets.ResetsScreen
import dev.sebastiano.headroom.ui.widgets.WidgetsScreen
import dev.sebastiano.headroom.widgets.WidgetStyle
import kotlinx.coroutines.launch

const val TOOLBAR_TAG: String = "floating-toolbar"
const val REFRESH_TAG: String = "refresh"

/**
 * Navigation for every width. Compact: a floating toolbar with the refresh button, and the detail
 * full screen. Medium: a navigation rail and two columns of cards. Expanded: the rail, and the list
 * and the detail side by side. The list and detail panes use [NavigableListDetailPaneScaffold],
 * which also handles predictive back.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
internal fun HomeScaffold(
    home: HomeUiState,
    detail: DetailUiState?,
    formatter: ResetFormatter,
    onRefresh: () -> Unit,
    onSelectAccount: (String) -> Unit,
    onAlertChange: (String, String, Boolean) -> Unit,
    onChartWindowChange: (String) -> Unit,
    onSortChange: (OverviewSort) -> Unit,
    onOpenAccounts: () -> Unit,
    onOpenSettings: () -> Unit,
    onAddWidget: (WidgetStyle) -> Unit,
    modifier: Modifier = Modifier,
    openAccountRequest: OpenAccountRequest? = null,
    onConsumeOpenAccount: () -> Unit = {},
    settingsReveal: PageReveal? = null,
    playEntrance: Boolean = false,
    onEntranceStart: () -> Unit = {},
) {
    val width = layoutWidth()
    var tab by rememberSaveable { mutableStateOf(HomeTab.Overview) }
    val navigator = rememberListDetailPaneScaffoldNavigator<String>()
    val scope = rememberCoroutineScope()
    val detailOnly =
        navigator.scaffoldValue[ListDetailPaneScaffoldRole.List] == PaneAdaptedValue.Hidden
    val selectTab: (HomeTab) -> Unit = { next ->
        if (next != tab && tab == HomeTab.Overview && detailOnly) {
            scope.launch { navigator.navigateBack() }
        }
        tab = next
    }
    val opener =
        rememberAccountOpener(openAccountRequest, onConsumeOpenAccount) { tab = HomeTab.Overview }
    val suiteType =
        if (width == LayoutWidth.Compact) NavigationSuiteType.None
        else NavigationSuiteType.WideNavigationRailCollapsed

    NavigationSuiteScaffold(
        navigationItems = { HomeNavigationItems(tab, suiteType, selectTab) },
        navigationSuiteType = suiteType,
        primaryActionContent = {
            if (width != LayoutWidth.Compact) {
                RefreshButton(
                    refreshing = home.isRefreshing,
                    onRefresh = onRefresh,
                    inToolbar = false,
                )
            }
        },
        modifier = modifier,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val compact = width == LayoutWidth.Compact
            val bottomPadding = if (compact) ToolbarClearance else 0.dp
            val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
            val fast = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
            // With motion reduced, tabs and the toolbar only fade.
            val animate = animationsEnabled()
            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    if (!animate) fadeIn(fast) togetherWith fadeOut(fast)
                    else
                        (fadeIn(effects) +
                            scaleIn(effects, initialScale = FADE_THROUGH_SCALE)) togetherWith
                            fadeOut(fast)
                },
                label = "tab",
            ) { current ->
                when (current) {
                    HomeTab.Overview ->
                        OverviewPanes(
                            navigator = navigator,
                            width = width,
                            home = home,
                            detail = detail,
                            formatter = formatter,
                            onRefresh = onRefresh,
                            onSelectAccount = onSelectAccount,
                            onAlertChange = onAlertChange,
                            onChartWindowChange = onChartWindowChange,
                            onSortChange = onSortChange,
                            onAllResets = { selectTab(HomeTab.Resets) },
                            onOpenAccounts = onOpenAccounts,
                            onOpenSettings = onOpenSettings,
                            settingsReveal = settingsReveal,
                            bottomPadding = bottomPadding,
                            playEntrance = playEntrance,
                            onEntranceStart = onEntranceStart,
                            opener = opener,
                        )
                    HomeTab.Resets ->
                        ResetsScreen(
                            state = home,
                            formatter = formatter,
                            onOpenAccount = opener::open,
                            onAlertChange = onAlertChange,
                            bottomPadding = bottomPadding,
                        )
                    HomeTab.Widgets ->
                        WidgetsScreen(home, formatter, onAddWidget, bottomPadding = bottomPadding)
                }
            }
            ToolbarSlot(
                visible = compact && !(tab == HomeTab.Overview && detailOnly),
                animate = animate,
                modifier =
                    Modifier.align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 16.dp),
            ) {
                HomeToolbar(tab, selectTab, home.isRefreshing, onRefresh)
            }
        }
    }
}

/** The floating toolbar slides up into view, or only fades when motion is reduced. */
@Composable
private fun ToolbarSlot(
    visible: Boolean,
    animate: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val fast = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val spatial =
        MaterialTheme.motionScheme.defaultSpatialSpec<androidx.compose.ui.unit.IntOffset>()
    AnimatedVisibility(
        visible = visible,
        enter = if (animate) slideInVertically(spatial) { it } + fadeIn(effects) else fadeIn(fast),
        exit = if (animate) slideOutVertically(spatial) { it } + fadeOut(fast) else fadeOut(fast),
        modifier = modifier,
    ) {
        content()
    }
}

/**
 * The scopes a card and the detail share for the container transform, or null when [enabled] is
 * false: in the two-pane layout, and when motion is reduced.
 */
@Composable
private fun rememberSharedElements(
    transitionScope: SharedTransitionScope,
    visibilityScope: AnimatedVisibilityScope,
    enabled: Boolean,
): SharedElements? {
    if (!enabled) return null
    val containerSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Rect>()
    val valueSpec = MaterialTheme.motionScheme.slowEffectsSpec<Rect>()
    return remember(transitionScope, visibilityScope, containerSpec, valueSpec) {
        SharedElements(
            transitionScope = transitionScope,
            visibilityScope = visibilityScope,
            containerTransform = BoundsTransform { _, _ -> containerSpec },
            valueTransform = BoundsTransform { _, _ -> valueSpec },
            containerClip = transitionScope.OverlayClip(CardShape),
        )
    }
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
private fun OverviewPanes(
    navigator: ThreePaneScaffoldNavigator<String>,
    width: LayoutWidth,
    home: HomeUiState,
    detail: DetailUiState?,
    formatter: ResetFormatter,
    onRefresh: () -> Unit,
    onSelectAccount: (String) -> Unit,
    onAlertChange: (String, String, Boolean) -> Unit,
    onChartWindowChange: (String) -> Unit,
    onSortChange: (OverviewSort) -> Unit,
    onAllResets: () -> Unit,
    onOpenAccounts: () -> Unit,
    onOpenSettings: () -> Unit,
    settingsReveal: PageReveal?,
    bottomPadding: androidx.compose.ui.unit.Dp,
    playEntrance: Boolean,
    onEntranceStart: () -> Unit,
    opener: AccountOpener,
) {
    OpenDetailEffect(navigator, opener, onSelectAccount)
    val scope = rememberCoroutineScope()
    val twoPanes = width == LayoutWidth.Expanded
    // With motion reduced, the card does not turn into the detail: the panes only fade.
    val animate = animationsEnabled()
    val fast = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    SharedTransitionLayout {
        val transitionScope = this
        NavigableListDetailPaneScaffold(
            navigator = navigator,
            listPane = {
                AnimatedPane(
                    enterTransition =
                        if (animate) motionDataProvider.calculateDefaultEnterTransition(paneRole)
                        else fadeIn(fast),
                    exitTransition =
                        if (animate) motionDataProvider.calculateDefaultExitTransition(paneRole)
                        else fadeOut(fast),
                ) {
                    val shared = rememberSharedElements(transitionScope, this, !twoPanes && animate)
                    OverviewScreen(
                        state = home,
                        formatter = formatter,
                        onRefresh = onRefresh,
                        onOpenAccount = { id ->
                            onSelectAccount(id)
                            scope.launch {
                                navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, id)
                            }
                        },
                        onNextResetAlertChange = { enabled ->
                            home.nextReset?.let {
                                onAlertChange(it.accountId, it.windowId, enabled)
                            }
                        },
                        onAllResets = onAllResets,
                        onOpenAccounts = onOpenAccounts,
                        onOpenSettings = onOpenSettings,
                        onSortChange = onSortChange,
                        settingsReveal = settingsReveal,
                        columns = if (width == LayoutWidth.Medium) 2 else 1,
                        selectedAccountId = if (twoPanes) detail?.account?.id else null,
                        bottomPadding = bottomPadding,
                        sharedElements = shared,
                        playEntrance = playEntrance,
                        onEntranceStart = onEntranceStart,
                    )
                }
            },
            detailPane = {
                AnimatedPane(
                    enterTransition =
                        if (animate) motionDataProvider.calculateDefaultEnterTransition(paneRole)
                        else fadeIn(fast),
                    exitTransition =
                        if (animate) motionDataProvider.calculateDefaultExitTransition(paneRole)
                        else fadeOut(fast),
                ) {
                    val current = detail ?: return@AnimatedPane
                    if (twoPanes) {
                        DetailPane(current, formatter, onAlertChange, onChartWindowChange)
                    } else {
                        val shared = rememberSharedElements(transitionScope, this, animate)
                        DetailScreen(
                            state = current,
                            formatter = formatter,
                            onAlertChange = onAlertChange,
                            onChartWindowChange = onChartWindowChange,
                            onBack = { scope.launch { navigator.navigateBack() } },
                            sharedElements = shared,
                            modifier = Modifier.background(MaterialTheme.colorScheme.surface),
                        )
                    }
                }
            },
        )
    }
}

@Composable
private fun HomeNavigationItems(
    tab: HomeTab,
    suiteType: NavigationSuiteType,
    onSelect: (HomeTab) -> Unit,
) {
    HomeTab.entries.forEach { item ->
        NavigationSuiteItem(
            selected = item == tab,
            onClick = { onSelect(item) },
            icon = {
                Icon(
                    painter = painterResource(if (item == tab) item.selectedIcon else item.icon),
                    contentDescription = null,
                )
            },
            label = { Text(stringResource(item.label)) },
            navigationSuiteType = suiteType,
        )
    }
}

/**
 * Opens the requested account. The caller passes only requests for accounts on screen; see
 * [decideOpenAccount].
 */
@Composable
private fun OpenAccountEffect(
    request: OpenAccountRequest?,
    onOpen: suspend (String) -> Unit,
    onConsume: () -> Unit,
) {
    val open by rememberUpdatedState(onOpen)
    val consume by rememberUpdatedState(onConsume)
    LaunchedEffect(request) {
        val pending = request ?: return@LaunchedEffect
        open(pending.accountId)
        consume()
    }
}

/**
 * The account whose detail opens next. A widget tap and a tap on the Resets tab both open an
 * account through it: [open] switches to the Overview tab, and [OpenDetailEffect] then shows the
 * detail.
 */
@Stable
internal class AccountOpener(private val onShowOverview: () -> Unit) {
    var pending: String? by mutableStateOf(null)
        private set

    fun open(accountId: String) {
        onShowOverview()
        pending = accountId
    }

    fun consume() {
        pending = null
    }
}

/** An [AccountOpener] that also opens the accounts that [request] asks for, such as widget taps. */
@Composable
private fun rememberAccountOpener(
    request: OpenAccountRequest?,
    onConsumeRequest: () -> Unit,
    onShowOverview: () -> Unit,
): AccountOpener {
    val showOverview by rememberUpdatedState(onShowOverview)
    val opener = remember { AccountOpener { showOverview() } }
    OpenAccountEffect(request, { opener.open(it) }, onConsumeRequest)
    return opener
}

/**
 * Selects the account [opener] holds and shows its detail. It must be composed with the list-detail
 * scaffold: a navigator whose scaffold is not on screen, for example while the Resets tab shows,
 * ignores the request.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
private fun OpenDetailEffect(
    navigator: ThreePaneScaffoldNavigator<String>,
    opener: AccountOpener,
    onSelect: (String) -> Unit,
) {
    val select by rememberUpdatedState(onSelect)
    val accountId = opener.pending
    LaunchedEffect(accountId) {
        val pending = accountId ?: return@LaunchedEffect
        select(pending)
        navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, pending)
        opener.consume()
    }
}

/** The expanded detail pane: a rounded panel that fades through to a newly selected account. */
@Composable
private fun DetailPane(
    detail: DetailUiState,
    formatter: ResetFormatter,
    onAlertChange: (String, String, Boolean) -> Unit,
    onChartWindowChange: (String) -> Unit,
) {
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val fast = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val animate = animationsEnabled()
    AnimatedContent(
        targetState = detail,
        contentKey = { it.account.id },
        transitionSpec = {
            if (!animate) fadeIn(fast) togetherWith fadeOut(fast)
            else
                (fadeIn(effects) + scaleIn(effects, initialScale = PANE_SCALE)) togetherWith
                    fadeOut(fast)
        },
        modifier =
            Modifier.fillMaxSize()
                .statusBarsPadding()
                .padding(top = 8.dp, end = 12.dp, bottom = 12.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow),
        label = "detail pane",
    ) { shown ->
        DetailScreen(
            state = shown,
            formatter = formatter,
            onAlertChange = onAlertChange,
            onChartWindowChange = onChartWindowChange,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HomeToolbar(
    tab: HomeTab,
    onSelect: (HomeTab) -> Unit,
    refreshing: Boolean,
    onRefresh: () -> Unit,
) {
    HorizontalFloatingToolbar(
        expanded = true,
        floatingActionButton = {
            RefreshButton(refreshing = refreshing, onRefresh = onRefresh, inToolbar = true)
        },
        modifier = Modifier.testTag(TOOLBAR_TAG),
    ) {
        HomeTab.entries.forEach { item ->
            val selected = item == tab
            val label = stringResource(item.label)
            IconButton(
                onClick = { onSelect(item) },
                colors =
                    if (selected) {
                        IconButtonDefaults.filledIconButtonColors()
                    } else {
                        IconButtonDefaults.iconButtonColors()
                    },
                modifier =
                    Modifier.width(56.dp).semantics {
                        contentDescription = label
                        this.selected = selected
                    },
            ) {
                Icon(
                    painter = painterResource(if (selected) item.selectedIcon else item.icon),
                    contentDescription = null,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RefreshButton(refreshing: Boolean, onRefresh: () -> Unit, inToolbar: Boolean) {
    val label =
        stringResource(if (refreshing) R.string.action_refreshing else R.string.action_refresh)
    val modifier = Modifier.testTag(REFRESH_TAG).semantics { contentDescription = label }
    val content: @Composable () -> Unit = {
        if (refreshing) {
            LoadingIndicator(modifier = Modifier.size(32.dp))
        } else {
            Icon(painter = painterResource(HeadroomIcons.Sync), contentDescription = null)
        }
    }
    if (inToolbar) {
        FloatingToolbarDefaults.VibrantFloatingActionButton(
            onClick = onRefresh,
            modifier = modifier,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            content = content,
        )
    } else {
        FloatingActionButton(
            onClick = onRefresh,
            modifier = modifier,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            content = content,
        )
    }
}

private val CardShape = RoundedCornerShape(24.dp)
private val ToolbarClearance = 96.dp
private const val FADE_THROUGH_SCALE = 0.96f
private const val PANE_SCALE = 0.98f
