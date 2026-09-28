package dev.sebastiano.headroom.data.reset

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import dev.sebastiano.headroom.data.R
import dev.sebastiano.headroom.model.AccountState
import dev.sebastiano.headroom.model.QuotaWindow
import dev.sebastiano.headroom.model.WindowKind
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal fun interface ResetNotifier {
    fun notifyReset(account: AccountState, window: QuotaWindow)
}

/** Posts "your weekly limit has reset" notifications on their own channel. */
internal class AndroidResetNotifier(
    private val context: Context,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : ResetNotifier {
    private val manager = context.getSystemService(NotificationManager::class.java)

    override fun notifyReset(account: AccountState, window: QuotaWindow) {
        ensureChannel()
        val name = account.account.name
        val titleRes =
            if (window.kind == WindowKind.Monthly) R.string.reset_title_monthly
            else R.string.reset_title_weekly
        val body =
            window.resetsAt?.let { next ->
                context.getString(
                    R.string.reset_body_with_next,
                    nextResetFormat().format(next.atZone(zone)),
                )
            } ?: context.getString(R.string.reset_body)
        val id = notificationId(account.account.id, window.id)

        val notification =
            Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_reset)
                .setContentTitle(context.getString(titleRes, name))
                .setContentText(body)
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_REMINDER)
                .setContentIntent(openAppIntent())
                .addAction(
                    Notification.Action.Builder(
                            null,
                            context.getString(R.string.reset_action_open),
                            openAppIntent(),
                        )
                        .build()
                )
                .addAction(
                    Notification.Action.Builder(
                            null,
                            context.getString(R.string.reset_action_mute, name),
                            MuteAlertReceiver.pendingIntent(
                                context,
                                account.account.id,
                                window.id,
                                id,
                            ),
                        )
                        .build()
                )
                .build()
        manager.notify(id, notification)
    }

    /**
     * A reset pops up as a heads-up notification. Android never raises the importance of a channel
     * that exists, so the high-importance channel has a new id and the old quiet one is removed.
     */
    private fun ensureChannel() {
        manager.deleteNotificationChannel(OLD_CHANNEL_ID)
        val channel =
            NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.reset_channel_name),
                    NotificationManager.IMPORTANCE_HIGH,
                )
                .apply { description = context.getString(R.string.reset_channel_description) }
        manager.createNotificationChannel(channel)
    }

    private fun nextResetFormat(): DateTimeFormatter =
        DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", Locale.getDefault())

    private fun openAppIntent(): PendingIntent? {
        val launch =
            context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
        launch.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        return PendingIntent.getActivity(context, 0, launch, PendingIntent.FLAG_IMMUTABLE)
    }

    companion object {
        const val CHANNEL_ID: String = "reset_alerts"

        /** The quiet channel resets used before they popped up. */
        private const val OLD_CHANNEL_ID = "weekly_resets"

        fun notificationId(accountId: String, windowId: String): Int =
            "$accountId/$windowId".hashCode()
    }
}
