package dev.sebastiano.headroom.data.reset

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import dev.sebastiano.headroom.data.ResetReminderIntents
import dev.sebastiano.headroom.model.Account
import dev.sebastiano.headroom.model.AccountState
import dev.sebastiano.headroom.model.ExpiringReset
import dev.sebastiano.headroom.model.Provider
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class AndroidResetReminderNotifierTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val now = Instant.parse("2026-10-10T08:00:00Z")
    private val codex = Account("codex", Provider.Codex, "sam@example.com")
    private val grok = Account("grok", Provider.Grok, "sam")

    private var zone = ZoneId.of("UTC")

    private fun notifier(is24Hour: Boolean = true) =
        AndroidResetReminderNotifier(
            context,
            zone = { zone },
            locale = { Locale.US },
            is24Hour = { is24Hour },
            clock = { now },
        ) { accountId ->
            Intent("test.OPEN").putExtra(ResetReminderIntents.EXTRA_ACCOUNT_ID, accountId)
        }

    private fun states(vararg accounts: Account) = accounts.map { AccountState(it, null) }

    private fun posted(): Notification = shadowOf(manager).allNotifications.single()

    @Test
    fun `posts on its own reminders channel at default importance`() {
        notifier()
            .notifyExpiring(
                listOf(ExpiringReset(codex, "credits", Instant.parse("2026-10-11T06:18:00Z"))),
                states(codex),
            )

        assertEquals(AndroidResetReminderNotifier.CHANNEL_ID, posted().channelId)
        val channel = manager.getNotificationChannel(AndroidResetReminderNotifier.CHANNEL_ID)
        assertEquals("Reset reminders", channel.name)
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, channel.importance)
    }

    @Test
    fun `one reset says which and when it expires`() {
        notifier()
            .notifyExpiring(
                listOf(ExpiringReset(codex, "credits", Instant.parse("2026-10-11T06:18:00Z"))),
                states(codex),
            )

        assertEquals(
            "Your ChatGPT Codex reset expires tomorrow at 06:18",
            shadowOf(posted()).contentTitle,
        )
        assertEquals("Use it before then, or it's lost.", shadowOf(posted()).contentText)
    }

    @Test
    fun `follows the device's 12-hour clock, and says today for a reset that expires today`() {
        notifier(is24Hour = false)
            .notifyExpiring(
                listOf(ExpiringReset(codex, "credits", Instant.parse("2026-10-10T21:05:00Z"))),
                states(codex),
            )

        assertEquals(
            "Your ChatGPT Codex reset expires today at 9:05 PM",
            shadowOf(posted()).contentTitle,
        )
    }

    @Test
    fun `reads the time zone when it posts, so a trip shows the local time`() {
        val notifier = notifier()
        zone = ZoneId.of("Europe/Rome")

        notifier.notifyExpiring(
            listOf(ExpiringReset(codex, "credits", Instant.parse("2026-10-11T06:18:00Z"))),
            states(codex),
        )

        assertEquals(
            "Your ChatGPT Codex reset expires tomorrow at 08:18",
            shadowOf(posted()).contentTitle,
        )
    }

    @Test
    fun `several resets share one notification, soonest first`() {
        notifier()
            .notifyExpiring(
                listOf(
                    ExpiringReset(grok, "tokens", Instant.parse("2026-10-10T20:00:00Z")),
                    ExpiringReset(codex, "credits", Instant.parse("2026-10-11T06:18:00Z")),
                ),
                states(codex, grok),
            )

        assertEquals("2 resets expire soon", shadowOf(posted()).contentTitle)
        assertEquals(
            "Grok: today at 20:00 · ChatGPT Codex: tomorrow at 06:18",
            shadowOf(posted()).contentText,
        )
    }

    @Test
    fun `names the account when the provider has several`() {
        val work = Account("codex-2", Provider.Codex, "work@example.com", nickname = "Work")
        notifier()
            .notifyExpiring(
                listOf(ExpiringReset(work, "credits", Instant.parse("2026-10-11T06:18:00Z"))),
                states(codex, work),
            )

        assertEquals(
            "Your ChatGPT Codex · Work reset expires tomorrow at 06:18",
            shadowOf(posted()).contentTitle,
        )
    }

    @Test
    fun `tapping opens the resets of the soonest reset's account, and removes the reminder`() {
        notifier()
            .notifyExpiring(
                listOf(
                    ExpiringReset(grok, "tokens", Instant.parse("2026-10-10T20:00:00Z")),
                    ExpiringReset(codex, "credits", Instant.parse("2026-10-11T06:18:00Z")),
                ),
                states(codex, grok),
            )

        val pending = posted().contentIntent
        assertTrue(pending.isImmutable)
        assertEquals(
            "grok",
            shadowOf(pending).savedIntent.getStringExtra(ResetReminderIntents.EXTRA_ACCOUNT_ID),
        )
        assertTrue(posted().flags and Notification.FLAG_AUTO_CANCEL != 0)
    }
}
