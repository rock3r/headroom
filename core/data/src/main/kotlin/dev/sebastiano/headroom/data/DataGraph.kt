package dev.sebastiano.headroom.data

import android.content.Context
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkerFactory
import dev.sebastiano.headroom.auth.AuthMethods
import dev.sebastiano.headroom.auth.TokenStore
import dev.sebastiano.headroom.data.account.AccountQuotaFetcher
import dev.sebastiano.headroom.data.account.AndroidSignInNotifier
import dev.sebastiano.headroom.data.account.EncryptedTokenStore
import dev.sebastiano.headroom.data.account.SharedPreferencesSignInAlertLedger
import dev.sebastiano.headroom.data.account.SignInAlertingRepository
import dev.sebastiano.headroom.data.account.SignInAlerts
import dev.sebastiano.headroom.data.account.SignInManager
import dev.sebastiano.headroom.data.account.SignInNotifier
import dev.sebastiano.headroom.data.account.SyncedResetProvider
import dev.sebastiano.headroom.data.account.TinkCredentialCipher
import dev.sebastiano.headroom.data.reset.AlarmResetScheduler
import dev.sebastiano.headroom.data.reset.AndroidResetLog
import dev.sebastiano.headroom.data.reset.AndroidResetNotifier
import dev.sebastiano.headroom.data.reset.AndroidResetReminderNotifier
import dev.sebastiano.headroom.data.reset.ResetAlarm
import dev.sebastiano.headroom.data.reset.ResetAlarmPlanner
import dev.sebastiano.headroom.data.reset.ResetCheckWorker
import dev.sebastiano.headroom.data.reset.ResetChecker
import dev.sebastiano.headroom.data.reset.ResetIsland
import dev.sebastiano.headroom.data.reset.ResetReminderAlarm
import dev.sebastiano.headroom.data.reset.ResetReminders
import dev.sebastiano.headroom.data.reset.SharedPreferencesAttemptTargetStore
import dev.sebastiano.headroom.data.reset.SharedPreferencesResetAttemptStore
import dev.sebastiano.headroom.data.reset.SharedPreferencesResetLedger
import dev.sebastiano.headroom.data.reset.SharedPreferencesResetReminderLedger
import dev.sebastiano.headroom.data.sync.HeadroomWorkerFactory
import dev.sebastiano.headroom.data.sync.SyncWorker
import dev.sebastiano.headroom.model.AlertPreferences
import dev.sebastiano.headroom.model.ResetAttemptStore
import dev.sebastiano.headroom.model.ResetEventLog
import dev.sebastiano.headroom.model.ResetProvider
import dev.sebastiano.headroom.model.SettingsRepository
import dev.sebastiano.headroom.quota.QuotaFetchers
import dev.sebastiano.headroom.quota.ResetClients
import dev.sebastiano.headroom.quota.ResetLog
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Implemented by the Application, so receivers and workers can reach the [DataGraph]. */
public interface DataGraphOwner {
    public val dataGraph: DataGraph
}

/**
 * Builds and owns the data layer: Room, DataStore, the repository, the settings, reset alarms and
 * background work. The app creates one per process and passes in how to fetch an account's quota.
 */
public class DataGraph(
    context: Context,
    private val scope: CoroutineScope,
    private val clock: () -> Instant = Clock.System::now,
    /** Where sign-in tokens live. Defaults to the encrypted on-device store. */
    public val tokenStore: TokenStore = EncryptedTokenStore(context, TinkCredentialCipher(context)),
    /** How each provider signs in and refreshes its tokens. */
    public val authMethods: AuthMethods = AuthMethods(),
    quotaFetchers: QuotaFetchers =
        QuotaFetchers.create(jetBrainsLog = { Log.i(JETBRAINS_LOG_TAG, it) }),
    /** Shows a reset on the screen, when the app allows it. The default never shows. */
    resetIsland: ResetIsland = ResetIsland.None,
    /** Where the reset clients write what they ask and get: logcat, under `HeadroomResets`. */
    resetLog: ResetLog = AndroidResetLog,
    /** Reads and uses the usage-limit resets of the providers that have them. */
    resetClients: ResetClients =
        ResetClients.create(
            log = resetLog,
            zAiReachedUse = SharedPreferencesAttemptTargetStore(context),
            grokPinnedTokens =
                SharedPreferencesAttemptTargetStore(
                    context,
                    key = SharedPreferencesAttemptTargetStore.GROK_PINNED_TOKENS,
                ),
        ),
) {
    private val appContext = context.applicationContext

    private val storage = openHeadroomStorage(appContext, scope)

    private val accountFetcher =
        AccountQuotaFetcher(authMethods.credentialProvider(tokenStore), quotaFetchers, resetClients)

    private val roomRepository = storage.accounts(accountFetcher::fetch, clock, scope)

    /** Shows and removes the warning that an account's sign-in expired. */
    public val signInNotifier: SignInNotifier = AndroidSignInNotifier(appContext)

    public val repository: AccountsRepository =
        SignInAlertingRepository(
            roomRepository,
            SignInAlerts(signInNotifier, SharedPreferencesSignInAlertLedger(appContext)),
        )

    /** The user's app settings: used or left, and how often to sync in the background. */
    public val settings: SettingsRepository = storage.settings

    /**
     * The resets of the signed-in accounts: read by each sync and stored with the snapshot, and
     * used through the providers' reset clients, when the user's [settings] allow it.
     */
    public val resetProvider: ResetProvider =
        SyncedResetProvider(
            // What storage holds now: right after a refresh, accounts.value may still be the old
            // one.
            accounts = { repository.current() },
            settings = { settings.settings.first() },
            fetcher = accountFetcher,
            clients = resetClients,
            log = resetLog,
        )

    /**
     * The resets used and expired, kept in Room for a year: each sync records the ones it finds
     * gone, and a redeem that works in Headroom is recorded here.
     */
    public val resetEvents: ResetEventLog = storage.resetEvents(clock)

    /** Keeps the keys of unsettled redeem attempts across restarts. */
    public val resetAttempts: ResetAttemptStore = SharedPreferencesResetAttemptStore(appContext)

    public val alertPreferences: AlertPreferences = storage.alertPreferences

    /** Turns finished sign-ins into accounts, and signs accounts out. */
    public val signInManager: SignInManager = SignInManager(tokenStore, repository)

    private val scheduler =
        AlarmResetScheduler(appContext) { overdue ->
            // Keep a check that is already running; only start one when none is queued.
            ResetCheckWorker.enqueue(
                appContext,
                overdue,
                attempt = 1,
                delay = null,
                policy = ExistingWorkPolicy.KEEP,
            )
        }

    private val resetReminders =
        ResetReminders(
            repository = repository,
            settings = { settings.settings.first() },
            ledger = SharedPreferencesResetReminderLedger(appContext),
            notifier = AndroidResetReminderNotifier(appContext),
            schedule = ResetReminderAlarm(appContext)::schedule,
            clock = clock,
        )

    public val workerFactory: WorkerFactory =
        HeadroomWorkerFactory(
            resetChecker =
                ResetChecker(
                    repository = repository,
                    notifier = AndroidResetNotifier(appContext, island = resetIsland),
                    ledger = SharedPreferencesResetLedger(appContext),
                    isEnabled = { accountId, window ->
                        alertPreferences.isEnabled(accountId, window).first()
                    },
                ),
            repository = repository,
            resetReminders = resetReminders,
        )

    /**
     * Keeps the periodic sync on the period from the settings, and reset alarms in step with
     * accounts and alert switches. Reset alarms do not depend on the sync period. The reset
     * reminder's alarm follows the resets the accounts hold and the settings.
     */
    @OptIn(FlowPreview::class)
    public fun start() {
        scope.launch {
            settings.settings
                .map { it.syncFrequency }
                .distinctUntilChanged()
                .collect { SyncWorker.schedule(appContext, it) }
        }
        scope.launch {
            combine(repository.accounts, storage.alertChanges) { _, _ -> Unit }
                .debounce(RESCHEDULE_DEBOUNCE_MS)
                .collect { rescheduleResetAlarms() }
        }
        scope.launch {
            combine(repository.accounts, settings.settings) { _, _ -> Unit }
                .debounce(RESCHEDULE_DEBOUNCE_MS)
                .collect { resetReminders.reschedule() }
        }
    }

    public suspend fun rescheduleResetAlarms() {
        val accounts = repository.current()
        val enabled =
            accounts
                .flatMap { state ->
                    state.snapshot?.windows.orEmpty().map { state.account.id to it }
                }
                .associate { (accountId, window) ->
                    (accountId to window.id) to
                        alertPreferences.isEnabled(accountId, window).first()
                }
        val alarms =
            ResetAlarmPlanner.plan(accounts, clock()) { accountId, window ->
                enabled[accountId to window.id] == true
            }
        scheduler.replaceAll(clock(), alarms)
    }

    /** Plans the reminder that a reset expires soon again, for example after a reboot. */
    internal suspend fun rescheduleResetReminders() {
        resetReminders.reschedule()
    }

    internal fun markAlarmFired(alarm: ResetAlarm) {
        scheduler.markFired(alarm)
    }

    /** Runs work from broadcast receivers on the app's own scope and dispatcher. */
    internal fun launchInBackground(block: suspend () -> Unit) {
        scope.launch { block() }
    }

    private companion object {
        const val RESCHEDULE_DEBOUNCE_MS = 500L

        /** `adb logcat -s HeadroomJetBrains` shows the JetBrains AI quota calls. */
        const val JETBRAINS_LOG_TAG = "HeadroomJetBrains"
    }
}
