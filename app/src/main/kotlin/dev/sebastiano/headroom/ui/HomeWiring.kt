package dev.sebastiano.headroom.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.stringResource
import dev.sebastiano.headroom.AppGraph
import dev.sebastiano.headroom.R
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
            CursorConnect(graph.cursorSignIn, viewModel::dismissCursorPrompt)
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
