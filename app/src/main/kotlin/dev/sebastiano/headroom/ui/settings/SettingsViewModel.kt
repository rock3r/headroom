package dev.sebastiano.headroom.ui.settings

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.sebastiano.headroom.model.AppSettings
import dev.sebastiano.headroom.model.MotionPreference
import dev.sebastiano.headroom.model.QuotaDisplay
import dev.sebastiano.headroom.model.SettingsRepository
import dev.sebastiano.headroom.model.SyncFrequency
import dev.sebastiano.headroom.model.ThemeMode
import dev.sebastiano.headroom.model.ThemePalette
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the settings screen shows. */
@Immutable
data class SettingsUiState(
    val quotaDisplay: QuotaDisplay,
    val syncFrequency: SyncFrequency,
    /** The app's version name, shown at the bottom of the screen. */
    val appVersion: String,
    val theme: ThemeMode = ThemeMode.System,
    val motion: MotionPreference = MotionPreference.System,
    val palette: ThemePalette = ThemePalette.Wallpaper,
    val refreshShimmer: Boolean = true,
    val resetConfetti: Boolean = true,
    val resetIsland: Boolean = false,
    val redeemClaudeResets: Boolean = false,
    val resetExpiryReminders: Boolean = true,
)

/** Reads the settings from the [settings] repository and stores the user's choices in it. */
class SettingsViewModel(private val settings: SettingsRepository, appVersion: String) :
    ViewModel() {
    val state: StateFlow<SettingsUiState> =
        settings.settings
            .map { it.toUiState(appVersion) }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(STOP_TIMEOUT),
                AppSettings().toUiState(appVersion),
            )

    fun setQuotaDisplay(display: QuotaDisplay) {
        viewModelScope.launch { settings.setQuotaDisplay(display) }
    }

    fun setSyncFrequency(frequency: SyncFrequency) {
        viewModelScope.launch { settings.setSyncFrequency(frequency) }
    }

    fun setTheme(theme: ThemeMode) {
        viewModelScope.launch { settings.setTheme(theme) }
    }

    fun setMotion(motion: MotionPreference) {
        viewModelScope.launch { settings.setMotion(motion) }
    }

    fun setPalette(palette: ThemePalette) {
        viewModelScope.launch { settings.setPalette(palette) }
    }

    fun setRefreshShimmer(enabled: Boolean) {
        viewModelScope.launch { settings.setRefreshShimmer(enabled) }
    }

    fun setResetConfetti(enabled: Boolean) {
        viewModelScope.launch { settings.setResetConfetti(enabled) }
    }

    fun setResetIsland(enabled: Boolean) {
        viewModelScope.launch { settings.setResetIsland(enabled) }
    }

    fun setRedeemClaudeResets(enabled: Boolean) {
        viewModelScope.launch { settings.setRedeemClaudeResets(enabled) }
    }

    fun setResetExpiryReminders(enabled: Boolean) {
        viewModelScope.launch { settings.setResetExpiryReminders(enabled) }
    }

    private fun AppSettings.toUiState(appVersion: String) =
        SettingsUiState(
            quotaDisplay,
            syncFrequency,
            appVersion,
            theme,
            motion,
            palette,
            refreshShimmer,
            resetConfetti,
            resetIsland,
            redeemClaudeResets,
            resetExpiryReminders,
        )

    private companion object {
        const val STOP_TIMEOUT = 5_000L
    }
}
