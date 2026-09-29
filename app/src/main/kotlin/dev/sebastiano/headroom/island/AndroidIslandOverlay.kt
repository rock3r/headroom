package dev.sebastiano.headroom.island

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.provider.Settings
import android.util.Log
import android.view.Display
import android.view.WindowManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The island's window for `Overlay` mode: a `TYPE_APPLICATION_OVERLAY` window that hangs below the
 * status bar. It exists only while an island plays. Every method must be called on the main thread,
 * except [isAllowed] and [awaitIdle].
 *
 * The window is added from a window context, because that is how an app that is not in front gets a
 * window manager for its own overlay type. A tap on the pill runs [openApp].
 *
 * [reduceMotion] says whether the user turned motion off in Headroom's settings.
 */
internal class AndroidIslandOverlay(
    private val context: Context,
    private val reduceMotion: () -> Boolean,
    private val openApp: () -> Unit,
) : IslandOverlay {
    /** The window that is on screen, with what is needed to measure it again. */
    private class Live(
        val window: IslandWindow,
        val windowManager: WindowManager,
        val density: Float,
    )

    private var live: Live? = null
    private val idle = MutableStateFlow(true)

    /** True when no overlay window is on screen. */
    val isIdle: Boolean
        get() = idle.value

    override fun isAllowed(): Boolean = Settings.canDrawOverlays(context)

    override fun show(request: IslandRequest): Boolean {
        if (!isAllowed()) return false
        val current = live?.takeIf { it.window.isShowing } ?: newLive() ?: return false
        live = current
        // Set before the window is added, so an add that fails (and calls gone) ends up idle.
        idle.value = false
        current.window.show(request, measure(current), reduceMotion())
        return current.window.isShowing
    }

    override suspend fun awaitIdle() {
        // The window's own watchdog removes it well before this, so the timeout never ends a wait
        // that was still needed. It only means a bug cannot hold a worker forever.
        withTimeoutOrNull(MAX_WAIT_MILLIS) { idle.first { it } }
    }

    private fun newLive(): Live? {
        val windowContext =
            try {
                val display =
                    context
                        .getSystemService(DisplayManager::class.java)
                        .getDisplay(Display.DEFAULT_DISPLAY)
                context.createWindowContext(
                    display,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    null,
                )
            } catch (failure: UnsupportedOperationException) {
                Log.w(TAG, "No window context for the overlay island", failure)
                return null
            } catch (failure: IllegalArgumentException) {
                Log.w(TAG, "No window context for the overlay island", failure)
                return null
            }
        val windowManager = windowContext.getSystemService(WindowManager::class.java)
        val window =
            IslandWindow(
                context = windowContext,
                windowManager = windowManager,
                kind = IslandWindowKind.Application,
                onTap = openApp,
                onGone = {
                    idle.value = true
                    live = null
                },
            )
        return Live(window, windowManager, windowContext.resources.displayMetrics.density)
    }

    // The window takes the touches in its own area, including the little room around the pill that
    // the grow spring needs. That margin is only 8 dp, and only for a few seconds.
    private fun measure(current: Live): IslandGeometry =
        current.windowManager.measureIsland(current.density)

    private companion object {
        const val TAG = "HeadroomIsland"
        const val MAX_WAIT_MILLIS = 15_000L
    }
}

/**
 * The intent that opens Headroom, the same one the reset notification uses: it brings the app's
 * task to the front, or starts it.
 */
internal fun launchHeadroomIntent(context: Context): Intent? =
    context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }

/**
 * Starts [launchIntent] with [start], and returns false instead of crashing when there is nothing
 * to start or Android refuses to.
 */
internal fun openHeadroom(launchIntent: Intent?, start: (Intent) -> Unit): Boolean {
    if (launchIntent == null) return false
    return try {
        start(launchIntent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}
