package dev.sebastiano.headroom.data.reset

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.sebastiano.headroom.data.DataGraphOwner
import java.time.Instant

/** Fires at a planned reset time and hands the check to [ResetCheckWorker]. */
internal class ResetAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val alarm = alarmFrom(intent) ?: return
        (context.applicationContext as? DataGraphOwner)?.dataGraph?.markAlarmFired(alarm)
        ResetCheckWorker.enqueue(context, alarm, attempt = 1, delay = null)
    }

    companion object {
        private const val EXTRA_ACCOUNT = "account"
        private const val EXTRA_WINDOW = "window"
        private const val EXTRA_TRIGGER = "trigger"
        private const val EXTRA_EXPECTED = "expected"
        private const val EXTRA_USED = "used"

        fun intent(context: Context, alarm: ResetAlarm): Intent =
            Intent(context, ResetAlarmReceiver::class.java)
                .putExtra(EXTRA_ACCOUNT, alarm.accountId)
                .putExtra(EXTRA_WINDOW, alarm.windowId)
                .putExtra(EXTRA_TRIGGER, alarm.triggerAt.toEpochMilli())
                .putExtra(EXTRA_EXPECTED, alarm.expectedResetAt.toEpochMilli())
                .putExtra(EXTRA_USED, alarm.usedBefore)

        fun alarmFrom(intent: Intent): ResetAlarm? {
            val account = intent.getStringExtra(EXTRA_ACCOUNT) ?: return null
            val window = intent.getStringExtra(EXTRA_WINDOW) ?: return null
            return ResetAlarm(
                accountId = account,
                windowId = window,
                triggerAt = Instant.ofEpochMilli(intent.getLongExtra(EXTRA_TRIGGER, 0)),
                expectedResetAt = Instant.ofEpochMilli(intent.getLongExtra(EXTRA_EXPECTED, 0)),
                usedBefore = intent.getDoubleExtra(EXTRA_USED, 0.0),
            )
        }
    }
}

/** The "Mute" notification action: turns that window's alert off. */
internal class MuteAlertReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val account = intent.getStringExtra(EXTRA_ACCOUNT) ?: return
        val window = intent.getStringExtra(EXTRA_WINDOW) ?: return
        context
            .getSystemService(NotificationManager::class.java)
            .cancel(intent.getIntExtra(EXTRA_NOTIFICATION, 0))
        val graph = (context.applicationContext as? DataGraphOwner)?.dataGraph ?: return
        val pending = goAsync()
        graph.launchInBackground {
            try {
                graph.alertPreferences.setEnabled(account, window, false)
                graph.rescheduleResetAlarms()
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val EXTRA_ACCOUNT = "account"
        private const val EXTRA_WINDOW = "window"
        private const val EXTRA_NOTIFICATION = "notification"

        fun pendingIntent(
            context: Context,
            accountId: String,
            windowId: String,
            notificationId: Int,
        ): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                notificationId,
                Intent(context, MuteAlertReceiver::class.java)
                    .putExtra(EXTRA_ACCOUNT, accountId)
                    .putExtra(EXTRA_WINDOW, windowId)
                    .putExtra(EXTRA_NOTIFICATION, notificationId),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
    }
}

/**
 * Alarms do not survive a reboot or an app update; plan them again. A time zone change plans them
 * again too, because a reset reminder moved to the next morning is due at a local time.
 */
internal class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in ACTIONS) return
        val graph = (context.applicationContext as? DataGraphOwner)?.dataGraph ?: return
        val pending = goAsync()
        graph.launchInBackground {
            try {
                graph.rescheduleResetAlarms()
                graph.rescheduleResetReminders()
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val ACTIONS =
            setOf(
                Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_MY_PACKAGE_REPLACED,
                Intent.ACTION_TIMEZONE_CHANGED,
            )
    }
}
