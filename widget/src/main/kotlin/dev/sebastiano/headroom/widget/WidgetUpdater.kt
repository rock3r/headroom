package dev.sebastiano.headroom.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.util.SizeF
import android.widget.RemoteViews
import dev.sebastiano.headroom.model.AccountState
import dev.sebastiano.headroom.model.DemoData
import dev.sebastiano.headroom.widget.render.WidgetRenderer
import java.time.Instant

/**
 * Redraws the widgets. The app calls [updateAll] after every sync and whenever [HeadroomWidgetHost]
 * asks for fresh content; widgets never read data on their own.
 */
public class WidgetUpdater
internal constructor(
    private val configStore: WidgetConfigStore,
    private val gateway: (Context) -> AppWidgetGateway = ::SystemAppWidgetGateway,
    private val render: WidgetRender = { context, state, appWidgetId, size ->
        WidgetRenderer.remoteViews(WidgetRenderer.capture(context, state, appWidgetId, size))
    },
) {
    public constructor(configStore: WidgetConfigStore) : this(configStore, ::SystemAppWidgetGateway)

    /** Redraws every placed widget of every style from [accounts] as they are at [now]. */
    public suspend fun updateAll(context: Context, accounts: List<AccountState>, now: Instant) {
        val widgets = gateway(context)
        WidgetStyle.entries.forEach { style ->
            widgets.appWidgetIds(HeadroomWidgetProvider.classFor(style)).forEach { appWidgetId ->
                val config = configStore.get(appWidgetId) ?: WidgetConfig.defaultFor(style)
                val options = widgets.options(appWidgetId)
                val size = options.widgetSize() ?: HeadroomWidgetProvider.defaultSize(style)
                val state = WidgetUiState.from(accounts, config, now, size, options.hostCategory())
                widgets.update(appWidgetId, render(context, state, appWidgetId, size))
            }
        }
    }

    /**
     * Publishes a picker preview for each style, drawn from demo data. The system limits how often
     * this works, so call it rarely, for example once after an app update.
     */
    public suspend fun publishPreviews(context: Context, now: Instant) {
        val widgets = gateway(context)
        val accounts = DemoData.accounts(now)
        WidgetStyle.entries.forEach { style ->
            val size = HeadroomWidgetProvider.defaultSize(style)
            val state =
                WidgetUiState.from(
                    accounts,
                    WidgetConfig.defaultFor(style),
                    now,
                    size,
                    WidgetHostCategory.HomeScreen,
                )
            widgets.setPreview(
                HeadroomWidgetProvider.classFor(style),
                render(context, state, PREVIEW_WIDGET_ID, size),
            )
        }
    }

    private companion object {
        const val PREVIEW_WIDGET_ID = AppWidgetManager.INVALID_APPWIDGET_ID
    }
}

internal typealias WidgetRender = suspend (Context, WidgetUiState, Int, WidgetSize) -> RemoteViews

/** The parts of [AppWidgetManager] the updater uses, so tests can replace them. */
internal interface AppWidgetGateway {
    fun appWidgetIds(provider: Class<out HeadroomWidgetProvider>): IntArray

    fun options(appWidgetId: Int): Bundle

    fun update(appWidgetId: Int, views: RemoteViews)

    fun setPreview(provider: Class<out HeadroomWidgetProvider>, views: RemoteViews)
}

internal class SystemAppWidgetGateway(private val context: Context) : AppWidgetGateway {
    private val manager = AppWidgetManager.getInstance(context)

    override fun appWidgetIds(provider: Class<out HeadroomWidgetProvider>): IntArray =
        manager.getAppWidgetIds(ComponentName(context, provider))

    override fun options(appWidgetId: Int): Bundle = manager.getAppWidgetOptions(appWidgetId)

    override fun update(appWidgetId: Int, views: RemoteViews) {
        manager.updateAppWidget(appWidgetId, views)
    }

    override fun setPreview(provider: Class<out HeadroomWidgetProvider>, views: RemoteViews) {
        manager.setWidgetPreview(
            ComponentName(context, provider),
            AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN,
            views,
        )
    }
}

/**
 * The widget size from the launcher's options. When the launcher lists several sizes (one per
 * orientation), the smallest width and height are used so nothing overflows.
 */
internal fun Bundle.widgetSize(): WidgetSize? {
    val sizes = getParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, SizeF::class.java)
    if (!sizes.isNullOrEmpty()) {
        return WidgetSize(sizes.minOf { it.width }, sizes.minOf { it.height })
    }
    val width = getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
    val height = getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
    return if (width > 0 && height > 0) WidgetSize(width.toFloat(), height.toFloat()) else null
}

internal fun Bundle.hostCategory(): WidgetHostCategory =
    if (
        getInt(AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY) ==
            AppWidgetProviderInfo.WIDGET_CATEGORY_KEYGUARD
    ) {
        WidgetHostCategory.Keyguard
    } else {
        WidgetHostCategory.HomeScreen
    }
