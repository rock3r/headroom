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

/** Light or dark. [System] follows the device's dark theme setting. */
public enum class ThemeMode {
    System,
    Light,
    Dark,
}

/**
 * How much the app moves. [System] follows the device: when the user removes animations there,
 * nothing moves. [Reduced] keeps motion to short crossfades even when the device animates, and
 * stops decorative and endless animations. There is no option for more motion than the device
 * allows, because an app must not animate when the user has turned animations off.
 */
public enum class MotionPreference {
    System,
    Reduced,
}

/**
 * Where the app's colours come from. [Wallpaper] is the device's dynamic palette; the others are
 * fixed palettes, each built from one bright seed colour, in order round the colour wheel.
 */
public enum class ThemePalette {
    Wallpaper,
    Coral,
    Tangerine,
    Lemon,
    Lime,
    Lagoon,
    Sky,
    Grape,
    Bubblegum,
}

/**
 * How the overview orders the account cards. [YourOrder] keeps the order the accounts come in from
 * the repository. The quota sorts compare how much of the primary window is used, and the reset
 * sorts compare when the primary window resets. Accounts without that value always go last.
 */
public enum class OverviewSort {
    YourOrder,
    MostUsedFirst,
    LeastUsedFirst,
    SoonestResetFirst,
    LatestResetFirst,
}

/**
 * The user's app settings. The defaults are how the app behaved before it had settings, except the
 * delights, which are on until the user turns them off. The reset island is experimental and needs
 * accessibility access, so it is the one delight that stays off until the user turns it on. Using
 * Claude's resets is experimental too, and off until the user turns it on. Reminders before a reset
 * expires are on until the user turns them off.
 */
public data class AppSettings(
    val quotaDisplay: QuotaDisplay = QuotaDisplay.Used,
    val syncFrequency: SyncFrequency = SyncFrequency.Minutes15,
    val theme: ThemeMode = ThemeMode.System,
    val motion: MotionPreference = MotionPreference.System,
    val palette: ThemePalette = ThemePalette.Wallpaper,
    val overviewSort: OverviewSort = OverviewSort.YourOrder,
    /** A soft sheen sweeps over the app once when a refresh brings new data. */
    val refreshShimmer: Boolean = true,
    /** Confetti bursts from an account when its quota resets while the app is open. */
    val resetConfetti: Boolean = true,
    /**
     * A black pill grows out of the camera cutout when a quota resets while the app is in the
     * background. Experimental, and off by default.
     */
    val resetIsland: Boolean = false,
    /**
     * Headroom can use Claude's saved resets. Experimental, and off by default: see
     * [redeemsResetsExperimentally].
     */
    val redeemClaudeResets: Boolean = false,
    /**
     * A notification about a day before a reset the user can use expires: see
     * [ResetReminderPolicy].
     */
    val resetExpiryReminders: Boolean = true,
)

/** Reads and stores the [AppSettings]. */
public interface SettingsRepository {
    public val settings: Flow<AppSettings>

    public suspend fun setQuotaDisplay(display: QuotaDisplay)

    public suspend fun setSyncFrequency(frequency: SyncFrequency)

    public suspend fun setTheme(theme: ThemeMode)

    public suspend fun setMotion(motion: MotionPreference)

    public suspend fun setPalette(palette: ThemePalette)

    public suspend fun setOverviewSort(sort: OverviewSort)

    public suspend fun setRefreshShimmer(enabled: Boolean)

    public suspend fun setResetConfetti(enabled: Boolean)

    public suspend fun setResetIsland(enabled: Boolean)

    public suspend fun setRedeemClaudeResets(enabled: Boolean)

    public suspend fun setResetExpiryReminders(enabled: Boolean)
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

    override suspend fun setTheme(theme: ThemeMode) {
        state.update { it.copy(theme = theme) }
    }

    override suspend fun setMotion(motion: MotionPreference) {
        state.update { it.copy(motion = motion) }
    }

    override suspend fun setPalette(palette: ThemePalette) {
        state.update { it.copy(palette = palette) }
    }

    override suspend fun setOverviewSort(sort: OverviewSort) {
        state.update { it.copy(overviewSort = sort) }
    }

    override suspend fun setRefreshShimmer(enabled: Boolean) {
        state.update { it.copy(refreshShimmer = enabled) }
    }

    override suspend fun setResetConfetti(enabled: Boolean) {
        state.update { it.copy(resetConfetti = enabled) }
    }

    override suspend fun setResetIsland(enabled: Boolean) {
        state.update { it.copy(resetIsland = enabled) }
    }

    override suspend fun setRedeemClaudeResets(enabled: Boolean) {
        state.update { it.copy(redeemClaudeResets = enabled) }
    }

    override suspend fun setResetExpiryReminders(enabled: Boolean) {
        state.update { it.copy(resetExpiryReminders = enabled) }
    }
}
