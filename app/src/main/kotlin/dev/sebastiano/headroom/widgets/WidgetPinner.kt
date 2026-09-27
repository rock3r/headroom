package dev.sebastiano.headroom.widgets

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context

/** The widget styles the app offers. */
enum class WidgetStyle {
    Rings,
    Bars,
    Shape,
    Countdown,
}

/**
 * Asks the launcher to pin a widget. Returns false when that cannot happen (no provider for the
 * style yet, or a launcher that does not support pinning), so the UI can explain.
 */
fun interface WidgetPinner {
    fun requestPin(style: WidgetStyle): Boolean
}

/**
 * Pins through [AppWidgetManager.requestPinAppWidget]. [providerFor] maps a style to its
 * `AppWidgetProvider`; the widget module supplies it through [dev.sebastiano.headroom.AppGraph],
 * and until then every style returns null.
 */
class AppWidgetManagerPinner(
    private val context: Context,
    private val providerFor: (WidgetStyle) -> ComponentName?,
) : WidgetPinner {
    override fun requestPin(style: WidgetStyle): Boolean {
        val provider = providerFor(style) ?: return false
        val manager = context.getSystemService(AppWidgetManager::class.java) ?: return false
        return manager.isRequestPinAppWidgetSupported &&
            manager.requestPinAppWidget(provider, null, null)
    }
}
