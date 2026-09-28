package dev.sebastiano.headroom.data.reset

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.sebastiano.headroom.model.DemoData
import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class AndroidResetNotifierTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val now = Instant.parse("2026-09-27T12:32:00Z")
    private val grok = DemoData.accounts(now).first { it.account.id == "demo-grok" }

    @Test
    fun `posts a reset notification on the weekly resets channel`() {
        val notifier = AndroidResetNotifier(context, ZoneId.of("UTC"))
        notifier.notifyReset(grok, grok.primaryWindow!!)

        val posted = shadowOf(manager).allNotifications.single()
        assertEquals(AndroidResetNotifier.CHANNEL_ID, posted.channelId)
        assertEquals("Grok weekly limit has reset", shadowOf(posted).contentTitle)
        assertEquals(2, posted.actions.size)
        assertEquals(
            "Weekly resets",
            manager.getNotificationChannel(AndroidResetNotifier.CHANNEL_ID).name,
        )
    }

    @Test
    fun `a named account is called by its name`() {
        val named = grok.copy(account = grok.account.copy(nickname = "Side project"))

        AndroidResetNotifier(context, ZoneId.of("UTC")).notifyReset(named, named.primaryWindow!!)

        val posted = shadowOf(manager).allNotifications.single()
        assertEquals("Side project weekly limit has reset", shadowOf(posted).contentTitle)
    }

    @Test
    fun `the same window replaces its previous notification`() {
        val notifier = AndroidResetNotifier(context, ZoneId.of("UTC"))
        notifier.notifyReset(grok, grok.primaryWindow!!)
        notifier.notifyReset(grok, grok.primaryWindow!!)
        assertEquals(1, shadowOf(manager).allNotifications.size)
    }
}
