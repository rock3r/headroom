package dev.sebastiano.headroom.data.reset

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.sebastiano.headroom.model.DemoData
import dev.sebastiano.headroom.model.Provider
import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
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
    fun `posts a reset notification on the weekly resets channel`() = runTest {
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
    fun `a reset pops up as a heads-up notification`() = runTest {
        AndroidResetNotifier(context, ZoneId.of("UTC")).notifyReset(grok, grok.primaryWindow!!)

        val channel = manager.getNotificationChannel(AndroidResetNotifier.CHANNEL_ID)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, channel.importance)
    }

    @Test
    fun `the old quiet channel is removed, because a channel's importance cannot be raised`() =
        runTest {
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
    fun `a named account is called by its name`() = runTest {
        val named = grok.copy(account = grok.account.copy(nickname = "Side project"))

        AndroidResetNotifier(context, ZoneId.of("UTC")).notifyReset(named, named.primaryWindow!!)

        val posted = shadowOf(manager).allNotifications.single()
        assertEquals("Side project weekly limit has reset", shadowOf(posted).contentTitle)
    }

    @Test
    fun `the same window replaces its previous notification`() = runTest {
        val notifier = AndroidResetNotifier(context, ZoneId.of("UTC"))
        notifier.notifyReset(grok, grok.primaryWindow!!)
        notifier.notifyReset(grok, grok.primaryWindow!!)
        assertEquals(1, shadowOf(manager).allNotifications.size)
    }

    private class RecordingIsland(private val shows: Boolean) : ResetIsland {
        val shown = mutableListOf<Pair<Provider, String>>()

        override suspend fun show(provider: Provider, message: String): Boolean {
            shown += provider to message
            return shows
        }
    }

    @Test
    fun `the island is offered the provider and a short line`() = runTest {
        val island = RecordingIsland(shows = true)
        AndroidResetNotifier(context, ZoneId.of("UTC"), island)
            .notifyReset(grok, grok.primaryWindow!!)

        assertEquals(listOf(Provider.Grok to "Grok weekly limit reset"), island.shown)
    }

    @Test
    fun `a named account is called by its name on the island too`() = runTest {
        val named = grok.copy(account = grok.account.copy(nickname = "Side project"))
        val island = RecordingIsland(shows = true)
        AndroidResetNotifier(context, ZoneId.of("UTC"), island)
            .notifyReset(named, named.primaryWindow!!)

        assertEquals("Side project weekly limit reset", island.shown.single().second)
    }

    @Test
    fun `when the island shows, the notification is posted on the quiet channel`() = runTest {
        AndroidResetNotifier(context, ZoneId.of("UTC"), RecordingIsland(shows = true))
            .notifyReset(grok, grok.primaryWindow!!)

        val posted = shadowOf(manager).allNotifications.single()
        assertEquals(AndroidResetNotifier.QUIET_CHANNEL_ID, posted.channelId)
        assertEquals("Grok weekly limit has reset", shadowOf(posted).contentTitle)
        assertEquals(2, posted.actions.size)
        assertEquals(
            NotificationManager.IMPORTANCE_LOW,
            manager.getNotificationChannel(AndroidResetNotifier.QUIET_CHANNEL_ID).importance,
        )
    }

    @Test
    fun `when the island does not show, the notification pops up as before`() = runTest {
        AndroidResetNotifier(context, ZoneId.of("UTC"), RecordingIsland(shows = false))
            .notifyReset(grok, grok.primaryWindow!!)

        val posted = shadowOf(manager).allNotifications.single()
        assertEquals(AndroidResetNotifier.CHANNEL_ID, posted.channelId)
    }

    @Test
    fun `a reset that replaces a quiet notification takes the heads-up channel back`() = runTest {
        val id = AndroidResetNotifier.notificationId(grok.account.id, grok.primaryWindow!!.id)
        AndroidResetNotifier(context, ZoneId.of("UTC"), RecordingIsland(shows = true))
            .notifyReset(grok, grok.primaryWindow!!)
        AndroidResetNotifier(context, ZoneId.of("UTC"), RecordingIsland(shows = false))
            .notifyReset(grok, grok.primaryWindow!!)

        val posted = shadowOf(manager).getNotification(id)
        assertEquals(AndroidResetNotifier.CHANNEL_ID, posted.channelId)
    }
}
