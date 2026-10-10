package dev.sebastiano.headroom.ui

import android.content.ClipData
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.sebastiano.headroom.AppGraph
import dev.sebastiano.headroom.R
import dev.sebastiano.headroom.designsystem.animationsEnabled
import dev.sebastiano.headroom.island.ResetIslandAccess
import dev.sebastiano.headroom.signin.SignInState
import dev.sebastiano.headroom.signin.signInTabIntent
import dev.sebastiano.headroom.tile.TileAddResult
import dev.sebastiano.headroom.tile.TileSubtitleMode
import dev.sebastiano.headroom.ui.accounts.AccountsActions
import dev.sebastiano.headroom.ui.accounts.AccountsScreen
import dev.sebastiano.headroom.ui.accounts.AccountsStep
import dev.sebastiano.headroom.ui.accounts.AccountsViewModel
import dev.sebastiano.headroom.ui.components.rememberResetFormatter
import dev.sebastiano.headroom.ui.delights.HomeDelights
import dev.sebastiano.headroom.ui.home.HomeUiState
import dev.sebastiano.headroom.ui.home.HomeViewModel
import dev.sebastiano.headroom.ui.settings.LicencesScreen
import dev.sebastiano.headroom.ui.settings.ResetIslandUi
import dev.sebastiano.headroom.ui.settings.SettingsAccounts
import dev.sebastiano.headroom.ui.settings.SettingsActions
import dev.sebastiano.headroom.ui.settings.SettingsScreen
import dev.sebastiano.headroom.ui.settings.SettingsUiState
import dev.sebastiano.headroom.ui.settings.SettingsViewModel
import dev.sebastiano.headroom.ui.stats.StatsScreen
import dev.sebastiano.headroom.ui.stats.StatsViewModel
import dev.sebastiano.headroom.widgets.WidgetPinner
import dev.sebastiano.headroom.widgets.WidgetStyle
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.launch

/**
 * The whole app: the home scaffold (overview, resets, stats, and the account detail), and the
 * accounts and settings pages on top of it. State comes from the view models; this composable only
 * wires it to the screens.
 */
@Composable
fun HeadroomApp(
    graph: AppGraph,
    modifier: Modifier = Modifier,
    openAccountRequest: OpenAccountRequest? = null,
    onConsumeOpenAccount: () -> Unit = {},
    openTileSettingsRequest: Long? = null,
    onConsumeOpenTileSettings: () -> Unit = {},
    homeViewModel: HomeViewModel = viewModel(factory = graph.homeViewModelFactory),
    accountsViewModel: AccountsViewModel = viewModel(factory = graph.accountsViewModelFactory),
    settingsViewModel: SettingsViewModel = viewModel(factory = graph.settingsViewModelFactory),
    statsViewModel: StatsViewModel = viewModel(factory = graph.statsViewModelFactory),
) {
    val live by homeViewModel.state.collectAsStateWithLifecycle()
    val liveDetail by homeViewModel.detail.collectAsStateWithLifecycle()
    val accounts by accountsViewModel.state.collectAsStateWithLifecycle()
    val settings by settingsViewModel.state.collectAsStateWithLifecycle()
    val resets = collectResets(graph)
    val formatter = rememberResetFormatter(graph.zone)
    val snackbar = remember { SnackbarHostState() }
    val addWidget = rememberWidgetAdder(graph.widgetPinner, snackbar)
    var page by rememberSaveable { mutableStateOf(Page.Home) }
    // Accounts open from Settings, or from the demo banner's shortcut; back returns there.
    var accountsFromSettings by rememberSaveable { mutableStateOf(false) }
    // The cards' entrance plays on the first open only, not on returning to the overview.
    var entrancePlayed by rememberSaveable { mutableStateOf(false) }
    val sheet = rememberSaveable(saver = ResetSheetState.Saver) { ResetSheetState() }
    val (home, detail) = holdUnderSheet(live, liveDetail, sheet.request?.accountId)
    // A widget tap leaves the page on top, unless the user is in the middle of signing in.
    val signingIn = (accounts.step as? AccountsStep.SignIn)?.state?.isWaitingForUser() == true
    val requests = rememberPendingRequest(openAccountRequest, home, onConsumeOpenAccount)
    val openNow = requests.ready.takeIf { !signingIn && it?.signInAgain != true }
    val shown = if (openNow == null) page else Page.Home
    val signInAgain =
        rememberSignInAgain(accountsViewModel, accounts.step, requests.ready, requests::drop) {
            again ->
            if (again) accountsFromSettings = false
            page = if (again) Page.Accounts else Page.Home
        }

    val openPage: (Page) -> Unit = { next ->
        if (next == Page.Accounts) accountsFromSettings = true
        page = next
    }
    val tileScroll =
        rememberTileScroll(openTileSettingsRequest, onConsumeOpenTileSettings, requests, openPage)
    // Made once: this composable recomposes whenever the pages settle, for example as Settings'
    // reveal ends. New callbacks then would compose every row of Settings again.
    val settingsActions =
        remember(settingsViewModel, addWidget, graph.resetIsland) {
            settingsActions(settingsViewModel, addWidget, graph.resetIsland, openPage)
        }
    val accountsActions =
        accountsActions(accountsViewModel) {
                page = if (accountsFromSettings) Page.Settings else Page.Home
            }
            .withDeviceActions()

    Box(modifier = modifier.fillMaxSize()) {
        // Inside a sign-in, the accounts screen handles back itself, one step at a time.
        val back =
            (if (shown == Page.Accounts && accountsFromSettings) Page.Settings else shown.back)
                .takeIf { shown != Page.Accounts || accounts.step == AccountsStep.List }
        val pageTransition = rememberPageTransition(shown, back) { page = it }
        Pages(
            transition = rememberTransition(pageTransition.state, label = "page"),
            scrubbing = { pageTransition.scrubbing },
        ) { current, reveal ->
            when (current) {
                Page.Accounts -> AccountsScreen(state = accounts, actions = accountsActions)
                Page.Settings ->
                    SettingsPage(graph, settings, home, settingsActions, reveal, tileScroll)
                Page.Prototypes ->
                    PrototypesPage(graph, home, formatter, onBack = { page = Page.Settings })
                Page.Licences -> LicencesScreen(onBack = { page = Page.Settings })
                Page.Home ->
                    HomeScaffold(
                        home = home,
                        detail = detail,
                        formatter = formatter,
                        onRefresh = homeViewModel::refresh,
                        onSelectAccount = homeViewModel::select,
                        onAlertChange = homeViewModel::setAlert,
                        onChartWindowChange = homeViewModel::selectChartWindow,
                        onSortChange = homeViewModel::setOverviewSort,
                        onOpenAccounts = {
                            requests.drop()
                            accountsFromSettings = false
                            page = Page.Accounts
                        },
                        onOpenSettings = {
                            requests.drop()
                            page = Page.Settings
                        },
                        settingsReveal = reveal,
                        playEntrance = !entrancePlayed,
                        onEntranceStart = { entrancePlayed = true },
                        onSignInAgain = signInAgain,
                        openAccountRequest = openNow,
                        onConsumeOpenAccount = {
                            page = Page.Home
                            requests.handle(openNow?.serial)
                        },
                        resets = resets,
                        resetHandlers = sheet.handlers(),
                        stats = { bottomPadding ->
                            // Collected only while the tab shows: no stats work off screen.
                            val stats by statsViewModel.state.collectAsStateWithLifecycle()
                            StatsScreen(stats, formatter, bottomPadding = bottomPadding)
                        },
                    )
            }
        }
        AppSnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
    HomeDelights(home = home, onBurstFinish = homeViewModel::onResetBurstShown)
    ResetSheetHost(sheet, graph, live, resets, formatter) {
        accountsFromSettings = false
        page = Page.Accounts
    }
}

/** Settings, with the Quick Settings tile's row and the debug build's prototypes entry. */
@Composable
private fun SettingsPage(
    graph: AppGraph,
    settings: SettingsUiState,
    home: HomeUiState,
    actions: SettingsActions,
    reveal: PageReveal,
    tileScroll: TileScroll,
) {
    var tileStatus by rememberSaveable { mutableStateOf<TileAddResult?>(null) }
    // Leaving Settings before the scroll to the tile's rows ends cancels it. A configuration
    // change keeps it: Settings comes back and finishes the scroll.
    val activity = LocalActivity.current
    DisposableEffect(tileScroll, activity) {
        onDispose { if (activity?.isChangingConfigurations != true) tileScroll.pending = false }
    }
    val tileSubtitle = graph.tileSettings?.subtitle?.collectAsStateWithLifecycle()?.value
    // The same callbacks and debug row on every recomposition, so Settings' rows can skip.
    val pageActions =
        remember(actions, graph) {
            actions.copy(
                onAddTile = { graph.tileAdder.request { tileStatus = it } },
                onTileSubtitleChange = { graph.tileSettings?.setSubtitle(it) },
            )
        }
    val debugEntry =
        remember(actions, graph) { graph.prototypes?.settingsEntry { actions.onOpenPrototypes() } }
    SettingsScreen(
        state = settings,
        accounts = SettingsAccounts(home.accountsInYourOrder.map { it.provider }, home.isDemo),
        actions = pageActions,
        reveal = reveal,
        resetIsland = graph.resetIsland.collectUi(),
        tileStatus = tileStatus,
        tileSubtitle = tileSubtitle ?: TileSubtitleMode.NextReset,
        debugEntry = debugEntry,
        scrollToTile = tileScroll.pending,
        onScrollToTileFinish = { tileScroll.pending = false },
    )
}

/** Whether Settings still has to scroll to the Quick Settings tile's rows. */
@Stable
private class TileScroll(pending: Boolean = false) {
    var pending by mutableStateOf(pending)

    companion object {
        /** Keeps a scroll that has not finished across a configuration change. */
        val Saver: Saver<TileScroll, Boolean> = Saver({ it.pending }, ::TileScroll)
    }
}

/**
 * Handles a long press on the Quick Settings tile, by [request] serial: [onOpenPage] shows
 * Settings, the returned [TileScroll] asks it to scroll to the tile's rows, and [onConsume] tells
 * the host to clear the request. The long press wins over an earlier widget tap in [pending], like
 * the user's own taps.
 */
@Composable
private fun rememberTileScroll(
    request: Long?,
    onConsume: () -> Unit,
    pending: PendingRequest,
    onOpenPage: (Page) -> Unit,
): TileScroll {
    val scroll = rememberSaveable(saver = TileScroll.Saver) { TileScroll() }
    var handled by remember { mutableStateOf<Long?>(null) }
    SideEffect {
        if (request == null || request == handled) return@SideEffect
        handled = request
        pending.drop()
        onOpenPage(Page.Settings)
        scroll.pending = true
        onConsume()
    }
    return scroll
}

private const val ENTER_SCALE = 0.96f

/** An account request, from a widget tap or a sign-in warning, that the UI has not acted on yet. */
private class PendingRequest(
    /** The request, once the accounts are known well enough to act on it. */
    val ready: OpenAccountRequest?,
    private val pending: OpenAccountRequest?,
    private val markHandled: (serial: Long?) -> Unit,
) {
    /** Marks the request with [serial] as handled, and tells the host to clear it. */
    fun handle(serial: Long?) = markHandled(serial)

    /** Drops the pending request: the user's own tap wins over one that came earlier. */
    fun drop() {
        pending?.let { handle(it.serial) }
    }
}

/**
 * Tracks [request] until it is handled. A request is acted on once, even if the host is slow to
 * clear it: a request left behind must never keep the accounts screen from opening. Unknown
 * accounts are dropped once loading is done, see [decideOpenAccount].
 */
@Composable
private fun rememberPendingRequest(
    request: OpenAccountRequest?,
    home: HomeUiState,
    onConsume: () -> Unit,
): PendingRequest {
    var handledRequest by remember { mutableStateOf<Long?>(null) }
    val pending = request?.takeIf { it.serial != handledRequest }
    val decision = pending?.let {
        decideOpenAccount(
            it.accountId,
            home.accounts.map { account -> account.id },
            home.accountsLoaded,
        )
    }
    SideEffect { if (decision == OpenAccountDecision.Ignore) onConsume() }
    return PendingRequest(pending.takeIf { decision == OpenAccountDecision.Open }, pending) { serial
        ->
        handledRequest = serial
        onConsume()
    }
}

/**
 * Starts signing an account in again, from a card, the detail or a sign-in warning's [request].
 * [onPage] is called with true to show the accounts page with the sign-in, and with false once that
 * sign-in is over (done, cancelled or backed out of), to go back to the overview.
 */
@Composable
private fun rememberSignInAgain(
    viewModel: AccountsViewModel,
    step: AccountsStep,
    request: OpenAccountRequest?,
    onHandleRequest: () -> Unit,
    onPage: (signingInAgain: Boolean) -> Unit,
): (accountId: String) -> Unit {
    // Started, and whether its sign-in step has shown yet: a browser sign-in starts a moment late.
    var started by rememberSaveable { mutableStateOf(false) }
    var shown by rememberSaveable { mutableStateOf(false) }
    val signingIn = (step as? AccountsStep.SignIn)?.again != null
    val currentOnHandleRequest by rememberUpdatedState(onHandleRequest)
    val currentOnPage by rememberUpdatedState(onPage)
    // Remembered, so the screens it is passed to can skip recomposing.
    val start =
        remember(viewModel) {
            { accountId: String ->
                currentOnHandleRequest()
                viewModel.signInAgain(accountId)
                started = true
                shown = false
                currentOnPage(true)
            }
        }
    SideEffect {
        when {
            request?.signInAgain == true -> start(request.accountId)
            started && signingIn -> shown = true
            started && shown -> {
                started = false
                shown = false
                currentOnPage(false)
            }
        }
    }
    return start
}

/** The reset island's service state, as the settings screen shows it. */
@Composable
private fun ResetIslandAccess.collectUi(): ResetIslandUi {
    val ready by ready.collectAsStateWithLifecycle()
    val enabled by enabledInSettings.collectAsStateWithLifecycle()
    val overlay by overlayAllowed.collectAsStateWithLifecycle()
    return ResetIslandUi(ready, enabled, overlay)
}

/** Asks [pinner] for a widget, and says so in [snackbar] when the launcher cannot add it. */
@Composable
private fun rememberWidgetAdder(
    pinner: WidgetPinner,
    snackbar: SnackbarHostState,
): (WidgetStyle) -> Unit {
    val scope = rememberCoroutineScope()
    val unavailable = stringResource(R.string.widget_add_unavailable)
    return remember(pinner, snackbar, unavailable) {
        { style ->
            if (!pinner.requestPin(style)) scope.launch { snackbar.showSnackbar(unavailable) }
        }
    }
}

/**
 * The pages, one over the other. Settings opens from the overview with a circular reveal from the
 * settings button (see [SettingsReveal]); every other page fades and scales in. With motion reduced
 * or off, every page crossfades.
 */
@Composable
private fun Pages(
    transition: Transition<Page>,
    scrubbing: () -> Boolean,
    content: @Composable (Page, PageReveal) -> Unit,
) {
    val animate = animationsEnabled()
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val fast = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val isScrubbing by rememberUpdatedState(scrubbing)
    SharedTransitionLayout {
        val reveal = remember(this) { SettingsReveal(this) }
        transition.AnimatedContent(
            transitionSpec = {
                when {
                    !animate -> fadeIn(fast) togetherWith fadeOut(fast)
                    // Settings grows over the overview, which stays still underneath it.
                    initialState == Page.Home && targetState == Page.Settings ->
                        EnterTransition.None togetherWith
                            ExitTransition.KeepUntilTransitionsFinished
                    initialState == Page.Settings && targetState == Page.Home ->
                        (EnterTransition.None togetherWith
                                ExitTransition.KeepUntilTransitionsFinished)
                            .apply { targetContentZIndex = -1f }
                    else ->
                        (fadeIn(effects) +
                            scaleIn(effects, initialScale = ENTER_SCALE)) togetherWith fadeOut(fast)
                }
            }
        ) { current ->
            val scope = this
            val pageReveal =
                remember(reveal, scope, animate, transition) {
                    PageReveal(
                        reveal = reveal,
                        visibility = scope,
                        animate = animate,
                        isRevealing = { animate && transition.isBetween(Page.Home, Page.Settings) },
                        isScrubbing = { isScrubbing() },
                        isOpening = { transition.targetState == Page.Settings },
                    )
                }
            val clip = rememberRevealClip(pageReveal, enabled = current == Page.Settings)
            Box(modifier = Modifier.revealClip(clip)) { content(current, pageReveal) }
        }
    }
}

/** True while this transition runs between [first] and [second], in either direction. */
private fun Transition<Page>.isBetween(first: Page, second: Page): Boolean =
    (currentState == first && targetState == second) ||
        (currentState == second && targetState == first)

/** Snackbars sit above the floating toolbar. */
@Composable
private fun AppSnackbarHost(state: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(
        hostState = state,
        modifier = modifier.navigationBarsPadding().padding(bottom = 88.dp),
    )
}

/**
 * The settings screen's callbacks, wired to [viewModel]; [onAddWidget] pins a widget and
 * [onNavigate] goes to another page.
 */
private fun settingsActions(
    viewModel: SettingsViewModel,
    onAddWidget: (WidgetStyle) -> Unit,
    resetIsland: ResetIslandAccess,
    onNavigate: (Page) -> Unit,
) =
    SettingsActions(
        onClose = { onNavigate(Page.Home) },
        onQuotaDisplayChange = viewModel::setQuotaDisplay,
        onSyncFrequencyChange = viewModel::setSyncFrequency,
        onOpenLicences = { onNavigate(Page.Licences) },
        onOpenAccounts = { onNavigate(Page.Accounts) },
        onThemeChange = viewModel::setTheme,
        onMotionChange = viewModel::setMotion,
        onPaletteChange = viewModel::setPalette,
        onAddWidget = onAddWidget,
        onRefreshShimmerChange = viewModel::setRefreshShimmer,
        onResetConfettiChange = viewModel::setResetConfetti,
        onResetIslandChange = viewModel::setResetIsland,
        onRedeemClaudeResetsChange = viewModel::setRedeemClaudeResets,
        onResetExpiryRemindersChange = viewModel::setResetExpiryReminders,
        onTryResetIsland = { provider, message -> resetIsland.showDemo(provider, message) },
        onRefreshResetIsland = resetIsland::refresh,
        onOpenPrototypes = { onNavigate(Page.Prototypes) },
    )

/** These actions, with copying to the clipboard and opening links in a browser tab wired up. */
@Composable
private fun AccountsActions.withDeviceActions(): AccountsActions {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    return copy(
        onCopy = { text ->
            scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(null, text))) }
        },
        onOpenUrl = { url -> signInTabIntent(context).launchUrl(context, url.toUri()) },
    )
}

/**
 * The transition between the pages, seekable so the predictive back gesture scrubs the page on top
 * away. [scrubbing] is true from the start of a back gesture until the pages settle, so the reveal
 * can follow the finger one to one.
 */
@Stable
private class PageTransition(initial: Page) {
    val state = SeekableTransitionState(initial)
    var scrubbing by mutableStateOf(false)
}

/**
 * The page transition, scrubbed by the predictive back gesture towards [back]. [onBack] runs when
 * the gesture completes; a cancelled gesture settles back on [shown].
 */
@Composable
private fun rememberPageTransition(
    shown: Page,
    back: Page?,
    onBack: (Page) -> Unit,
): PageTransition {
    val pages = remember { PageTransition(shown) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(shown) {
        pages.state.animateTo(shown)
        pages.scrubbing = false
    }
    PredictiveBackHandler(enabled = back != null) { gesture ->
        val target = back ?: return@PredictiveBackHandler
        pages.scrubbing = true
        try {
            gesture.collect { event -> pages.state.seekTo(event.progress, target) }
            // The finger has let go: the rest plays at the closing pace, not following a finger.
            pages.scrubbing = false
            onBack(target)
        } catch (cancelled: CancellationException) {
            // The gesture's coroutine is cancelled; settle back from a live scope.
            scope.launch {
                pages.state.animateTo(shown)
                pages.scrubbing = false
            }
            throw cancelled
        }
    }
    return pages
}

/** The home scaffold, or a page drawn over it. Back from a page goes to [back]. */
private enum class Page {
    Home,
    Accounts,
    Settings,
    Licences,
    /** Debug builds only: the reset prototypes. */
    Prototypes;

    val back: Page?
        get() =
            when (this) {
                Home -> null
                Accounts,
                Settings -> Home
                Licences,
                Prototypes -> Settings
            }
}

private fun SignInState.isWaitingForUser() =
    this is SignInState.Starting ||
        this is SignInState.Browser ||
        this is SignInState.DeviceCode ||
        this is SignInState.ApiKey ||
        this is SignInState.Finishing

/** The accounts screen's callbacks, wired to [viewModel]. Device actions are set by the caller. */
private fun accountsActions(viewModel: AccountsViewModel, onClose: () -> Unit) =
    AccountsActions(
        onClose = onClose,
        onAddAccount = viewModel::addAccount,
        onPickProvider = viewModel::pickProvider,
        onBack = viewModel::back,
        onSubmitCode = viewModel::submitCode,
        onSubmitApiKey = viewModel::submitApiKey,
        onRetry = viewModel::retry,
        onFinish = viewModel::finish,
        onRename = viewModel::rename,
        onRemove = viewModel::remove,
        onReorder = viewModel::reorder,
        onOpenUrl = {},
        onCopy = {},
    )
