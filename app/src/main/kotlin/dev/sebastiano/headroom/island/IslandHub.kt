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

/** What the settings screen needs from the reset island. */
interface ResetIslandAccess {
    /** True while the accessibility service that draws the island is connected. */
    val ready: StateFlow<Boolean>

    /**
     * True when accessibility settings list the service as turned on. It can be true a moment
     * before [ready], while Android connects the service.
     */
    val enabledInSettings: StateFlow<Boolean>

    /** Reads the accessibility settings again. Call it when the user comes back to the app. */
    fun refresh()

    /**
     * Shows the island with [provider]'s logo and [message] at once, for the Try button. It does
     * not ask whether the screen is free. It returns false when the service is not connected.
     */
    fun showDemo(provider: Provider, message: String): Boolean

    companion object {
        /** No island at all, for previews and builds without the service. */
        val Unavailable: ResetIslandAccess =
            object : ResetIslandAccess {
                override val ready: StateFlow<Boolean> = MutableStateFlow(false)
                override val enabledInSettings: StateFlow<Boolean> = MutableStateFlow(false)

                override fun refresh() = Unit

                override fun showDemo(provider: Provider, message: String) = false
            }
    }
}

/**
 * The in-process signal between the reset checker and the accessibility service. Both run in the
 * app's process. The service says when it connects and listens to [requests]; the checker and the
 * Try button hand it a request.
 *
 * [readEnabled] reads whether accessibility settings list the service as turned on.
 */
class IslandHub(private val readEnabled: () -> Boolean = { false }) : ResetIslandAccess {
    private val connected = MutableStateFlow(false)
    private val enabled = MutableStateFlow(false)
    private val serial = AtomicLong()

    // One slot, newest wins: a newer reset replaces one the island has not started on.
    private val pending =
        MutableSharedFlow<IslandRequest>(
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

    override val ready: StateFlow<Boolean> = connected.asStateFlow()

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
    }

    override fun showDemo(provider: Provider, message: String): Boolean = show(provider, message)

    /**
     * Hands a request to the service. It returns true only when the service is connected and is
     * listening, so the caller knows the island really shows it.
     */
    fun show(provider: Provider, message: String): Boolean {
        if (!connected.value || pending.subscriptionCount.value == 0) return false
        return pending.tryEmit(IslandRequest(provider, message, serial.incrementAndGet()))
    }
}
