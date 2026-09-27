package dev.sebastiano.headroom

import android.content.Context
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.sebastiano.headroom.appdata.DemoModeQuotaRepository
import dev.sebastiano.headroom.appdata.DemoResetHistory
import dev.sebastiano.headroom.appdata.InMemoryAlertPreferences
import dev.sebastiano.headroom.appdata.ResetHistory
import dev.sebastiano.headroom.model.AlertPreferences
import dev.sebastiano.headroom.model.FakeQuotaRepository
import dev.sebastiano.headroom.model.QuotaRepository
import dev.sebastiano.headroom.signin.FakeSignInController
import dev.sebastiano.headroom.signin.SignInController
import dev.sebastiano.headroom.ui.accounts.AccountsViewModel
import dev.sebastiano.headroom.ui.home.HomeViewModel
import dev.sebastiano.headroom.widgets.AppWidgetManagerPinner
import dev.sebastiano.headroom.widgets.WidgetPinner
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow

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
 */
class AppGraph(
    val quotaRepository: QuotaRepository,
    val isDemo: StateFlow<Boolean>,
    val alertPreferences: AlertPreferences,
    val clock: () -> Instant,
    val zone: ZoneId,
    val signInController: SignInController,
    val widgetPinner: WidgetPinner,
    val resetHistory: ResetHistory,
    /** How often countdowns re-read the clock. Null turns it off, for tests with a fixed clock. */
    val tickInterval: Duration? = Duration.ofMinutes(1),
) {
    val homeViewModelFactory: ViewModelProvider.Factory = viewModelFactory {
        initializer {
            HomeViewModel(
                repository = quotaRepository,
                alertPreferences = alertPreferences,
                clock = clock,
                isDemo = isDemo,
                resetHistory = resetHistory,
                tickInterval = tickInterval,
                savedStateHandle = createSavedStateHandle(),
            )
        }
    }

    val accountsViewModelFactory: ViewModelProvider.Factory = viewModelFactory {
        initializer {
            AccountsViewModel(
                repository = quotaRepository,
                signInController = signInController,
                isDemo = isDemo,
            )
        }
    }

    companion object {
        /**
         * The graph the app runs with today: no real accounts yet, so it shows demo data. The data
         * layer replaces `realAccounts` and the in-memory preferences when it is wired in.
         */
        fun create(
            context: Context,
            clock: () -> Instant = Instant::now,
            zone: ZoneId = ZoneId.systemDefault(),
            scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            demoLatency: Duration = Duration.ofMillis(DEMO_LATENCY_MILLIS),
            tickInterval: Duration? = Duration.ofMinutes(1),
        ): AppGraph {
            val realAccounts: QuotaRepository = FakeQuotaRepository(clock, initial = emptyList())
            val repository =
                DemoModeQuotaRepository(
                    real = realAccounts,
                    demo = FakeQuotaRepository(clock),
                    scope = scope,
                    simulatedLatency = demoLatency,
                )
            return AppGraph(
                quotaRepository = repository,
                isDemo = repository.isDemo,
                alertPreferences = InMemoryAlertPreferences(),
                clock = clock,
                zone = zone,
                signInController = FakeSignInController(),
                widgetPinner = AppWidgetManagerPinner(context.applicationContext) { null },
                resetHistory = DemoResetHistory,
                tickInterval = tickInterval,
            )
        }

        private const val DEMO_LATENCY_MILLIS = 900L
    }
}
