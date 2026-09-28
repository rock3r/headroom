package dev.sebastiano.headroom.ui

import android.content.ClipData
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.SeekableTransitionState
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
import dev.sebastiano.headroom.signin.SignInState
import dev.sebastiano.headroom.signin.signInTabIntent
import dev.sebastiano.headroom.ui.accounts.AccountsActions
import dev.sebastiano.headroom.ui.accounts.AccountsScreen
import dev.sebastiano.headroom.ui.accounts.AccountsStep
import dev.sebastiano.headroom.ui.accounts.AccountsViewModel
import dev.sebastiano.headroom.ui.components.rememberResetFormatter
import dev.sebastiano.headroom.ui.home.HomeViewModel
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.launch

/**
 * The whole app: the home scaffold (overview, resets, widgets, and the account detail), and the
 * accounts screen on top of it. State comes from the view models; this composable only wires it to
 * the screens.
 */
@Composable
fun HeadroomApp(
    graph: AppGraph,
    modifier: Modifier = Modifier,
    openAccountRequest: OpenAccountRequest? = null,
    onConsumeOpenAccount: () -> Unit = {},
    homeViewModel: HomeViewModel = viewModel(factory = graph.homeViewModelFactory),
    accountsViewModel: AccountsViewModel = viewModel(factory = graph.accountsViewModelFactory),
) {
    val home by homeViewModel.state.collectAsStateWithLifecycle()
    val detail by homeViewModel.detail.collectAsStateWithLifecycle()
    val accounts by accountsViewModel.state.collectAsStateWithLifecycle()
    val formatter = rememberResetFormatter(graph.zone)
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val widgetUnavailable = stringResource(R.string.widget_add_unavailable)
    var accountsOpen by rememberSaveable { mutableStateOf(false) }
    // The cards' entrance plays on the first open only, not on returning to the overview.
    var entrancePlayed by rememberSaveable { mutableStateOf(false) }
    // A widget tap leaves the accounts screen, unless the user is in the middle of signing in.
    val signingIn = (accounts.step as? AccountsStep.SignIn)?.state?.isWaitingForUser() == true
    val decision = openAccountRequest?.let { request ->
        decideOpenAccount(request.accountId, home.accounts.map { it.id }, home.accountsLoaded)
    }
    // Unknown accounts are dropped once loading is done; a pending request never blocks the UI.
    SideEffect { if (decision == OpenAccountDecision.Ignore) onConsumeOpenAccount() }
    val openNow = openAccountRequest.takeIf { decision == OpenAccountDecision.Open && !signingIn }
    val showAccounts = accountsOpen && openNow == null

    val accountsActions =
        AccountsActions(
            onClose = { accountsOpen = false },
            onAddAccount = accountsViewModel::addAccount,
            onPickProvider = accountsViewModel::pickProvider,
            onBack = accountsViewModel::back,
            onSubmitCode = accountsViewModel::submitCode,
            onSubmitApiKey = accountsViewModel::submitApiKey,
            onRetry = accountsViewModel::retry,
            onFinish = accountsViewModel::finish,
            onRename = accountsViewModel::rename,
            onRemove = accountsViewModel::remove,
            onOpenUrl = { url -> signInTabIntent(context).launchUrl(context, url.toUri()) },
            onCopy = { text ->
                scope.launch {
                    clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(null, text)))
                }
            },
        )

    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val fast = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    Box(modifier = modifier.fillMaxSize()) {
        // Seekable, so the predictive back gesture scrubs the accounts screen away.
        val accountsTransition = remember { SeekableTransitionState(showAccounts) }
        LaunchedEffect(showAccounts) { accountsTransition.animateTo(showAccounts) }
        PredictiveBackHandler(enabled = showAccounts && accounts.step == AccountsStep.List) {
            gesture ->
            try {
                gesture.collect { event ->
                    accountsTransition.seekTo(event.progress, targetState = false)
                }
                accountsOpen = false
            } catch (cancelled: CancellationException) {
                // The gesture's coroutine is cancelled; settle back from a live scope.
                scope.launch { accountsTransition.animateTo(true) }
                throw cancelled
            }
        }
        rememberTransition(accountsTransition, label = "accounts").AnimatedContent(
            transitionSpec = {
                (fadeIn(effects) + scaleIn(effects, initialScale = ENTER_SCALE)) togetherWith
                    fadeOut(fast)
            }
        ) { open ->
            if (open) {
                AccountsScreen(state = accounts, actions = accountsActions)
            } else {
                HomeScaffold(
                    home = home,
                    detail = detail,
                    formatter = formatter,
                    onRefresh = homeViewModel::refresh,
                    onSelectAccount = homeViewModel::select,
                    onAlertChange = homeViewModel::setAlert,
                    onChartWindowChange = homeViewModel::selectChartWindow,
                    onOpenAccounts = { accountsOpen = true },
                    playEntrance = !entrancePlayed,
                    onEntranceStart = { entrancePlayed = true },
                    openAccountRequest = openNow,
                    onConsumeOpenAccount = {
                        accountsOpen = false
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
        SnackbarHost(
            hostState = snackbar,
            modifier =
                Modifier.align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 88.dp),
        )
    }
}

private const val ENTER_SCALE = 0.96f

private fun SignInState.isWaitingForUser() =
    this is SignInState.Browser || this is SignInState.DeviceCode || this is SignInState.ApiKey
