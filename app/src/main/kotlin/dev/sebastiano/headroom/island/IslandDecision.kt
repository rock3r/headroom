package dev.sebastiano.headroom.island

/** Everything that decides whether a reset shows on the island. Each field is one fact. */
internal data class IslandConditions(
    /** The user turned the reset island on in the settings. */
    val enabled: Boolean,
    /** The accessibility service that owns the overlay window is connected. */
    val serviceConnected: Boolean,
    val screenOn: Boolean,
    val landscape: Boolean,
    /** Headroom itself is on screen. The in-app confetti covers that case. */
    val appInForeground: Boolean,
    val doNotDisturb: Boolean,
)

/** Why the island stays hidden. */
internal enum class IslandBlock {
    SettingOff,
    ServiceNotConnected,
    ScreenOff,
    Landscape,
    AppInForeground,
    DoNotDisturb,
}

/** The first reason the island must stay hidden, or null when it may show. */
internal fun IslandConditions.blockedBy(): IslandBlock? =
    when {
        !enabled -> IslandBlock.SettingOff
        !serviceConnected -> IslandBlock.ServiceNotConnected
        !screenOn -> IslandBlock.ScreenOff
        landscape -> IslandBlock.Landscape
        appInForeground -> IslandBlock.AppInForeground
        doNotDisturb -> IslandBlock.DoNotDisturb
        else -> null
    }

internal fun IslandConditions.allowsIsland(): Boolean = blockedBy() == null
