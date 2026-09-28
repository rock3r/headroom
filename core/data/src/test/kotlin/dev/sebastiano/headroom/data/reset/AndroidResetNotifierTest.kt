package dev.sebastiano.headroom.data.reset

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.sebastiano.headroom.model.DemoData
import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
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
    fun `a reset pops up as a heads-up notification`() {
        AndroidResetNotifier(context, ZoneId.of("UTC")).notifyReset(grok, grok.primaryWindow!!)

        val channel = manager.getNotificationChannel(AndroidResetNotifier.CHANNEL_ID)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, channel.importance)
    }

    @Test
    fun `the old quiet channel is removed, because a channel's importance cannot be raised`() {
        manager.createNotificationChannel(
            NotificationChannel(
                "weekly_resets",
                "Weekly resets",
                NotificationManager.IMPORTANCE_DEFAULT,
            )
        )

        AndroidResetNotifier(context, ZoneId.of("UTC")).notifyReset(grok, grok.primaryWindow!!)

        assertNull(manager.getNotificationChannel("weekly_resets"))
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
