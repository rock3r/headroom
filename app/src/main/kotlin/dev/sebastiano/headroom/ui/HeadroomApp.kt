package dev.sebastiano.headroom.ui

import android.content.ClipData
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import dev.sebastiano.headroom.model.QuotaDisplay
import dev.sebastiano.headroom.model.SyncFrequency
import dev.sebastiano.headroom.signin.SignInState
import dev.sebastiano.headroom.signin.signInTabIntent
import dev.sebastiano.headroom.ui.accounts.AccountsActions
import dev.sebastiano.headroom.ui.accounts.AccountsScreen
import dev.sebastiano.headroom.ui.accounts.AccountsStep
import dev.sebastiano.headroom.ui.accounts.AccountsViewModel
import dev.sebastiano.headroom.ui.components.rememberResetFormatter
import dev.sebastiano.headroom.ui.home.HomeUiState
import dev.sebastiano.headroom.ui.home.HomeViewModel
import dev.sebastiano.headroom.ui.settings.LicencesScreen
import dev.sebastiano.headroom.ui.settings.SettingsAccounts
import dev.sebastiano.headroom.ui.settings.SettingsActions
import dev.sebastiano.headroom.ui.settings.SettingsScreen
import dev.sebastiano.headroom.ui.settings.SettingsUiState
import dev.sebastiano.headroom.ui.settings.SettingsViewModel
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.launch

/**
 * The whole app: the home scaffold (overview, resets, widgets, and the account detail), and the
 * accounts and settings pages on top of it. State comes from the view models; this composable only
 * wires it to the screens.
 */
@Composable
fun HeadroomApp(
    graph: AppGraph,
    modifier: Modifier = Modifier,
    openAccountRequest: OpenAccountRequest? = null,
    onConsumeOpenAccount: () -> Unit = {},
    homeViewModel: HomeViewModel = viewModel(factory = graph.homeViewModelFactory),
    accountsViewModel: AccountsViewModel = viewModel(factory = graph.accountsViewModelFactory),
    settingsViewModel: SettingsViewModel = viewModel(factory = graph.settingsViewModelFactory),
) {
    val home by homeViewModel.state.collectAsStateWithLifecycle()
    val detail by homeViewModel.detail.collectAsStateWithLifecycle()
    val accounts by accountsViewModel.state.collectAsStateWithLifecycle()
    val settings by settingsViewModel.state.collectAsStateWithLifecycle()
    val formatter = rememberResetFormatter(graph.zone)
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val widgetUnavailable = stringResource(R.string.widget_add_unavailable)
    var page by rememberSaveable { mutableStateOf(Page.Home) }
    // Accounts open from Settings, or from the demo banner's shortcut; back returns there.
    var accountsFromSettings by rememberSaveable { mutableStateOf(false) }
    // The cards' entrance plays on the first open only, not on returning to the overview.
    var entrancePlayed by rememberSaveable { mutableStateOf(false) }
    // A widget tap leaves the page on top, unless the user is in the middle of signing in.
    val signingIn = (accounts.step as? AccountsStep.SignIn)?.state?.isWaitingForUser() == true
    // A request is acted on once, even if the host is slow to clear it: a request left behind
    // must never keep the accounts screen from opening.
    var handledRequest by remember { mutableStateOf<Long?>(null) }
    val pending = openAccountRequest?.takeIf { it.serial != handledRequest }
    val decision = pending?.let { request ->
        decideOpenAccount(request.accountId, home.accounts.map { it.id }, home.accountsLoaded)
    }
    // Unknown accounts are dropped once loading is done; a pending request never blocks the UI.
    SideEffect { if (decision == OpenAccountDecision.Ignore) onConsumeOpenAccount() }
    val openNow = pending.takeIf { decision == OpenAccountDecision.Open && !signingIn }
    val shown = if (openNow == null) page else Page.Home
    // The user's tap wins over an account a widget asked for earlier.
    val dropPending = {
        pending?.let {
            handledRequest = it.serial
            onConsumeOpenAccount()
        }
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
            scrubbing = pageTransition.scrubbing,
        ) { current, reveal ->
            when (current) {
                Page.Accounts -> AccountsScreen(state = accounts, actions = accountsActions)
                Page.Settings ->
                    SettingsPage(
                        settings = settings,
                        home = home,
                        reveal = reveal,
                        onQuotaDisplayChange = settingsViewModel::setQuotaDisplay,
                        onSyncFrequencyChange = settingsViewModel::setSyncFrequency,
                        onNavigate = { next ->
                            if (next == Page.Accounts) accountsFromSettings = true
                            page = next
                        },
                    )
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
                        onOpenAccounts = {
                            dropPending()
                            accountsFromSettings = false
                            page = Page.Accounts
                        },
                        onOpenSettings = {
                            dropPending()
                            page = Page.Settings
                        },
                        settingsReveal = reveal,
                        playEntrance = !entrancePlayed,
                        onEntranceStart = { entrancePlayed = true },
                        openAccountRequest = openNow,
                        onConsumeOpenAccount = {
                            handledRequest = openNow?.serial
                            page = Page.Home
                            onConsumeOpenAccount()
                        },
                        onAddWidget = { style ->
                            if (!graph.widgetPinner.requestPin(style)) {
                                scope.launch { snackbar.showSnackbar(widgetUnavailable) }
                            }
                        },
                    )
            }
        }
        AppSnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

private const val ENTER_SCALE = 0.96f

/**
 * The pages, one over the other. Settings opens from the overview with a circular reveal from the
 * settings button (see [SettingsReveal]); every other page fades and scales in. With motion reduced
 * or off, every page crossfades.
 */
@Composable
private fun Pages(
    transition: Transition<Page>,
    scrubbing: Boolean,
    content: @Composable (Page, PageReveal) -> Unit,
) {
    val animate = animationsEnabled()
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val fast = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val revealing = animate && transition.isBetween(Page.Home, Page.Settings)
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
                remember(reveal, scope, revealing, animate, scrubbing) {
                    PageReveal(reveal, scope, revealing, animate, scrubbing)
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

/** The settings page; [onNavigate] goes back home or on to the accounts or licences page. */
@Composable
private fun SettingsPage(
    settings: SettingsUiState,
    home: HomeUiState,
    reveal: PageReveal,
    onQuotaDisplayChange: (QuotaDisplay) -> Unit,
    onSyncFrequencyChange: (SyncFrequency) -> Unit,
    onNavigate: (Page) -> Unit,
) {
    SettingsScreen(
        state = settings,
        accounts = SettingsAccounts(home.accounts.map { it.provider }, home.isDemo),
        actions =
            SettingsActions(
                onClose = { onNavigate(Page.Home) },
                onQuotaDisplayChange = onQuotaDisplayChange,
                onSyncFrequencyChange = onSyncFrequencyChange,
                onOpenLicences = { onNavigate(Page.Licences) },
                onOpenAccounts = { onNavigate(Page.Accounts) },
            ),
        reveal = reveal,
    )
}

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
    Licences;

    val back: Page?
        get() =
            when (this) {
                Home -> null
                Accounts,
                Settings -> Home
                Licences -> Settings
            }
}

private fun SignInState.isWaitingForUser() =
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
        onOpenUrl = {},
        onCopy = {},
    )
