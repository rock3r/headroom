package dev.sebastiano.headroom.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.sebastiano.headroom.AppGraph
import dev.sebastiano.headroom.R
import dev.sebastiano.headroom.signin.ServiceSignIn
import dev.sebastiano.headroom.signin.ServiceSignInState
import dev.sebastiano.headroom.signin.signInTabIntent
import dev.sebastiano.headroom.ui.components.CursorConnect
import dev.sebastiano.headroom.ui.home.HomeViewModel
import dev.sebastiano.headroom.ui.settings.SettingsActions
import dev.sebastiano.headroom.ui.settings.SettingsViewModel
import dev.sebastiano.headroom.widgets.WidgetPinner
import dev.sebastiano.headroom.widgets.WidgetStyle
import kotlinx.coroutines.launch

/**
 * The Cursor sign-in of a Grok account, or null in the demo, where the accounts are not real and so
 * have nothing to connect.
 */
@Composable
internal fun rememberCursorConnect(
    graph: AppGraph,
    viewModel: HomeViewModel,
    isDemo: Boolean,
): CursorConnect? =
    if (isDemo) null
    else
        remember(graph.cursorSignIn, viewModel) {
            CursorConnect(
                signIn = graph.cursorSignIn,
                onDismiss = viewModel::dismissCursorPrompt,
                onRetry = viewModel::refresh,
            )
        }

/**
 * Opens a second sign-in's page in a browser tab, once per ask: when the sign-in starts waiting for
 * the user, and again when they ask for the page a second time. It belongs here, at the top of the
 * app, because the card's prompt and the account's say the same thing and only one tab may open.
 *
 * The tab starts from this activity, so it lands in this app's task; returning to the app then puts
 * the app back in front of the tab, which goes away with it.
 */
@Composable
internal fun OpenSignInPage(signIn: ServiceSignIn?) {
    if (signIn == null) return
    val context = LocalContext.current
    val state by signIn.state.collectAsStateWithLifecycle()
    val waiting = state as? ServiceSignInState.Waiting
    // Asking again bumps the count, so a tab the user closed opens again.
    DisposableEffect(waiting?.accountId, waiting?.opened) {
        if (waiting != null) signInTabIntent(context).launchUrl(context, waiting.url.toUri())
        onDispose {}
    }
}

/**
 * Settings' actions, made once: the app recomposes whenever the pages settle, for example as
 * Settings' reveal ends. New callbacks then would compose every row of Settings again.
 */
@Composable
internal fun rememberSettingsActions(
    viewModel: SettingsViewModel,
    addWidget: (WidgetStyle) -> Unit,
    graph: AppGraph,
    openPage: (Page) -> Unit,
): SettingsActions =
    remember(viewModel, addWidget, graph.resetIsland) {
        settingsActions(viewModel, addWidget, graph.resetIsland, openPage)
    }

/** Asks [pinner] for a widget, and says so in [snackbar] when the launcher cannot add it. */
@Composable
internal fun rememberWidgetAdder(
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
