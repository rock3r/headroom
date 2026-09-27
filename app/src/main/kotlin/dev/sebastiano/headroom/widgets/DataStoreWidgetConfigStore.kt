package dev.sebastiano.headroom.widgets

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.sebastiano.headroom.widget.ColourMode
import dev.sebastiano.headroom.widget.WidgetConfig
import dev.sebastiano.headroom.widget.WidgetConfigStore
import dev.sebastiano.headroom.widget.WidgetStyle
import dev.sebastiano.headroom.widget.WidgetWindow
import kotlinx.coroutines.flow.first

/** Widget configs in DataStore, one entry per app widget id. */
class DataStoreWidgetConfigStore(private val store: DataStore<Preferences>) : WidgetConfigStore {
    override suspend fun get(appWidgetId: Int): WidgetConfig? =
        store.data.first()[key(appWidgetId)]?.let(::decode)

    override suspend fun set(appWidgetId: Int, config: WidgetConfig) {
        store.edit { it[key(appWidgetId)] = encode(config) }
    }

    override suspend fun remove(appWidgetId: Int) {
        store.edit { it.remove(key(appWidgetId)) }
    }

    private fun key(appWidgetId: Int) = stringPreferencesKey("widget:$appWidgetId")

    private fun encode(config: WidgetConfig): String =
        listOf(
                config.style.id,
                config.window.id,
                config.colourMode.id,
                config.accountIds.joinToString(ACCOUNT_SEPARATOR),
            )
            .joinToString(FIELD_SEPARATOR)

    private fun decode(raw: String): WidgetConfig? {
        val fields = raw.split(FIELD_SEPARATOR)
        if (fields.size != FIELD_COUNT) return null
        val values = fields.iterator()
        val style = WidgetStyle.fromId(values.next()) ?: return null
        val window = WidgetWindow.fromId(values.next()) ?: return null
        val colourMode = ColourMode.fromId(values.next()) ?: return null
        val accounts = values.next().split(ACCOUNT_SEPARATOR).filter { it.isNotEmpty() }
        return WidgetConfig(style, accounts, window, colourMode)
    }

    private companion object {
        const val FIELD_SEPARATOR = "|"
        const val ACCOUNT_SEPARATOR = ","
        const val FIELD_COUNT = 4
    }
}
