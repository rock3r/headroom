package dev.sebastiano.headroom.island

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import dev.sebastiano.headroom.HeadroomApplication
import dev.sebastiano.headroom.model.MotionPreference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.launch

/**
 * The reset island's window owner. It is an accessibility service only because an accessibility
 * overlay is the one kind of window that can draw above the status bar, the shade and the lock
 * screen. It asks for no events, no window content and no key access, so it cannot see the screen
 * or other apps. It draws the island when [IslandHub] hands it a request, and does nothing else.
 */
class ResetIslandService : AccessibilityService() {
    private var scope: CoroutineScope? = null
    private var window: IslandWindow? = null
    private var hub: IslandHub? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        val app = application as? HeadroomApplication ?: return
        val hub = app.islandHub.also { this.hub = it }
        val islandWindow =
            IslandWindow(this, getSystemService(WindowManager::class.java)).also { window = it }
        val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope = mainScope
        mainScope.launch {
            // Say "connected" only once the collection has started, so no request is lost.
            hub.requests
                .onSubscription { hub.serviceConnected(true) }
                .collect { request ->
                    val reduceMotion =
                        app.graph.settings.settings.first().motion == MotionPreference.Reduced
                    val geometry =
                        getSystemService(WindowManager::class.java)
                            .measureIsland(resources.displayMetrics.density)
                    islandWindow.show(request, geometry, reduceMotion)
                }
        }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        disconnect()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        disconnect()
        super.onDestroy()
    }

    private fun disconnect() {
        hub?.serviceConnected(false)
        hub = null
        window?.hide()
        window = null
        scope?.cancel()
        scope = null
    }

    // The service asks for no events, so these are never called.
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit
}
