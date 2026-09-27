package dev.sebastiano.headroom

import android.app.Application
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.work.Configuration
import dev.sebastiano.headroom.data.DataGraph
import dev.sebastiano.headroom.data.DataGraphOwner
import dev.sebastiano.headroom.model.AccountState
import dev.sebastiano.headroom.widget.HeadroomWidgetHost
import dev.sebastiano.headroom.widget.WidgetConfigStore
import dev.sebastiano.headroom.widget.WidgetUpdater
import dev.sebastiano.headroom.widgets.DataStoreWidgetConfigStore
import java.time.Instant
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

/**
 * Owns the process-wide graphs: the data layer (Room, sync, reset alarms), the UI's [AppGraph],
 * WorkManager's worker factory and the widget host. Instrumented tests swap in a graph on fakes.
 */
open class HeadroomApplication :
    Application(), DataGraphOwner, HeadroomWidgetHost, Configuration.Provider {
    private val appScope = processScope()

    override val dataGraph: DataGraph by lazy { DataGraph(this, appScope) }

    val graph: AppGraph by lazy { createGraph() }

    /** False in instrumented tests, which run on fakes and must not start background work. */
    protected open val usesDataLayer: Boolean = true

    protected open fun createGraph(): AppGraph =
        AppGraph.create(this, scope = appScope, data = dataGraph)

    override val widgetConfigStore: WidgetConfigStore by lazy {
        DataStoreWidgetConfigStore(
            PreferenceDataStoreFactory.create(scope = appScope) {
                preferencesDataStoreFile(WIDGETS_STORE)
            }
        )
    }

    private val widgetUpdater by lazy { WidgetUpdater(widgetConfigStore) }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(dataGraph.workerFactory).build()

    @OptIn(FlowPreview::class)
    override fun onCreate() {
        super.onCreate()
        if (!usesDataLayer) return
        dataGraph.start()
        // Widgets redraw whenever the stored data changes; they never fetch on their own.
        appScope.launch {
            dataGraph.repository.accounts.debounce(WIDGET_UPDATE_DEBOUNCE_MS).collect {
                updateWidgets(it)
            }
        }
    }

    override fun onWidgetRefreshRequested(appWidgetIds: IntArray) {
        if (!usesDataLayer) return
        appScope.launch {
            dataGraph.repository.refresh()
            updateWidgets(dataGraph.repository.current())
        }
    }

    override fun onWidgetUpdateRequested(appWidgetIds: IntArray) {
        if (!usesDataLayer) return
        appScope.launch { updateWidgets(dataGraph.repository.current()) }
    }

    private suspend fun updateWidgets(accounts: List<AccountState>) {
        widgetUpdater.updateAll(this, accounts, Instant.now())
    }

    private companion object {
        /** The process-wide scope; the dispatcher is a parameter so tests could replace it. */
        fun processScope(dispatcher: CoroutineDispatcher = Dispatchers.Default): CoroutineScope =
            CoroutineScope(SupervisorJob() + dispatcher)

        const val WIDGETS_STORE = "widgets"
        const val WIDGET_UPDATE_DEBOUNCE_MS = 300L
    }
}
