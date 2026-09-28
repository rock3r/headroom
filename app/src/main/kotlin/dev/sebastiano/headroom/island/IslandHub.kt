package dev.sebastiano.headroom.island

import dev.sebastiano.headroom.model.Provider
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/** One reset to show on the island: whose logo, and which words. */
data class IslandRequest(
    val provider: Provider,
    val message: String,
    /** Numbers the requests, so the same words shown twice still count as two requests. */
    val serial: Long,
)

/**
 * The island's second window, for when the accessibility service is not available: a
 * `TYPE_APPLICATION_OVERLAY` window, which needs the user to allow Display over other apps.
 */
interface IslandOverlay {
    /** True when the user allowed Display over other apps. Reads the system, so it is current. */
    fun isAllowed(): Boolean

    /**
     * Shows [request] in the overlay window, and returns true when the window is on screen. Call it
     * on the main thread. It returns false, and shows nothing, when it is not allowed or the window
     * cannot be added.
     */
    fun show(request: IslandRequest): Boolean

    /** Returns once no overlay window is on screen. It returns at once when there is none. */
    suspend fun awaitIdle()

    companion object {
        /** No overlay window at all, for previews and tests. */
        val Unavailable: IslandOverlay =
            object : IslandOverlay {
                override fun isAllowed() = false

                override fun show(request: IslandRequest) = false

                override suspend fun awaitIdle() = Unit
            }
    }
}

/** What the settings screen needs from the reset island. */
interface ResetIslandAccess {
    /** True while the accessibility service that draws the island is connected. */
    val ready: StateFlow<Boolean>

    /** True when the user allowed Display over other apps, the fallback when [ready] is false. */
    val overlayAllowed: StateFlow<Boolean>

    /**
     * True when accessibility settings list the service as turned on. It can be true a moment
     * before [ready], while Android connects the service.
     */
    val enabledInSettings: StateFlow<Boolean>

    /** Reads the accessibility and overlay settings again. Call it when the user comes back. */
    fun refresh()

    /**
     * Shows the island with [provider]'s logo and [message] at once, for the Try button, in the
     * best mode available. It does not ask whether the screen is free. It returns false when
     * neither the service nor the overlay is available. Call it on the main thread.
     */
    fun showDemo(provider: Provider, message: String): Boolean

    companion object {
        /** No island at all, for previews and builds without the service. */
        val Unavailable: ResetIslandAccess =
            object : ResetIslandAccess {
                override val ready: StateFlow<Boolean> = MutableStateFlow(false)
                override val overlayAllowed: StateFlow<Boolean> = MutableStateFlow(false)
                override val enabledInSettings: StateFlow<Boolean> = MutableStateFlow(false)

                override fun refresh() = Unit

                override fun showDemo(provider: Provider, message: String) = false
            }
    }
}

/**
 * The in-process signal between the reset checker and the island's windows. Both run in the app's
 * process. The accessibility service says when it connects and listens to [requests]; the checker
 * and the Try button hand it a request. When the service is not connected, the [overlay] window
 * draws the island instead, if the user allowed it.
 *
 * [readEnabled] reads whether accessibility settings list the service as turned on.
 */
class IslandHub(
    private val readEnabled: () -> Boolean = { false },
    private val overlay: IslandOverlay = IslandOverlay.Unavailable,
) : ResetIslandAccess {
    private val connected = MutableStateFlow(false)
    private val enabled = MutableStateFlow(false)
    private val overlayPermitted = MutableStateFlow(overlay.isAllowed())
    private val serial = AtomicLong()

    // One slot, newest wins: a newer reset replaces one the island has not started on.
    private val pending =
        MutableSharedFlow<IslandRequest>(
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

    override val ready: StateFlow<Boolean> = connected.asStateFlow()

    override val overlayAllowed: StateFlow<Boolean> = overlayPermitted.asStateFlow()

    override val enabledInSettings: StateFlow<Boolean> = enabled.asStateFlow()

    /** The requests the service shows. Subscribe first, then call [serviceConnected]. */
    val requests: SharedFlow<IslandRequest> = pending.asSharedFlow()

    /** The service calls this when it connects, and when it goes away. */
    fun serviceConnected(isConnected: Boolean) {
        connected.value = isConnected
        if (isConnected) enabled.value = true
    }

    override fun refresh() {
        enabled.value = readEnabled()
        overlayPermitted.value = overlay.isAllowed()
    }

    /** The mode that works right now. It reads the overlay permission again. */
    fun mode(): IslandMode = resolveIslandMode(connected.value, overlay.isAllowed())

    override fun showDemo(provider: Provider, message: String): Boolean =
        when (mode()) {
            IslandMode.Accessibility -> show(provider, message)
            IslandMode.Overlay -> showOverlay(provider, message)
            IslandMode.None -> false
        }

    /**
     * Hands a request to the service. It returns true only when the service is connected and is
     * listening, so the caller knows the island really shows it.
     */
    fun show(provider: Provider, message: String): Boolean {
        if (!connected.value || pending.subscriptionCount.value == 0) return false
        return pending.tryEmit(IslandRequest(provider, message, serial.incrementAndGet()))
    }

    /**
     * Shows a request in the overlay window. Call it on the main thread. It returns true only when
     * the window is on screen.
     */
    fun showOverlay(provider: Provider, message: String): Boolean =
        overlay.show(IslandRequest(provider, message, serial.incrementAndGet()))

    /** Returns once no overlay window is on screen. See [IslandOverlay.awaitIdle]. */
    suspend fun awaitIdle() = overlay.awaitIdle()
}
