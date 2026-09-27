package dev.sebastiano.headroom.appdata

import dev.sebastiano.headroom.model.AlertPreferences
import dev.sebastiano.headroom.model.QuotaWindow
import dev.sebastiano.headroom.model.ResetPolicy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * Alert switches kept in memory. Demo mode and tests use it until the DataStore-backed
 * implementation from the data layer is wired into [dev.sebastiano.headroom.AppGraph].
 *
 * Windows that [ResetPolicy] never alerts for stay off whatever is stored; the others fall back to
 * [ResetPolicy.alertsByDefault].
 */
class InMemoryAlertPreferences : AlertPreferences {
    private val switches = MutableStateFlow<Map<Pair<String, String>, Boolean>>(emptyMap())

    override fun isEnabled(accountId: String, window: QuotaWindow): Flow<Boolean> =
        switches
            .map { stored ->
                ResetPolicy.canAlert(window) &&
                    (stored[accountId to window.id] ?: ResetPolicy.alertsByDefault(window))
            }
            .distinctUntilChanged()

    override suspend fun setEnabled(accountId: String, windowId: String, enabled: Boolean) {
        switches.update { it + ((accountId to windowId) to enabled) }
    }
}
