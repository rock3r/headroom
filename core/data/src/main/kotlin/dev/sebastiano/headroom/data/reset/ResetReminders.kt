package dev.sebastiano.headroom.data.reset

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.content.edit
import dev.sebastiano.headroom.model.AccountState
import dev.sebastiano.headroom.model.AppSettings
import dev.sebastiano.headroom.model.ExpiringReset
import dev.sebastiano.headroom.model.QuotaErrorKind
import dev.sebastiano.headroom.model.QuotaRepository
import dev.sebastiano.headroom.model.ResetReminderPolicy
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Which resets were reminded about, with when each expires, and the day of the last reminder. */
internal data class ReminderRecord(
    val reminded: Map<String, Instant> = emptyMap(),
    val lastDay: LocalDate? = null,
)

/** Keeps the [ReminderRecord] across process deaths, so a reset is reminded about once. */
internal interface ResetReminderLedger {
    fun load(): ReminderRecord

    fun save(record: ReminderRecord)
}

internal class SharedPreferencesResetReminderLedger(context: Context) : ResetReminderLedger {
    private val store = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    override fun load(): ReminderRecord =
        ReminderRecord(
            reminded =
                store.all
                    .filterKeys { it.startsWith(RESET_PREFIX) }
                    .mapNotNull { (key, value) ->
                        (value as? Long)?.let {
                            key.removePrefix(RESET_PREFIX) to Instant.ofEpochMilli(it)
                        }
                    }
                    .toMap(),
            lastDay =
                store
                    .getLong(LAST_DAY, Long.MIN_VALUE)
                    .takeIf { it != Long.MIN_VALUE }
                    ?.let { LocalDate.ofEpochDay(it) },
        )

    override fun save(record: ReminderRecord) {
        store.edit {
            clear()
            record.reminded.forEach { (key, expiresAt) ->
                putLong(RESET_PREFIX + key, expiresAt.toEpochMilli())
            }
            record.lastDay?.let { putLong(LAST_DAY, it.toEpochDay()) }
        }
    }

    private companion object {
        const val FILE = "reset_reminders"
        const val RESET_PREFIX = "reset:"
        const val LAST_DAY = "last_day"
    }
}

/** What [ResetReminders.remind] did. */
internal sealed interface ReminderOutcome {
    /** The reminder went out, or none was due. The next check is planned. */
    data object Done : ReminderOutcome

    /** The provider could not be reached, so the check must run again [after] this delay. */
    data class Retry(val after: Duration) : ReminderOutcome
}

/**
 * How long to wait before checking again when the refresh before a reminder could not reach the
 * provider, as when the phone is offline.
 */
internal object ResetReminderRetryPolicy {
    private val DELAYS: List<Duration> =
        listOf(
            Duration.ofMinutes(5),
            Duration.ofMinutes(15),
            Duration.ofMinutes(30),
            Duration.ofMinutes(60),
        )

    /**
     * The reminder never waits past this long before the soonest due reset expires. After that it
     * goes out from the stored resets, as a stale reminder is better than none.
     */
    val MIN_NOTICE: Duration = Duration.ofHours(12)

    /**
     * The delay after the given failed attempt (1-based), or null to post now. It is null once the
     * retries run out, or when waiting would bring [now] closer than [MIN_NOTICE] to [expiresAt].
     */
    fun delayAfterAttempt(attempt: Int, now: Instant, expiresAt: Instant): Duration? =
        DELAYS.getOrNull(attempt - 1)?.takeUnless {
            now.plus(it).isAfter(expiresAt.minus(MIN_NOTICE))
        }
}

/** Posts the reminder that [resets] expire soon. [accounts] are all the user's accounts. */
internal fun interface ResetReminderNotifier {
    fun notifyExpiring(resets: List<ExpiringReset>, accounts: List<AccountState>)
}

/**
 * Reminds the user before a reset expires unused, as [ResetReminderPolicy] decides. One alarm, set
 * through [schedule], wakes [remind] when a reminder is due. [reschedule] runs after every sync and
 * settings change, so the alarm follows the resets the accounts hold.
 */
internal class ResetReminders(
    private val repository: QuotaRepository,
    private val settings: suspend () -> AppSettings,
    private val ledger: ResetReminderLedger,
    private val notifier: ResetReminderNotifier,
    private val schedule: (Instant?) -> Unit,
    private val clock: () -> Instant = Instant::now,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) {
    suspend fun reschedule() {
        val now = clock()
        val record = ledger.load()
        val resets = ResetReminderPolicy.expiringResets(repository.current(), settings(), now)
        schedule(
            ResetReminderPolicy.nextCheck(
                resets,
                record.reminded.keys,
                record.lastDay,
                now,
                zone(),
            )
        )
    }

    /**
     * Posts one reminder for the resets that are due, then plans the next check. The accounts are
     * refreshed first, so a reset the user used elsewhere since the last sync is left out. When a
     * refresh cannot reach the provider, nothing is posted and the check asks to run again later,
     * as [ResetReminderRetryPolicy] decides. [attempt] counts these checks, starting at 1.
     */
    suspend fun remind(attempt: Int = 1): ReminderOutcome {
        val before = due(repository.current())
        val refreshed = before.map { it.account.id }.distinct()
        refreshed.forEach { repository.refresh(it) }
        if (before.isNotEmpty()) {
            val accounts = repository.current()
            val offline = accounts.any {
                it.account.id in refreshed && it.lastError == QuotaErrorKind.Network
            }
            val retry =
                if (offline) {
                    ResetReminderRetryPolicy.delayAfterAttempt(
                        attempt,
                        clock(),
                        before.minOf { it.expiresAt },
                    )
                } else {
                    null
                }
            // The retry plans the next check itself, so the alarm is not set here.
            if (retry != null) return ReminderOutcome.Retry(retry)
            val due = due(accounts)
            if (due.isNotEmpty()) post(due, accounts)
        }
        reschedule()
        return ReminderOutcome.Done
    }

    private fun post(due: List<ExpiringReset>, accounts: List<AccountState>) {
        notifier.notifyExpiring(due, accounts)
        val now = clock()
        val record = ledger.load()
        ledger.save(
            ReminderRecord(
                // Expired resets can never be due again, so they are forgotten.
                reminded =
                    (record.reminded + due.associate { it.key to it.expiresAt }).filterValues {
                        it.isAfter(now)
                    },
                lastDay = now.atZone(zone()).toLocalDate(),
            )
        )
    }

    private suspend fun due(accounts: List<AccountState>): List<ExpiringReset> {
        val now = clock()
        val record = ledger.load()
        return ResetReminderPolicy.due(
            ResetReminderPolicy.expiringResets(accounts, settings(), now),
            record.reminded.keys,
            record.lastDay,
            now,
            zone(),
        )
    }
}

/**
 * Keeps the one alarm that wakes the reminder check. It need not be exact: the reminder only has to
 * come about a day before the reset expires.
 */
internal class ResetReminderAlarm(private val context: Context) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun schedule(at: Instant?) {
        val intent =
            PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                Intent(context, ResetReminderReceiver::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        if (at == null) {
            alarmManager.cancel(intent)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), intent)
        }
    }

    private companion object {
        const val REQUEST_CODE = 0x7e5e7
    }
}
