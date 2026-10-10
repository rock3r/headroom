package dev.sebastiano.headroom.data.reset

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import dev.sebastiano.headroom.data.R
import dev.sebastiano.headroom.data.ResetReminderIntents
import dev.sebastiano.headroom.model.AccountState
import dev.sebastiano.headroom.model.ExpiringReset
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.time.toJavaInstant

/**
 * Posts the day's reminder that resets expire soon, on its own channel at default importance. One
 * reset gets a sentence of its own; several share one notification, soonest first. Tapping it opens
 * the detail of the soonest reset's account, with its Resets card in view, through [openResets].
 * Each day's reminder replaces the one before.
 */
internal class AndroidResetReminderNotifier(
    private val context: Context,
    /** Read when a reminder is posted, so a reminder after a trip shows the local time. */
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    private val locale: () -> Locale = Locale::getDefault,
    /** Whether the user's clock shows 24 hours, read when a reminder is posted. */
    private val is24Hour: () -> Boolean = { DateFormat.is24HourFormat(context) },
    private val clock: () -> Instant = Clock.System::now,
    private val openResets: (accountId: String) -> Intent? = {
        ResetReminderIntents.open(context, it)
    },
) : ResetReminderNotifier {
    private val manager = context.getSystemService(NotificationManager::class.java)

    override fun notifyExpiring(resets: List<ExpiringReset>, accounts: List<AccountState>) {
        val sorted = resets.sortedBy { it.expiresAt }
        val soonest = sorted.firstOrNull() ?: return
        ensureChannel()
        val title: String
        val body: String
        if (sorted.size == 1) {
            title =
                context.getString(
                    R.string.reset_reminder_title_one,
                    name(soonest, accounts),
                    whenText(soonest.expiresAt),
                )
            body = context.getString(R.string.reset_reminder_body_one)
        } else {
            title =
                context.resources.getQuantityString(
                    R.plurals.reset_reminder_title_many,
                    sorted.size,
                    sorted.size,
                )
            body =
                sorted.joinToString(SEPARATOR) {
                    context.getString(
                        R.string.reset_reminder_line,
                        name(it, accounts),
                        whenText(it.expiresAt),
                    )
                }
        }
        val notification =
            Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_reset)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(Notification.BigTextStyle().bigText(body))
                .setCategory(Notification.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent(soonest.account.id))
                .build()
        manager.notify(TAG, NOTIFICATION_ID, notification)
    }

    /** The provider, and the account's name when the user has several accounts of it. */
    private fun name(reset: ExpiringReset, accounts: List<AccountState>): String {
        val account = reset.account
        val provider = account.provider.displayName
        val several = accounts.count { it.account.provider == account.provider } > 1
        return if (several) {
            context.getString(
                R.string.reset_reminder_account_named,
                provider,
                account.nickname ?: account.label,
            )
        } else {
            provider
        }
    }

    /** "today at 06:18", "tomorrow at 06:18", or "on Mon 12 Oct at 06:18". */
    private fun whenText(at: Instant): String {
        val zone = zone()
        val locale = locale()
        val time = at.toJavaInstant().atZone(zone)
        val clockTime = DateTimeFormatter.ofPattern(timePattern(), locale).format(time)
        val today = clock().toJavaInstant().atZone(zone).toLocalDate()
        return when (time.toLocalDate()) {
            today -> context.getString(R.string.reset_reminder_today, clockTime)
            today.plusDays(1) -> context.getString(R.string.reset_reminder_tomorrow, clockTime)
            else ->
                context.getString(
                    R.string.reset_reminder_on_date,
                    DateTimeFormatter.ofPattern("EEE d MMM", locale).format(time),
                    clockTime,
                )
        }
    }

    private fun timePattern(): String = if (is24Hour()) "HH:mm" else "h:mm a"

    private fun pendingIntent(accountId: String): PendingIntent? {
        val intent = openResets(accountId) ?: return null
        return PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun ensureChannel() {
        manager.createNotificationChannel(
            NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.reset_reminder_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT,
                )
                .apply {
                    description = context.getString(R.string.reset_reminder_channel_description)
                }
        )
    }

    companion object {
        const val CHANNEL_ID: String = "reset_reminders"
        private const val TAG = "reset_reminder"
        private const val NOTIFICATION_ID = 1
        private const val SEPARATOR = " · "
    }
}
