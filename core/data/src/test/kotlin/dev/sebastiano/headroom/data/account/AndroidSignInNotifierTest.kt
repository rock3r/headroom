package dev.sebastiano.headroom.data.account

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import dev.sebastiano.headroom.data.SignInIntents
import dev.sebastiano.headroom.model.DemoData
import dev.sebastiano.headroom.model.QuotaErrorKind
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
class AndroidSignInNotifierTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val syncedAt = Instant.parse("2026-09-27T10:32:00Z")
    private val claude =
        DemoData.accounts(syncedAt)
            .first { it.account.id == "demo-claude" }
            .copy(lastError = QuotaErrorKind.Auth)

    private fun notifier() =
        AndroidSignInNotifier(context, ZoneId.of("UTC"), Locale.US) { accountId ->
            Intent("test.OPEN").putExtra(SignInIntents.EXTRA_ACCOUNT_ID, accountId)
        }

    private fun posted(): Notification = shadowOf(manager).allNotifications.single()

    @Test
    fun `posts on its own sign-in channel at default importance`() {
        notifier().notifyExpired(claude, showAccountName = false)

        assertEquals(AndroidSignInNotifier.CHANNEL_ID, posted().channelId)
        val channel = manager.getNotificationChannel(AndroidSignInNotifier.CHANNEL_ID)
        assertEquals("Sign-in problems", channel.name)
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, channel.importance)
    }

    @Test
    fun `says which provider to sign in to, and how old the numbers are`() {
        notifier().notifyExpired(claude, showAccountName = false)

        assertEquals("Sign in to Claude again", shadowOf(posted()).contentTitle)
        assertEquals(
            "Headroom can't update this account until you sign in. " +
                "The numbers you see are from Sun 27 Sep, 10:32.",
            shadowOf(posted()).contentText,
        )
    }

    @Test
    fun `names the account when the provider has several`() {
        notifier()
            .notifyExpired(
                claude.copy(account = claude.account.copy(nickname = "Work")),
                showAccountName = true,
            )

        assertEquals("Sign in to Claude again · Work", shadowOf(posted()).contentTitle)
    }

    @Test
    fun `the sign in action and the tap open the sign-in for that account, immutably`() {
        notifier().notifyExpired(claude, showAccountName = false)

        val action = posted().actions.single()
        assertEquals("Sign in", action.title)
        listOf(action.actionIntent, posted().contentIntent).forEach { pending ->
            assertTrue(pending.isImmutable)
            assertEquals(
                "demo-claude",
                shadowOf(pending).savedIntent.getStringExtra(SignInIntents.EXTRA_ACCOUNT_ID),
            )
        }
    }

    @Test
    fun `cancel removes the account's warning`() {
        val notifier = notifier()
        notifier.notifyExpired(claude, showAccountName = false)
        notifier.cancel("demo-claude")

        assertEquals(0, shadowOf(manager).allNotifications.size)
    }

    @Test
    fun `an account with no data yet only asks to sign in`() {
        notifier().notifyExpired(claude.copy(snapshot = null), showAccountName = false)

        assertEquals(
            "Headroom can't update this account until you sign in.",
            shadowOf(posted()).contentText,
        )
    }
}
