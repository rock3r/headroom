package dev.sebastiano.headroom.island

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dev.sebastiano.headroom.designsystem.HeadroomTheme

/** The island's geometry for the camera cutout and the screen, right now. */
internal fun WindowManager.measureIsland(density: Float): IslandGeometry {
    val metrics = maximumWindowMetrics
    val bounds = metrics.bounds
    val cutout = metrics.windowInsets.displayCutout
    val rect =
        cutout
            ?.boundingRects
            ?.firstOrNull { it.top == 0 }
            ?.let { PxRect(it.left, it.top, it.right, it.bottom) }
    // The outline of the top cutout: the path covers every cutout, so keep the part in the rect.
    val outline =
        cutout?.cutoutPath?.let { path ->
            val box = RectF().also { path.computeBounds(it) }
            PxRect(box.left.toInt(), box.top.toInt(), box.right.toInt(), box.bottom.toInt())
                .takeIf { box -> rect != null && !box.isEmpty && rect.contains(box) }
        }
    val hole = cameraHole(outline, rect)
    return islandGeometry(hole, bounds.width(), bounds.height(), density)
}

/** The two kinds of window the island can be drawn in. */
internal enum class IslandWindowKind {
    /**
     * An accessibility overlay: it sits above the status bar, the shade and the lock screen, and
     * every touch goes through it to what is below. The window manager of an [IslandWindow] of this
     * kind must be the one of the accessibility service that owns the window.
     */
    Accessibility,

    /**
     * A `TYPE_APPLICATION_OVERLAY` window, for when the user allowed Display over other apps. It
     * sits around the camera cutout too, but the status bar icons draw over it where they meet, it
     * is never shown on the lock screen, and it takes the touches in its own area. It is touchable
     * on purpose: Android draws an untouchable overlay at 80% opacity, which would turn the black
     * pill grey.
     */
    Application,
}

/**
 * Owns one window of the island, of the given [kind]. The window exists only while an island plays.
 * It is only as big as the largest pill.
 *
 * An [IslandWindowKind.Application] window can be tapped, which calls [onTap] and removes the
 * window, and swiped up, which removes it. [onGone] is called each time the window is removed, or
 * could not be added.
 *
 * A window cannot stay on screen for long. A watchdog removes it a few seconds after the longest
 * island should have ended, even if the animation never finishes.
 */
internal class IslandWindow(
    private val context: Context,
    private val windowManager: WindowManager,
    private val kind: IslandWindowKind = IslandWindowKind.Accessibility,
    private val onTap: () -> Unit = {},
    private val onGone: () -> Unit = {},
) {
    private val handler = Handler(Looper.getMainLooper())
    private val watchdog = Runnable {
        Log.w(TAG, "The island window outlived its animation, so it is removed")
        hide()
    }

    private var root: FrameLayout? = null
    private var owner: OverlayLifecycleOwner? = null
    private var params: WindowManager.LayoutParams? = null

    private var request by mutableStateOf<IslandRequest?>(null)
    private var geometry by mutableStateOf<IslandGeometry?>(null)
    private var reduceMotion by mutableStateOf(false)

    /** True while the window is on screen. */
    val isShowing: Boolean
        get() = root != null

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
        // A new request restarts the hold, so the watchdog starts again too.
        handler.removeCallbacks(watchdog)
        if (root != null) handler.postDelayed(watchdog, MAX_LIFETIME_MILLIS)
    }

    /** Removes the window. Safe to call when nothing shows. */
    fun hide() {
        handler.removeCallbacks(watchdog)
        val wasShowing = root != null
        root?.let { view -> runCatching { windowManager.removeViewImmediate(view) } }
        owner?.stop()
        root = null
        owner = null
        params = null
        request = null
        geometry = null
        if (wasShowing) onGone()
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
            // A service that lost its window token, or an app that lost its overlay permission.
            giveUp(lifecycle, failure)
        } catch (failure: WindowManager.InvalidDisplayException) {
            giveUp(lifecycle, failure)
        } catch (failure: SecurityException) {
            giveUp(lifecycle, failure)
        }
    }

    private fun giveUp(lifecycle: OverlayLifecycleOwner, failure: RuntimeException) {
        Log.w(TAG, "Could not add the island window", failure)
        lifecycle.stop()
        request = null
        geometry = null
        onGone()
    }

    @Composable
    private fun IslandContentTree() {
        HeadroomTheme(darkTheme = true, dynamicColor = false, reduceMotion = reduceMotion) {
            val current = request
            val geo = geometry
            if (current != null && geo != null) {
                ResetIslandHost(
                    request = current,
                    geometry = geo,
                    onFinish = ::hideSoon,
                    modifier =
                        if (kind == IslandWindowKind.Application) {
                            Modifier.islandTouch(
                                onTap = {
                                    onTap()
                                    hideSoon()
                                },
                                onSwipeUp = ::hideSoon,
                            )
                        } else {
                            Modifier
                        },
                )
            }
        }
    }

    private fun place(layout: WindowManager.LayoutParams, geometry: IslandGeometry) {
        layout.x = geometry.window.left
        layout.y = geometry.window.top
        layout.width = geometry.window.width
        layout.height = geometry.window.height
    }

    private fun newLayoutParams(): WindowManager.LayoutParams =
        when (kind) {
            IslandWindowKind.Accessibility -> accessibilityLayoutParams()
            IslandWindowKind.Application -> applicationLayoutParams()
        }

    // Accessibility overlays show over the lock screen. FLAG_SHOW_WHEN_LOCKED is deprecated for
    // activities, but it is how a window that is not an activity asks for it.
    @Suppress("DEPRECATION")
    private fun accessibilityLayoutParams() =
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

    // No FLAG_NOT_TOUCHABLE: Android forces an untouchable overlay to 80% alpha, and then black
    // shows as dark grey. The window is only as big as the pill, so it takes no other touches.
    private fun applicationLayoutParams() =
        WindowManager.LayoutParams(
                1,
                1,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT,
            )
            .apply {
                gravity = Gravity.TOP or Gravity.START
                // Around the camera cutout, like the accessibility island.
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                setTitle(WINDOW_TITLE)
            }

    private companion object {
        const val TAG = "HeadroomIsland"
        const val WINDOW_TITLE = "Headroom reset island"

        /**
         * The longest an island can take is the grow, the hold, the fade and the shrink. The
         * watchdog waits three seconds more.
         */
        const val MAX_LIFETIME_MILLIS =
            IslandTiming.GROW +
                IslandTiming.HOLD +
                IslandTiming.FADE_OUT +
                IslandTiming.SHRINK +
                3_000L
    }
}
