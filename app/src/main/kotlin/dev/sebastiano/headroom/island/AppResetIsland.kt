package dev.sebastiano.headroom.island

import dev.sebastiano.headroom.data.reset.ResetIsland
import dev.sebastiano.headroom.model.Provider
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.withContext

/**
 * The app's answer to the data layer's [ResetIsland] seam. It shows a reset on the island only when
 * [isEnabled] says the user wants it and [IslandConditions.allowsIsland] says the screen is free
 * for it. The accessibility service draws the island when it is connected. Otherwise the overlay
 * window does, if the user allowed Display over other apps.
 *
 * The overlay window is added on [mainContext], the main dispatcher in the app, because a window
 * can only be added from the main thread and the reset checker runs on a background thread.
 */
internal class AppResetIsland(
    private val hub: IslandHub,
    private val isEnabled: suspend () -> Boolean,
    private val environment: IslandEnvironment,
    private val mainContext: CoroutineContext,
) : ResetIsland {
    override suspend fun show(provider: Provider, message: String): Boolean {
        val mode = hub.mode()
        val conditions =
            IslandConditions(
                enabled = isEnabled(),
                mode = mode,
                screenOn = environment.isScreenOn(),
                landscape = environment.isLandscape(),
                appInForeground = environment.isAppInForeground(),
                doNotDisturb = environment.isDoNotDisturb(),
                keyguardLocked = environment.isKeyguardLocked(),
            )
        if (!conditions.allowsIsland()) return false
        return when (mode) {
            IslandMode.Accessibility -> hub.show(provider, message)
            IslandMode.Overlay -> withContext(mainContext) { hub.showOverlay(provider, message) }
            IslandMode.None -> false
        }
    }

    override suspend fun awaitIdle() = hub.awaitIdle()
}
