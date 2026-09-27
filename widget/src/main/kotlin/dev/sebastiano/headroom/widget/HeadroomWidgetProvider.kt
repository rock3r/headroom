package dev.sebastiano.headroom.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.Context
import android.os.Bundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

/**
 * The widget provider behind each style. Widgets never fetch or store usage data: every event is
 * forwarded to the app through [HeadroomWidgetHost], and the app answers by calling
 * [WidgetUpdater.updateAll].
 */
public sealed class HeadroomWidgetProvider(internal val style: WidgetStyle) : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        context.widgetHost()?.onWidgetUpdateRequested(appWidgetIds)
    }

    /** The widget was resized or moved between the home and lock screens: its layout changes. */
    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        context.widgetHost()?.onWidgetUpdateRequested(intArrayOf(appWidgetId))
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val store = context.widgetHost()?.widgetConfigStore ?: return
        // goAsync keeps the process alive while the store writes. It is null when a test calls
        // this method directly instead of through a broadcast.
        val pending: BroadcastReceiver.PendingResult? = goAsync()
        scope.launch(Dispatchers.Main.immediate) {
            try {
                appWidgetIds.forEach { store.remove(it) }
            } finally {
                pending?.finish()
            }
        }
    }

    public companion object {
        private val scope: CoroutineScope = MainScope()

        /** The provider class for [style], for `AppWidgetManager` queries and pin requests. */
        public fun classFor(style: WidgetStyle): Class<out HeadroomWidgetProvider> =
            when (style) {
                WidgetStyle.Rings -> RingsWidgetProvider::class.java
                WidgetStyle.Bars -> BarsWidgetProvider::class.java
                WidgetStyle.Shape -> ShapeWidgetProvider::class.java
                WidgetStyle.Countdown -> CountdownWidgetProvider::class.java
            }

        /** The size to draw for when the launcher has not reported one yet. */
        public fun defaultSize(style: WidgetStyle): WidgetSize =
            when (style) {
                WidgetStyle.Bars -> WidgetSize(widthDp = BARS_WIDTH_DP, heightDp = BARS_HEIGHT_DP)
                WidgetStyle.Rings,
                WidgetStyle.Shape,
                WidgetStyle.Countdown -> WidgetSize(widthDp = SQUARE_DP, heightDp = SQUARE_DP)
            }

        private const val SQUARE_DP = 148f
        private const val BARS_WIDTH_DP = 300f
        private const val BARS_HEIGHT_DP = 110f
    }
}

public class RingsWidgetProvider : HeadroomWidgetProvider(WidgetStyle.Rings)

public class BarsWidgetProvider : HeadroomWidgetProvider(WidgetStyle.Bars)

public class ShapeWidgetProvider : HeadroomWidgetProvider(WidgetStyle.Shape)

public class CountdownWidgetProvider : HeadroomWidgetProvider(WidgetStyle.Countdown)

internal fun Context.widgetHost(): HeadroomWidgetHost? = applicationContext as? HeadroomWidgetHost
