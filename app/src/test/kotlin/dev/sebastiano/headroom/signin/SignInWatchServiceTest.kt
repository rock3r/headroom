package dev.sebastiano.headroom.signin

import android.app.Application
import android.app.Notification
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import dev.sebastiano.headroom.R
import dev.sebastiano.headroom.TestHeadroomApplication
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The foreground service that keeps the app awake while a sign-in waits in the browser. */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestHeadroomApplication::class)
class SignInWatchServiceTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()

    private fun service(service: String = "Cursor"): SignInWatchService =
        Robolectric.buildService(
                SignInWatchService::class.java,
                Intent(context, SignInWatchService::class.java).putExtra("service", service),
            )
            .create()
            .startCommand(0, 0)
            .get()

    @Test
    fun `watching starts the foreground service, which says what it waits for`() {
        SignInWatchService.start(context, "Cursor")

        val started = shadowOf(context).nextStartedService
        assertEquals(SignInWatchService::class.java.name, started?.component?.className)
        assertEquals("Cursor", started?.getStringExtra("service"))
    }

    @Test
    fun `the service shows the sign-in while it waits`() {
        val notification = checkNotNull(shadowOf(service()).lastForegroundNotification)

        assertEquals(
            context.getString(R.string.sign_in_watch_title, "Cursor"),
            notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString(),
        )
        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
    }

    @Test
    fun `letting go stops the service`() {
        SignInWatchService.stop(context)

        assertEquals(
            SignInWatchService::class.java.name,
            shadowOf(context).nextStoppedService?.component?.className,
        )
    }
}
