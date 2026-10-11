package dev.sebastiano.headroom.shared

import dev.sebastiano.headroom.auth.TokenStore
import dev.sebastiano.headroom.data.AccountsRepository
import dev.sebastiano.headroom.data.account.SignInManager
import dev.sebastiano.headroom.model.AlertPreferences
import dev.sebastiano.headroom.model.NoResets
import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.ResetAttemptMemory
import dev.sebastiano.headroom.model.ResetEventLog
import dev.sebastiano.headroom.model.ResetProvider
import dev.sebastiano.headroom.model.SettingsRepository
import dev.sebastiano.headroom.signin.DeviceSession
import dev.sebastiano.headroom.signin.RealSignInController
import dev.sebastiano.headroom.signin.SignInController
import dev.sebastiano.headroom.signin.SignInSteps
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone

/** What Headroom is built from. The iOS opener passes the real ones; tests pass fakes. */
internal class HeadroomParts(
    val repository: AccountsRepository,
    val tokenStore: TokenStore,
    val signInSteps: SignInSteps,
    val settings: SettingsRepository,
    val alerts: AlertPreferences,
    val resetEvents: ResetEventLog = ResetEventLog.None,
    val resetProvider: ResetProvider = NoResets,
    val resetMemory: ResetAttemptMemory = ResetAttemptMemory(),
    val startZCode: suspend () -> DeviceSession = { error("No ZCode sign-in here") },
    val clock: () -> Instant = Clock.System::now,
    val zone: () -> TimeZone = TimeZone::currentSystemDefault,
    /** Where the stats maths runs: off the main thread. */
    val compute: CoroutineDispatcher = Dispatchers.Default,
    val onClose: () -> Unit = {},
)

/**
 * Headroom for the iOS app: the accounts, sync, sign-in, settings, resets, stats and notifications,
 * wired as on Android. Everything the app observes arrives on [scope], which the iOS opener runs on
 * the main thread, so the callbacks can update SwiftUI state directly.
 *
 * While no account is signed in, the overview, the Stats and the Resets tabs show the demo
 * accounts, as the Android app does.
 */
public class Headroom
internal constructor(parts: HeadroomParts, private val scope: CoroutineScope) {
    private val clock = parts.clock
    private val sources = Sources(parts.repository, parts.resetEvents, clock, parts.zone)
    private val signInManager = SignInManager(parts.tokenStore, parts.repository)
    private val alertPreferences = parts.alerts
    private val settingsRepository = parts.settings
    private val onClose = parts.onClose
    private val delights = Delights(sources.accounts, clock(), scope)

    /** Adding an account, or signing one in again. */
    public val signIn: HeadroomSignIn =
        HeadroomSignIn(
            RealSignInController(
                steps = parts.signInSteps,
                scope = scope,
                completeAgain = { accountId, tokens ->
                    signInManager.reauthenticate(accountId, tokens)
                },
                complete = { tokens -> signInManager.complete(tokens) },
            ),
            scope,
        )

    /** Every provider, in the order the Android app lists them, with how each signs in. */
    public val providers: List<ProviderUi> = providersFor(parts.signInSteps)

    public val settings: HeadroomSettings = HeadroomSettings(parts.settings, scope)

    public val accounts: HeadroomAccounts =
        HeadroomAccounts(sources, signInManager, parts.alerts, scope, clock)

    public val stats: HeadroomStats =
        HeadroomStats(sources, scope, clock, parts.zone, parts.compute)

    public val resets: HeadroomResets =
        HeadroomResets(
            sources = sources,
            overview = overview(),
            provider = parts.resetProvider,
            memory = parts.resetMemory,
            events = parts.resetEvents,
            scope = scope,
            startZCode = parts.startZCode,
            saveZCode = { accountId, tokens ->
                signInManager.signInToZCode(accountId, tokens)
                sources.refresh(accountId)
            },
        )

    public val notifications: HeadroomNotifications =
        HeadroomNotifications(sources, parts.settings, parts.alerts, clock, parts.zone)

    /** Calls [onChange] with the overview now and whenever it changes, until [Watch.cancel]. */
    public fun watchOverview(onChange: (OverviewUi) -> Unit): Watch =
        scope.watch(overview(), onChange)

    /**
     * Calls [onEvent] when a refresh brings new numbers or a weekly limit resets while the app is
     * open, until [Watch.cancel]. Each moment arrives once.
     */
    public fun watchDelights(onEvent: (DelightUi) -> Unit): Watch =
        scope.watch(delights.events, onEvent)

    /** Syncs [accountId], or every account when it is null. The overview shows the result. */
    public fun refresh(accountId: String?) {
        scope.launch { sources.refresh(accountId) }
    }

    /** Syncs every account and returns when it is done, for a background refresh. */
    public suspend fun refreshAll() {
        sources.refresh(accountId = null)
    }

    /** Stops everything this instance started. */
    public fun close() {
        signIn.cancel()
        scope.cancel()
        onClose()
    }

    /**
     * The real accounts, or the demo ones while there are none, with the settings and the alert
     * switches. It is worked out again every [TICK], so pace keeps moving between syncs.
     */
    private fun overview(): Flow<OverviewUi> =
        combine(
                delights.tracked,
                sources.isDemo,
                settingsRepository.settings,
                AlertStates.of(sources.accounts, alertPreferences),
                ticks(),
            ) { tracked, demo, settings, alerts, now ->
                UiMapping.overview(tracked.accounts, demo, now, settings, alerts, tracked.justReset)
            }
            .distinctUntilChanged()

    private fun ticks(): Flow<Instant> = flow {
        while (true) {
            emit(clock())
            delay(TICK)
        }
    }

    internal companion object {
        private val TICK: Duration = 1.minutes
    }
}

/**
 * Adding an account, or signing one in again: the same steps as on Android. The browser step is the
 * app's to show, in an `ASWebAuthenticationSession`, never in Safari, so the loopback listener
 * keeps running.
 */
public class HeadroomSignIn
internal constructor(private val controller: SignInController, private val scope: CoroutineScope) {
    /** Calls [onChange] with the step now and at every change, until [Watch.cancel]. */
    public fun watch(onChange: (SignInUi) -> Unit): Watch =
        scope.watch(controller.state.map(UiMapping::signIn), onChange)

    /**
     * Starts signing in to the provider with [providerId]. With an [accountId], it signs that
     * account in again, keeping its history and name.
     */
    public fun start(providerId: String, accountId: String?) {
        val provider = Provider.fromId(providerId) ?: return
        controller.start(provider, accountId)
    }

    /** The code the provider's page showed, for when the browser could not return by itself. */
    public fun submitCode(code: String) {
        controller.submitCode(code)
    }

    public fun submitApiKey(key: String) {
        controller.submitApiKey(key)
    }

    public fun retry() {
        controller.retry()
    }

    public fun cancel() {
        controller.cancel()
    }
}

/** A running observation. [cancel] stops its callbacks. */
public class Watch internal constructor(private val job: Job) {
    public fun cancel() {
        job.cancel()
    }
}

internal fun <T> CoroutineScope.watch(flow: Flow<T>, onChange: (T) -> Unit): Watch =
    Watch(launch { flow.collect { onChange(it) } })
