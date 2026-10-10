package dev.sebastiano.headroom.shared

import dev.sebastiano.headroom.model.AppSettings
import dev.sebastiano.headroom.model.MotionPreference
import dev.sebastiano.headroom.model.OverviewSort
import dev.sebastiano.headroom.model.QuotaDisplay
import dev.sebastiano.headroom.model.SettingsRepository
import dev.sebastiano.headroom.model.SyncFrequency
import dev.sebastiano.headroom.model.ThemeMode
import dev.sebastiano.headroom.model.ThemePalette
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * The app's settings, the same as on Android, with ids Swift can switch on. Values are the enum
 * names with a lower-case first letter, such as `used`, `minutes15` or `soonestResetFirst`.
 */
public data class SettingsUi(
    /** `used` or `left`. */
    val quotaDisplay: String,
    /** `minutes15`, `minutes30`, `hour1`, `hours3`, `hours6` or `onOpen`. */
    val syncFrequency: String,
    /** How often a background sync may run, in minutes, or null for `onOpen`. */
    val syncMinutes: Int?,
    /** `system`, `light` or `dark`. */
    val theme: String,
    val reduceMotion: Boolean,
    /**
     * `wallpaper` (the system accent on iOS), `coral`, `tangerine`, `lemon`, `lime`, `lagoon`,
     * `sky`, `grape` or `bubblegum`.
     */
    val palette: String,
    val overviewSort: String,
    val refreshShimmer: Boolean,
    val resetConfetti: Boolean,
    /** The reset island: on iOS, the next reset as a Live Activity. */
    val resetIsland: Boolean,
    val redeemClaudeResets: Boolean,
    val resetExpiryReminders: Boolean,
)

/** Reads and changes the settings. Unknown ids are ignored. */
public class HeadroomSettings
internal constructor(
    private val repository: SettingsRepository,
    private val scope: CoroutineScope,
) {
    /** Calls [onChange] with the settings now and at every change, until [Watch.cancel]. */
    public fun watch(onChange: (SettingsUi) -> Unit): Watch =
        scope.watch(repository.settings.map(::toUi), onChange)

    public fun setQuotaDisplay(id: String): Unit =
        set(id, QuotaDisplay.entries, repository::setQuotaDisplay)

    public fun setSyncFrequency(id: String): Unit =
        set(id, SyncFrequency.entries, repository::setSyncFrequency)

    public fun setTheme(id: String): Unit = set(id, ThemeMode.entries, repository::setTheme)

    public fun setPalette(id: String): Unit = set(id, ThemePalette.entries, repository::setPalette)

    public fun setOverviewSort(id: String): Unit =
        set(id, OverviewSort.entries, repository::setOverviewSort)

    public fun setReduceMotion(enabled: Boolean) {
        scope.launch {
            repository.setMotion(if (enabled) MotionPreference.Reduced else MotionPreference.System)
        }
    }

    /**
     * Sets one of the switches: `refreshShimmer`, `resetConfetti`, `resetIsland`,
     * `redeemClaudeResets` or `resetExpiryReminders`.
     */
    public fun setSwitch(id: String, enabled: Boolean) {
        val setter: suspend (Boolean) -> Unit =
            when (id) {
                "refreshShimmer" -> repository::setRefreshShimmer
                "resetConfetti" -> repository::setResetConfetti
                "resetIsland" -> repository::setResetIsland
                "redeemClaudeResets" -> repository::setRedeemClaudeResets
                "resetExpiryReminders" -> repository::setResetExpiryReminders
                else -> return
            }
        scope.launch { setter(enabled) }
    }

    private fun <T : Enum<T>> set(id: String, values: List<T>, setter: suspend (T) -> Unit) {
        val value = values.firstOrNull { it.id() == id } ?: return
        scope.launch { setter(value) }
    }

    internal companion object {
        fun toUi(settings: AppSettings): SettingsUi =
            SettingsUi(
                quotaDisplay = settings.quotaDisplay.id(),
                syncFrequency = settings.syncFrequency.id(),
                syncMinutes = settings.syncFrequency.period?.inWholeMinutes?.toInt(),
                theme = settings.theme.id(),
                reduceMotion = settings.motion == MotionPreference.Reduced,
                palette = settings.palette.id(),
                overviewSort = settings.overviewSort.id(),
                refreshShimmer = settings.refreshShimmer,
                resetConfetti = settings.resetConfetti,
                resetIsland = settings.resetIsland,
                redeemClaudeResets = settings.redeemClaudeResets,
                resetExpiryReminders = settings.resetExpiryReminders,
            )
    }
}

/** An enum's id for Swift: its name with a lower-case first letter. */
internal fun Enum<*>.id(): String = name.replaceFirstChar { it.lowercase() }
