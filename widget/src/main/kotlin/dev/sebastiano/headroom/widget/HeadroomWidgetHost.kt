package dev.sebastiano.headroom.widget

/**
 * What the widgets need from the app. The app's `Application` class implements it: widget providers
 * and receivers are created by the system, so they find the app through the application context
 * instead of through constructor injection.
 */
public interface HeadroomWidgetHost {
    /** Where each widget's [WidgetConfig] lives. */
    public val widgetConfigStore: WidgetConfigStore

    /**
     * The user tapped a widget to refresh it. Run a sync for the widget's accounts; when it ends,
     * call [WidgetUpdater.updateAll]. Widgets never fetch data themselves.
     */
    public fun onWidgetRefreshRequested(appWidgetIds: IntArray)

    /**
     * The system needs new widget content: a widget was added, resized or moved to another surface,
     * or the device restarted. Call [WidgetUpdater.updateAll] with the latest stored data. No
     * network fetch is needed.
     */
    public fun onWidgetUpdateRequested(appWidgetIds: IntArray)
}
