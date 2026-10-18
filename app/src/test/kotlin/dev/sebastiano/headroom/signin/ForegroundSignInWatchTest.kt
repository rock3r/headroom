package dev.sebastiano.headroom.signin

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import dev.sebastiano.headroom.TestHeadroomApplication
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The foreground service stays up while any of the app's sign-ins waits, and comes down when the
 * last one ends.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestHeadroomApplication::class)
class ForegroundSignInWatchTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val watch = ForegroundSignInWatch(context)
    private val started = shadowOf(context)

    @Test
    fun `watching starts the service once, and the last watch stops it`() {
        val cursor = watch.start("Cursor")
        val zCode = watch.start("ZCode")

        assertEquals("Cursor", started.nextStartedService?.getStringExtra("service"))
        assertNull(started.nextStartedService)

        // ZCode's sign-in ending first does not take the service away from Cursor's.
        zCode.stop()
        assertNull(started.nextStoppedService)

        cursor.stop()
        assertEquals(
            SignInWatchService::class.java.name,
            started.nextStoppedService?.component?.className,
        )
    }

    @Test
    fun `letting go twice counts once`() {
        val only = watch.start("Cursor")

        only.stop()
        only.stop()

        assertEquals(
            SignInWatchService::class.java.name,
            started.nextStoppedService?.component?.className,
        )
        assertNull(started.nextStoppedService)
    }

    @Test
    fun `a watch taken over keeps the service up across the change`() {
        val first = watch.start("Cursor")
        val second = watch.start("Cursor")

        // The replaced flow may let go after the new one started: the service must stay.
        first.stop()
        assertNull(started.nextStoppedService)

        second.stop()
        assertEquals(
            SignInWatchService::class.java.name,
            started.nextStoppedService?.component?.className,
        )
    }
}
