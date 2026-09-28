package dev.sebastiano.headroom.model

import java.time.Duration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Whether the app and the widgets show how much of a limit is used, or how much is left. */
public enum class QuotaDisplay {
    Used,
    Left;

    /** The percentage to show for a window that is [usedPercent] used. */
    public fun percent(usedPercent: Double): Double =
        when (this) {
            Used -> usedPercent
            Left -> (FULL_PERCENT - usedPercent).coerceAtLeast(0.0)
        }

    private companion object {
        const val FULL_PERCENT = 100.0
    }
}

/** How often the app syncs in the background. [OnOpen] turns background sync off. */
public enum class SyncFrequency(
    /** The period of the background sync, or null when there is none. */
    public val period: Duration?
) {
    /** The shortest period WorkManager allows. */
    Minutes15(Duration.ofMinutes(QUARTER_HOUR_MINUTES)),
    Minutes30(Duration.ofMinutes(HALF_HOUR_MINUTES)),
    Hour1(Duration.ofHours(1)),
    Hours3(Duration.ofHours(THREE_HOURS)),
    Hours6(Duration.ofHours(SIX_HOURS)),
    /** Only when the user opens the app, pulls to refresh or taps a widget. */
    OnOpen(null),
}

private const val QUARTER_HOUR_MINUTES = 15L
private const val HALF_HOUR_MINUTES = 30L
private const val THREE_HOURS = 3L
private const val SIX_HOURS = 6L

/** The user's app settings. The defaults are how the app behaved before it had settings. */
public data class AppSettings(
    val quotaDisplay: QuotaDisplay = QuotaDisplay.Used,
    val syncFrequency: SyncFrequency = SyncFrequency.Minutes15,
)

/** Reads and stores the [AppSettings]. */
public interface SettingsRepository {
    public val settings: Flow<AppSettings>

    public suspend fun setQuotaDisplay(display: QuotaDisplay)

    public suspend fun setSyncFrequency(frequency: SyncFrequency)
}

/** Settings kept in memory, for demo builds, previews and tests without the data layer. */
public class InMemorySettingsRepository(initial: AppSettings = AppSettings()) : SettingsRepository {
    private val state = MutableStateFlow(initial)

    override val settings: StateFlow<AppSettings> = state.asStateFlow()

    override suspend fun setQuotaDisplay(display: QuotaDisplay) {
        state.update { it.copy(quotaDisplay = display) }
    }

    override suspend fun setSyncFrequency(frequency: SyncFrequency) {
        state.update { it.copy(syncFrequency = frequency) }
    }
}
