package dev.sebastiano.headroom.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import dev.sebastiano.headroom.data.db.HeadroomDatabase
import dev.sebastiano.headroom.data.db.RoomQuotaRepository
import dev.sebastiano.headroom.data.db.RoomResetEventLog
import dev.sebastiano.headroom.data.prefs.DataStoreAlertPreferences
import dev.sebastiano.headroom.data.prefs.DataStorePromptDismissals
import dev.sebastiano.headroom.data.prefs.DataStoreSettingsRepository
import dev.sebastiano.headroom.model.Account
import dev.sebastiano.headroom.model.AlertPreferences
import dev.sebastiano.headroom.model.HistoryRetention
import dev.sebastiano.headroom.model.PromptDismissals
import dev.sebastiano.headroom.model.QuotaResult
import dev.sebastiano.headroom.model.ResetEventLog
import dev.sebastiano.headroom.model.SettingsRepository
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * What Headroom keeps on the device: the Room database of accounts, snapshots, history and reset
 * events, and the DataStore files of the settings and the alert switches. Each platform opens it
 * with its own `openHeadroomStorage`, which knows where the files go.
 */
public class HeadroomStorage
internal constructor(
    private val database: HeadroomDatabase,
    settingsStore: DataStore<Preferences>,
    alertStore: DataStore<Preferences>,
) {
    public val settings: SettingsRepository = DataStoreSettingsRepository(settingsStore)

    public val alertPreferences: AlertPreferences = DataStoreAlertPreferences(alertStore)

    /** The prompts the user dismissed, one per account. */
    public val promptDismissals: PromptDismissals = DataStorePromptDismissals(alertStore)

    /** The alert switches as they change, to reschedule alarms when one is flipped. */
    public val alertChanges: Flow<Preferences> = alertStore.data

    /**
     * The repository of the accounts and their latest snapshots. [fetch] reads one account's quotas
     * when it is refreshed.
     */
    public fun accounts(
        fetch: suspend (Account) -> QuotaResult,
        clock: () -> Instant,
        scope: CoroutineScope,
        retention: suspend () -> HistoryRetention = { settings.settings.first().historyRetention },
    ): AccountsRepository =
        RoomQuotaRepository(
            database.quotaDao(),
            database.accountOrderDao(),
            fetch,
            clock,
            scope,
            retention,
        )

    /** The history of resets that happened, for the Stats tab. */
    public fun resetEvents(clock: () -> Instant): ResetEventLog =
        RoomResetEventLog(database.quotaDao(), clock)

    /** Closes the database. */
    public fun close() {
        database.close()
    }
}
