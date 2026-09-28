package dev.sebastiano.headroom.ui.settings

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.sebastiano.headroom.model.AppSettings
import dev.sebastiano.headroom.model.QuotaDisplay
import dev.sebastiano.headroom.model.SettingsRepository
import dev.sebastiano.headroom.model.SyncFrequency
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

    private fun AppSettings.toUiState(appVersion: String) =
        SettingsUiState(quotaDisplay, syncFrequency, appVersion)

    private companion object {
        const val STOP_TIMEOUT = 5_000L
    }
}
