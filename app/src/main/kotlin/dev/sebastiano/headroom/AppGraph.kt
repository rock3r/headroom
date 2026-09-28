package dev.sebastiano.headroom

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.sebastiano.headroom.appdata.DemoAwareResetHistory
import dev.sebastiano.headroom.appdata.DemoModeQuotaRepository
import dev.sebastiano.headroom.appdata.DemoResetHistory
import dev.sebastiano.headroom.appdata.HistoryResetHistory
import dev.sebastiano.headroom.appdata.InMemoryAlertPreferences
import dev.sebastiano.headroom.appdata.ResetHistory
import dev.sebastiano.headroom.data.DataGraph
import dev.sebastiano.headroom.model.AlertPreferences
import dev.sebastiano.headroom.model.FakeQuotaRepository
import dev.sebastiano.headroom.model.InMemorySettingsRepository
import dev.sebastiano.headroom.model.QuotaRepository
import dev.sebastiano.headroom.model.SettingsRepository
import dev.sebastiano.headroom.signin.AuthSignInSteps
import dev.sebastiano.headroom.signin.FakeSignInController
import dev.sebastiano.headroom.signin.RealSignInController
import dev.sebastiano.headroom.signin.SignInController
import dev.sebastiano.headroom.ui.accounts.AccountsViewModel
import dev.sebastiano.headroom.ui.home.HomeViewModel
import dev.sebastiano.headroom.ui.settings.SettingsViewModel
import dev.sebastiano.headroom.widget.HeadroomWidgetProvider
import dev.sebastiano.headroom.widgets.AppWidgetManagerPinner
import dev.sebastiano.headroom.widgets.WidgetPinner
import dev.sebastiano.headroom.widgets.WidgetStyle
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * The app's object graph, built once by [HeadroomApplication]. There is no DI framework: every
 * dependency is passed by constructor from here.
 *
 * The seams the other layers fill:
 * - [quotaRepository]: shows the real accounts, or demo data while there are none. [create] puts an
 *   empty repository where the data layer's Room-backed repository goes.
 * - [alertPreferences]: in memory for now; the data layer's DataStore implementation goes here.
 * - [signInController]: the auth layer's controller replaces [FakeSignInController].
 * - [widgetPinner]: the widget module supplies the `AppWidgetProvider` for each style.
 * - [resetHistory]: the data layer answers it from the Room history.
 * - [settings]: the data layer's DataStore settings, or settings in memory without it.
 */
class AppGraph(
    val quotaRepository: QuotaRepository,
    val isDemo: StateFlow<Boolean>,
    /** True once the stored accounts have been read, so an empty list really means none. */
    val accountsLoaded: StateFlow<Boolean>,
    val alertPreferences: AlertPreferences,
    val clock: () -> Instant,
    val zone: ZoneId,
    val signInController: SignInController,
    val widgetPinner: WidgetPinner,
    val resetHistory: ResetHistory,
    /** Stores the name the user gives an account. Does nothing without the data layer. */
    val renameAccount: suspend (accountId: String, name: String?) -> Unit = { _, _ -> },
    /** Signs an account out and forgets it. Does nothing without the data layer. */
    val removeAccount: suspend (accountId: String) -> Unit = {},
    /** Stores the order the user put the accounts in. Does nothing without the data layer. */
    val reorderAccounts: suspend (orderedIds: List<String>) -> Unit = {},
    /** How often countdowns re-read the clock. Null turns it off, for tests with a fixed clock. */
    val tickInterval: Duration? = Duration.ofMinutes(1),
    /** Used or left, and how often to sync in the background. */
    val settings: SettingsRepository = InMemorySettingsRepository(),
    /** The version name the settings screen shows. */
    val appVersion: String = "",
) {
    val homeViewModelFactory: ViewModelProvider.Factory = viewModelFactory {
        initializer {
            HomeViewModel(
                repository = quotaRepository,
                alertPreferences = alertPreferences,
                clock = clock,
                isDemo = isDemo,
                accountsLoaded = accountsLoaded,
                resetHistory = resetHistory,
                tickInterval = tickInterval,
                savedStateHandle = createSavedStateHandle(),
                quotaDisplay = settings.settings.map { it.quotaDisplay }.distinctUntilChanged(),
            )
        }
    }

    val settingsViewModelFactory: ViewModelProvider.Factory = viewModelFactory {
        initializer { SettingsViewModel(settings = settings, appVersion = appVersion) }
    }

    val accountsViewModelFactory: ViewModelProvider.Factory = viewModelFactory {
        initializer {
            AccountsViewModel(
                repository = quotaRepository,
                signInController = signInController,
                isDemo = isDemo,
                renameAccount = renameAccount,
                removeAccount = removeAccount,
                reorderAccounts = reorderAccounts,
            )
        }
    }

    companion object {
        /**
         * Builds the graph. With a [data] graph the app shows the signed-in accounts from the data
         * layer, and demo data only while there are none. Without one (instrumented tests,
         * previews) it runs on fakes.
         */
        fun create(
            context: Context,
            clock: () -> Instant = Instant::now,
            zone: ZoneId = ZoneId.systemDefault(),
            scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            demoLatency: Duration = Duration.ofMillis(DEMO_LATENCY_MILLIS),
            tickInterval: Duration? = Duration.ofMinutes(1),
            data: DataGraph? = null,
        ): AppGraph {
            val realAccounts: QuotaRepository =
                data?.repository ?: FakeQuotaRepository(clock, initial = emptyList())
            val repository =
                DemoModeQuotaRepository(
                    real = realAccounts,
                    demo = FakeQuotaRepository(clock),
                    scope = scope,
                    simulatedLatency = demoLatency,
                )
            val signInController =
                if (data == null) {
                    FakeSignInController()
                } else {
                    val steps =
                        AuthSignInSteps(data.authMethods) {
                            // The app's task is under the browser tab, so this start is allowed;
                            // MainActivity is singleTask, so it clears the tab above it.
                            context.startActivity(
                                Intent(context, MainActivity::class.java)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    RealSignInController(steps, scope) { tokens ->
                        data.signInManager.complete(tokens)
                    }
                }
            return AppGraph(
                quotaRepository = repository,
                isDemo = repository.isDemo,
                accountsLoaded = repository.isLoaded,
                alertPreferences = data?.alertPreferences ?: InMemoryAlertPreferences(),
                clock = clock,
                zone = zone,
                signInController = signInController,
                widgetPinner =
                    AppWidgetManagerPinner(context.applicationContext) { style ->
                        ComponentName(
                            context,
                            HeadroomWidgetProvider.classFor(style.toWidgetStyle()),
                        )
                    },
                resetHistory =
                    DemoAwareResetHistory(
                        isDemo = repository.isDemo,
                        real = HistoryResetHistory(realAccounts),
                        demo = DemoResetHistory,
                    ),
                tickInterval = tickInterval,
                renameAccount = { id, name -> data?.repository?.renameAccount(id, name) },
                removeAccount = { id -> data?.signInManager?.signOut(id) },
                reorderAccounts = { ids -> data?.repository?.reorderAccounts(ids) },
                settings = data?.settings ?: InMemorySettingsRepository(),
                appVersion = versionName(context),
            )
        }

        private fun WidgetStyle.toWidgetStyle(): dev.sebastiano.headroom.widget.WidgetStyle =
            when (this) {
                WidgetStyle.Rings -> dev.sebastiano.headroom.widget.WidgetStyle.Rings
                WidgetStyle.Bars -> dev.sebastiano.headroom.widget.WidgetStyle.Bars
                WidgetStyle.Shape -> dev.sebastiano.headroom.widget.WidgetStyle.Shape
                WidgetStyle.Countdown -> dev.sebastiano.headroom.widget.WidgetStyle.Countdown
            }

        private fun versionName(context: Context): String =
            context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()

        private const val DEMO_LATENCY_MILLIS = 900L
    }
}
