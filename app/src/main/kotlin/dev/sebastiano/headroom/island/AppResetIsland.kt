package dev.sebastiano.headroom.island

import dev.sebastiano.headroom.data.reset.ResetIsland
import dev.sebastiano.headroom.model.Provider

/**
 * The app's answer to the data layer's [ResetIsland] seam. It shows a reset on the island only when
 * [isEnabled] says the user wants it and [IslandConditions.allowsIsland] says the screen is free
 * for it.
 */
internal class AppResetIsland(
    private val hub: IslandHub,
    private val isEnabled: suspend () -> Boolean,
    private val environment: IslandEnvironment,
) : ResetIsland {
    override suspend fun show(provider: Provider, message: String): Boolean {
        val conditions =
            IslandConditions(
                enabled = isEnabled(),
                serviceConnected = hub.ready.value,
                screenOn = environment.isScreenOn(),
                landscape = environment.isLandscape(),
                appInForeground = environment.isAppInForeground(),
                doNotDisturb = environment.isDoNotDisturb(),
            )
        return conditions.allowsIsland() && hub.show(provider, message)
    }
}
