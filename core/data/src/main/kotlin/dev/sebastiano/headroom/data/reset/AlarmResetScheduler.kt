package dev.sebastiano.headroom.data.reset

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.content.edit

/**
 * Keeps exactly one alarm per planned reset. Exact alarms are used when the app may schedule them
 * (it declares USE_EXACT_ALARM); otherwise the alarm may arrive a little late in Doze.
 */
internal class AlarmResetScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val store = context.getSharedPreferences(STORE, Context.MODE_PRIVATE)

    fun replaceAll(alarms: List<ResetAlarm>) {
        val wanted = alarms.associateBy { it.requestCode }
        val previous =
            store.getStringSet(KEY_CODES, emptySet()).orEmpty().mapNotNull { it.toIntOrNull() }
        (previous - wanted.keys).forEach { cancel(it) }
        alarms.forEach { schedule(it) }
        store.edit { putStringSet(KEY_CODES, wanted.keys.map { it.toString() }.toSet()) }
    }

    // The manifest declares USE_EXACT_ALARM, which grants exact alarms without the user-toggled
    // SCHEDULE_EXACT_ALARM, and the call is guarded by canScheduleExactAlarms(). Lint only knows
    // about SCHEDULE_EXACT_ALARM.
    @SuppressLint("MissingPermission")
    private fun schedule(alarm: ResetAlarm) {
        val intent = pendingIntent(alarm.requestCode, ResetAlarmReceiver.intent(context, alarm))
        val at = alarm.triggerAt.toEpochMilli()
        if (alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
        }
    }

    private fun cancel(requestCode: Int) {
        alarmManager.cancel(
            pendingIntent(requestCode, Intent(context, ResetAlarmReceiver::class.java))
        )
    }

    private fun pendingIntent(requestCode: Int, intent: Intent): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private companion object {
        const val STORE = "reset_alarms"
        const val KEY_CODES = "request_codes"
    }
}
