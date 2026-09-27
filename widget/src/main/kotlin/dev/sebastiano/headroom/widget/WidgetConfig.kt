package dev.sebastiano.headroom.widget

import java.util.concurrent.ConcurrentHashMap

/** The four widget styles. Each has its own provider, so the widget picker lists all four. */
public enum class WidgetStyle(public val id: String) {
    Rings("rings"),
    Bars("bars"),
    Shape("shape"),
    Countdown("countdown");

    public companion object {
        public fun fromId(id: String): WidgetStyle? = entries.firstOrNull { it.id == id }
    }
}

/** Which limit a widget shows. `Weekly` means the account's primary window (monthly for some). */
public enum class WidgetWindow(public val id: String) {
    Weekly("weekly"),
    Session("session");

    public companion object {
        public fun fromId(id: String): WidgetWindow? = entries.firstOrNull { it.id == id }
    }
}

/** Where a widget takes its colours from. */
public enum class ColourMode(public val id: String) {
    /** The system dynamic palette, which follows the wallpaper. */
    Wallpaper("wallpaper"),
    /** One hue per account, so accounts are easy to tell apart. */
    PerAccount("account"),
    /** Only the on-surface colour. */
    Mono("mono");

    public companion object {
        public fun fromId(id: String): ColourMode? = entries.firstOrNull { it.id == id }
    }
}

/**
 * What one widget instance shows. The app stores one per app widget id.
 *
 * @property accountIds the accounts to show, in this order. Empty means every account.
 */
public data class WidgetConfig(
    val style: WidgetStyle,
    val accountIds: List<String> = emptyList(),
    val window: WidgetWindow = WidgetWindow.Weekly,
    val colourMode: ColourMode = ColourMode.Wallpaper,
) {
    public companion object {
        /** The config of a widget that the user has not edited yet. */
        public fun defaultFor(style: WidgetStyle): WidgetConfig = WidgetConfig(style)
    }
}

/** Per-widget configuration storage. The app provides a DataStore-backed implementation. */
public interface WidgetConfigStore {
    public suspend fun get(appWidgetId: Int): WidgetConfig?

    public suspend fun set(appWidgetId: Int, config: WidgetConfig)

    public suspend fun remove(appWidgetId: Int)
}

/** A [WidgetConfigStore] that keeps everything in memory, for tests and previews. */
public class InMemoryWidgetConfigStore : WidgetConfigStore {
    private val configs = ConcurrentHashMap<Int, WidgetConfig>()

    override suspend fun get(appWidgetId: Int): WidgetConfig? = configs[appWidgetId]

    override suspend fun set(appWidgetId: Int, config: WidgetConfig) {
        configs[appWidgetId] = config
    }

    override suspend fun remove(appWidgetId: Int) {
        configs.remove(appWidgetId)
    }
}
