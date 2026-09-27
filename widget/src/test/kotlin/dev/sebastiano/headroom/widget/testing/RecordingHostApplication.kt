package dev.sebastiano.headroom.widget.testing

import android.app.Application
import dev.sebastiano.headroom.widget.HeadroomWidgetHost
import dev.sebastiano.headroom.widget.InMemoryWidgetConfigStore
import dev.sebastiano.headroom.widget.WidgetConfigStore

/** An application that records what the widget module asks of the app. */
class RecordingHostApplication : Application(), HeadroomWidgetHost {
    override val widgetConfigStore: WidgetConfigStore = InMemoryWidgetConfigStore()
    val refreshRequests = mutableListOf<List<Int>>()
    val updateRequests = mutableListOf<List<Int>>()

    override fun onWidgetRefreshRequested(appWidgetIds: IntArray) {
        refreshRequests += appWidgetIds.toList()
    }

    override fun onWidgetUpdateRequested(appWidgetIds: IntArray) {
        updateRequests += appWidgetIds.toList()
    }
}
