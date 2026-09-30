package dev.sebastiano.headroom

import android.app.NotificationManager
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The debug-only way to preview an expired sign-in on a device, with the demo accounts. */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestHeadroomApplication::class)
class DemoSignInExpiredReceiverTest {
    private val app = ApplicationProvider.getApplicationContext<TestHeadroomApplication>()
    private val notifications = app.getSystemService(NotificationManager::class.java)

    private fun claude() = app.graph.quotaRepository.accounts.value.first()

    @Test
    fun `marks the demo Claude account expired and posts the warning`() {
        DemoSignInExpiredReceiver().onReceive(app, Intent())

        assertTrue(claude().isSignInExpired)
        val posted = shadowOf(notifications).allNotifications.single()
        assertEquals("Sign in to Claude again", shadowOf(posted).contentTitle)
    }

    @Test
    fun `clear puts the demo back and removes the warning`() {
        DemoSignInExpiredReceiver().onReceive(app, Intent())
        DemoSignInExpiredReceiver().onReceive(app, Intent().putExtra("clear", true))

        assertFalse(claude().isSignInExpired)
        assertEquals(0, shadowOf(notifications).allNotifications.size)
    }
}
