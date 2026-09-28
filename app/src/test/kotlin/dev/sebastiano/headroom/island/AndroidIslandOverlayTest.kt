package dev.sebastiano.headroom.island

import android.content.Context
import android.os.Looper
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import dev.sebastiano.headroom.model.Provider
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowSettings

@RunWith(RobolectricTestRunner::class)
class AndroidIslandOverlayTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private var opened = 0
    private val overlay =
        AndroidIslandOverlay(context, reduceMotion = { false }, openApp = { opened++ })
    private val request = IslandRequest(Provider.Claude, "Claude weekly limit reset", serial = 1)

    @Test
    fun `display over other apps is not allowed until the user allows it`() {
        assertEquals(false, overlay.isAllowed())
        ShadowSettings.setCanDrawOverlays(true)
        assertEquals(true, overlay.isAllowed())
        ShadowSettings.setCanDrawOverlays(false)
        assertEquals(false, overlay.isAllowed())
    }

    @Test
    fun `it reads the same system setting as the permission page`() {
        // The permission is app-ops based: Settings.canDrawOverlays is the supported way to read
        // it.
        ShadowSettings.setCanDrawOverlays(true)
        assertEquals(true, Settings.canDrawOverlays(context))
        assertEquals(true, overlay.isAllowed())
    }

    @Test
    fun `without the permission nothing is shown, and nothing is left to wait for`() = runTest {
        ShadowSettings.setCanDrawOverlays(false)
        assertEquals(false, overlay.show(request))
        overlay.awaitIdle()
        assertEquals(0, opened)
    }

    @Test
    fun `with the permission the window is added, and the watchdog removes it in the end`() =
        runTest {
            ShadowSettings.setCanDrawOverlays(true)
            assertEquals(true, overlay.show(request))
            assertEquals(false, overlay.isIdle)

            // The watchdog is a main looper task: running the looper past it removes the window,
            // even though the animation of a paused test never finishes on its own.
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(20))

            assertEquals(true, overlay.isIdle)
            overlay.awaitIdle()
        }

    @Test
    fun `a second reset while the window is up keeps one window and restarts the watchdog`() =
        runTest {
            ShadowSettings.setCanDrawOverlays(true)
            assertEquals(true, overlay.show(request))
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(6))
            assertEquals(true, overlay.show(request.copy(serial = 2)))

            // Ten seconds after the first request, but only four after the second.
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(4))
            assertEquals(false, overlay.isIdle)

            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(10))
            assertEquals(true, overlay.isIdle)
        }
}
