package dev.sebastiano.headroom.island

import android.content.Context
import android.graphics.PixelFormat
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dev.sebastiano.headroom.designsystem.HeadroomTheme

/**
 * Owns the overlay window of the island. The window is an accessibility overlay: it sits above the
 * status bar, the shade and the lock screen, and every touch goes through it to what is below.
 * [windowManager] must be the window manager of the accessibility service that owns the window.
 *
 * The window exists only while an island plays. It is only as big as the largest pill.
 */
internal class IslandWindow(
    private val context: Context,
    private val windowManager: WindowManager,
) {
    private var root: FrameLayout? = null
    private var owner: OverlayLifecycleOwner? = null
    private var params: WindowManager.LayoutParams? = null

    private var request by mutableStateOf<IslandRequest?>(null)
    private var geometry by mutableStateOf<IslandGeometry?>(null)
    private var reduceMotion by mutableStateOf(false)

    /** Shows [request], or swaps the words of the island that is already on screen. */
    fun show(request: IslandRequest, geometry: IslandGeometry, reduceMotion: Boolean) {
        this.request = request
        this.geometry = geometry
        this.reduceMotion = reduceMotion
        val attached = params
        if (root == null || attached == null) {
            attach(geometry)
        } else {
            place(attached, geometry)
            runCatching { windowManager.updateViewLayout(root, attached) }
                .onFailure { Log.w(TAG, "Could not move the island window", it) }
        }
    }

    /** Removes the window. Safe to call when nothing shows. */
    fun hide() {
        root?.let { view -> runCatching { windowManager.removeViewImmediate(view) } }
        owner?.stop()
        root = null
        owner = null
        params = null
        request = null
        geometry = null
    }

    /** Called from inside the island's own composition, so the window goes a moment later. */
    private fun hideSoon() {
        root?.post { hide() }
    }

    private fun attach(geometry: IslandGeometry) {
        val lifecycle = OverlayLifecycleOwner().also { it.start() }
        val compose =
            ComposeView(context).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                setContent { IslandContentTree() }
            }
        val frame =
            FrameLayout(context).apply {
                setViewTreeLifecycleOwner(lifecycle)
                setViewTreeSavedStateRegistryOwner(lifecycle)
                setViewTreeViewModelStoreOwner(lifecycle)
                addView(compose)
            }
        val layout = newLayoutParams().also { place(it, geometry) }
        try {
            windowManager.addView(frame, layout)
            root = frame
            owner = lifecycle
            params = layout
        } catch (failure: WindowManager.BadTokenException) {
            // A service that lost its window token. The notification still arrived.
            giveUp(lifecycle, failure)
        } catch (failure: WindowManager.InvalidDisplayException) {
            giveUp(lifecycle, failure)
        }
    }

    private fun giveUp(lifecycle: OverlayLifecycleOwner, failure: RuntimeException) {
        Log.w(TAG, "Could not add the island window", failure)
        lifecycle.stop()
        request = null
        geometry = null
    }

    @Composable
    private fun IslandContentTree() {
        HeadroomTheme(darkTheme = true, dynamicColor = false, reduceMotion = reduceMotion) {
            val current = request
            val geo = geometry
            if (current != null && geo != null) {
                ResetIslandHost(request = current, geometry = geo, onFinish = ::hideSoon)
            }
        }
    }

    private fun place(layout: WindowManager.LayoutParams, geometry: IslandGeometry) {
        layout.x = geometry.window.left
        layout.y = geometry.window.top
        layout.width = geometry.window.width
        layout.height = geometry.window.height
    }

    // Accessibility overlays show over the lock screen. FLAG_SHOW_WHEN_LOCKED is deprecated for
    // activities, but it is how a window that is not an activity asks for it.
    @Suppress("DEPRECATION")
    private fun newLayoutParams() =
        WindowManager.LayoutParams(
                1,
                1,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED,
                PixelFormat.TRANSLUCENT,
            )
            .apply {
                gravity = Gravity.TOP or Gravity.START
                // The island draws around the camera cutout, so the window may reach into it.
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                setTitle(WINDOW_TITLE)
            }

    private companion object {
        const val TAG = "HeadroomIsland"
        const val WINDOW_TITLE = "Headroom reset island"
    }
}
