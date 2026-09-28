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
import dev.sebastiano.headroom.model.QuotaDisplay
import dev.sebastiano.headroom.model.ThemePalette
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
    private val render: WidgetRender = { context, state, appWidgetId, size, palette ->
        WidgetRenderer.remoteViews(
            WidgetRenderer.capture(context, state, appWidgetId, size, palette)
        )
    },
) {
    public constructor(configStore: WidgetConfigStore) : this(configStore, ::SystemAppWidgetGateway)

    /**
     * Redraws every placed widget of every style from [accounts] as they are at [now], showing how
     * much is used or how much is left as [display] says, in the colours of [palette].
     */
    public suspend fun updateAll(
        context: Context,
        accounts: List<AccountState>,
        now: Instant,
        display: QuotaDisplay = QuotaDisplay.Used,
        palette: ThemePalette = ThemePalette.Wallpaper,
    ) {
        val widgets = gateway(context)
        WidgetStyle.entries.forEach { style ->
            widgets.appWidgetIds(HeadroomWidgetProvider.classFor(style)).forEach { appWidgetId ->
                val config = configStore.get(appWidgetId) ?: WidgetConfig.defaultFor(style)
                val options = widgets.options(appWidgetId)
                val sizes =
                    options.widgetSizes().ifEmpty {
                        listOf(HeadroomWidgetProvider.defaultSize(style))
                    }
                val layouts = sizes.associateWith { size ->
                    val state =
                        WidgetUiState.from(
                            accounts,
                            config,
                            now,
                            size,
                            options.hostCategory(),
                            display,
                        )
                    render(context, state, appWidgetId, size, palette)
                }
                widgets.update(appWidgetId, layouts)
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
                render(context, state, PREVIEW_WIDGET_ID, size, ThemePalette.Wallpaper),
            )
        }
    }

    private companion object {
        const val PREVIEW_WIDGET_ID = AppWidgetManager.INVALID_APPWIDGET_ID
    }
}

internal typealias WidgetRender =
    suspend (Context, WidgetUiState, Int, WidgetSize, ThemePalette) -> RemoteViews

/** The parts of [AppWidgetManager] the updater uses, so tests can replace them. */
internal interface AppWidgetGateway {
    fun appWidgetIds(provider: Class<out HeadroomWidgetProvider>): IntArray

    fun options(appWidgetId: Int): Bundle

    /** Shows [layouts], one per size the launcher lists for the widget. */
    fun update(appWidgetId: Int, layouts: Map<WidgetSize, RemoteViews>)

    fun setPreview(provider: Class<out HeadroomWidgetProvider>, views: RemoteViews)
}

internal class SystemAppWidgetGateway(private val context: Context) : AppWidgetGateway {
    private val manager = AppWidgetManager.getInstance(context)

    override fun appWidgetIds(provider: Class<out HeadroomWidgetProvider>): IntArray =
        manager.getAppWidgetIds(ComponentName(context, provider))

    override fun options(appWidgetId: Int): Bundle = manager.getAppWidgetOptions(appWidgetId)

    override fun update(appWidgetId: Int, layouts: Map<WidgetSize, RemoteViews>) {
        manager.updateAppWidget(appWidgetId, layouts.toRemoteViews())
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
 * The widget sizes from the launcher's options, in dp. A launcher usually lists one size per
 * orientation; each gets its own layout, so the widget fills the space it has in both.
 */
internal fun Bundle.widgetSizes(): List<WidgetSize> {
    val sizes = getParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, SizeF::class.java)
    if (!sizes.isNullOrEmpty()) {
        return sizes.map { WidgetSize(it.width, it.height) }.distinct()
    }
    val width = getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
    val height = getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
    return if (width > 0 && height > 0) listOf(WidgetSize(width.toFloat(), height.toFloat()))
    else emptyList()
}

/** One layout as is, or several as views the launcher picks from by the widget's size. */
internal fun Map<WidgetSize, RemoteViews>.toRemoteViews(): RemoteViews =
    values.singleOrNull()
        ?: RemoteViews(
            entries.associate { (size, views) -> SizeF(size.widthDp, size.heightDp) to views }
        )

internal fun Bundle.hostCategory(): WidgetHostCategory =
    if (
        getInt(AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY) ==
            AppWidgetProviderInfo.WIDGET_CATEGORY_KEYGUARD
    ) {
        WidgetHostCategory.Keyguard
    } else {
        WidgetHostCategory.HomeScreen
    }
