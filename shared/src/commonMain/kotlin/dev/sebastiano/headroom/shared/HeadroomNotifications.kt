package dev.sebastiano.headroom.shared

import dev.sebastiano.headroom.data.reset.ResetAlarmPlanner
import dev.sebastiano.headroom.model.AccountState
import dev.sebastiano.headroom.model.AlertPreferences
import dev.sebastiano.headroom.model.AppSettings
import dev.sebastiano.headroom.model.ResetReminderPolicy
import dev.sebastiano.headroom.model.SettingsRepository
import dev.sebastiano.headroom.model.SignInAlertPolicy
import dev.sebastiano.headroom.model.WindowKind
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * The local notifications the iOS app should have scheduled now. iOS cannot wake an app at an exact
 * moment to check that a limit really reset, as Android's alarms do, so the alerts fire at the
 * reset time the provider announced.
 */
public data class NotificationPlanUi(
    /** One per window that can alert, has its alert on, and resets later. */
    val resetAlerts: List<ResetAlertUi>,
    /** At most one per day, for the usable resets that expire within a day of it. */
    val reminders: List<ReminderUi>,
    /** Accounts whose sign-in just expired: warn about them now. */
    val signInAlerts: List<SignInAlertUi>,
    /** Account ids whose sign-in warning must go: they synced fine again, or were removed. */
    val signInCancels: List<String>,
    /** The account ids with a sign-in warning out: pass them to the next plan. */
    val signInNotified: List<String>,
    /** The expiring resets a reminder covers now, or already did: pass them to the next plan. */
    val reminded: List<String>,
)

public data class ResetAlertUi(
    /** Stable per account and window, so a new plan replaces the old notification. */
    val id: String,
    val accountId: String,
    val windowId: String,
    /** The account, with its label when the user has several of the same provider. */
    val accountName: String,
    val monthly: Boolean,
    val fireAtEpochSeconds: Long,
    /** When the window resets after this one, for "Next reset …". */
    val nextResetAtEpochSeconds: Long?,
)

public data class ReminderUi(
    /** Stable per day. */
    val id: String,
    val fireAtEpochSeconds: Long,
    val resets: List<ReminderItemUi>,
)

public data class ReminderItemUi(val accountName: String, val expiresAtEpochSeconds: Long)

public data class SignInAlertUi(
    val accountId: String,
    val providerName: String,
    /** The account's label when the user has several of the same provider. */
    val label: String?,
    val syncedAtEpochSeconds: Long?,
)

/** Plans the reset alerts, the reset expiry reminders and the sign-in warnings. */
public class HeadroomNotifications
internal constructor(
    private val sources: Sources,
    private val settings: SettingsRepository,
    private val alerts: AlertPreferences,
    private val clock: () -> Instant,
    private val zone: () -> TimeZone,
) {
    /**
     * The notifications to have scheduled now, for the real accounts. [signInNotified] and
     * [reminded] are what the last plan returned, so a warning or a reminder goes out once.
     */
    public suspend fun plan(
        signInNotified: List<String>,
        reminded: List<String>,
    ): NotificationPlanUi {
        val accounts = sources.real.current()
        val now = clock()
        val alertStates = AlertStates.of(flowOf(accounts), alerts).first()
        return NotificationPlanner.plan(
            accounts = accounts,
            settings = settings.settings.first(),
            alerts = alertStates,
            now = now,
            zone = zone(),
            signInNotified = signInNotified.toSet(),
            reminded = reminded.toSet(),
        )
    }
}

internal object NotificationPlanner {
    /** A reminder that is already due goes out a few seconds from now. */
    private val DUE_DELAY = 5.seconds

    fun plan(
        accounts: List<AccountState>,
        settings: AppSettings,
        alerts: AlertStates,
        now: Instant,
        zone: TimeZone,
        signInNotified: Set<String>,
        reminded: Set<String>,
    ): NotificationPlanUi {
        val signIn = SignInAlertPolicy.decide(accounts, signInNotified)
        val reminders = reminders(accounts, settings, now, zone, reminded)
        return NotificationPlanUi(
            resetAlerts = resetAlerts(accounts, alerts, now),
            reminders = reminders.map { it.ui },
            signInAlerts =
                signIn.post.map { state ->
                    SignInAlertUi(
                        accountId = state.account.id,
                        providerName = state.account.provider.displayName,
                        label = state.account.label.takeIf { accounts.hasSeveral(state) },
                        syncedAtEpochSeconds = state.snapshot?.fetchedAt?.epochSeconds,
                    )
                },
            signInCancels = signIn.cancel.toList(),
            signInNotified = signIn.notified.toList(),
            reminded =
                (reminded + reminders.flatMap { it.keys })
                    .filterTo(mutableSetOf()) { key ->
                        // Forget reminders of resets that expired: they can never be due again.
                        key.substringAfterLast('/').toLongOrNull()?.let {
                            it > now.toEpochMilliseconds()
                        } ?: false
                    }
                    .toList(),
        )
    }

    private fun resetAlerts(
        accounts: List<AccountState>,
        alerts: AlertStates,
        now: Instant,
    ): List<ResetAlertUi> =
        ResetAlarmPlanner.plan(accounts, now) { accountId, window ->
                alerts.isOn(accountId, window)
            }
            .mapNotNull { alarm ->
                val state =
                    accounts.firstOrNull { it.account.id == alarm.accountId }
                        ?: return@mapNotNull null
                val window =
                    state.snapshot?.windows?.firstOrNull { it.id == alarm.windowId }
                        ?: return@mapNotNull null
                ResetAlertUi(
                    id = "reset:${alarm.accountId}/${alarm.windowId}",
                    accountId = alarm.accountId,
                    windowId = alarm.windowId,
                    accountName = state.displayName(accounts),
                    monthly = window.kind == WindowKind.Monthly,
                    fireAtEpochSeconds = alarm.triggerAt.epochSeconds,
                    nextResetAtEpochSeconds =
                        window.length?.let { (alarm.expectedResetAt + it).epochSeconds },
                )
            }

    /**
     * The usable resets that expire, one reminder a day before each, grouped by the local day the
     * reminder falls on: at most one notification a day, as on Android. A reminder already due goes
     * out at once, unless an earlier plan covered its reset.
     */
    private fun reminders(
        accounts: List<AccountState>,
        settings: AppSettings,
        now: Instant,
        zone: TimeZone,
        reminded: Set<String>,
    ): List<PlannedReminder> =
        ResetReminderPolicy.expiringResets(accounts, settings, now)
            .filter { it.key !in reminded }
            .map { reset ->
                reset to maxOf(reset.expiresAt - ResetReminderPolicy.LEAD, now + DUE_DELAY)
            }
            .groupBy { (_, at) -> at.toLocalDateTime(zone).date }
            .map { (day, resets) ->
                PlannedReminder(
                    ui =
                        ReminderUi(
                            id = "reminder:$day",
                            fireAtEpochSeconds = resets.minOf { (_, at) -> at }.epochSeconds,
                            resets =
                                resets.map { (reset, _) ->
                                    val state = accounts.first { it.account.id == reset.account.id }
                                    ReminderItemUi(
                                        state.displayName(accounts),
                                        reset.expiresAt.epochSeconds,
                                    )
                                },
                        ),
                    keys = resets.map { (reset, _) -> reset.key },
                )
            }
            .sortedBy { it.ui.fireAtEpochSeconds }

    /** "Claude", or "Claude · sam@example.com" when the user has several Claude accounts. */
    private fun AccountState.displayName(accounts: List<AccountState>): String =
        if (accounts.hasSeveral(this)) "${account.name} · ${account.label}" else account.name

    private fun List<AccountState>.hasSeveral(state: AccountState): Boolean =
        count { it.account.provider == state.account.provider } > 1
}

/** A reminder, with the keys of the expiring resets it covers. */
internal class PlannedReminder(val ui: ReminderUi, val keys: List<String>)
