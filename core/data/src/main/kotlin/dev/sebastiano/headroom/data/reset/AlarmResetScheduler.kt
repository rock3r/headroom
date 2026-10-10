package dev.sebastiano.headroom.data.reset

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.content.edit
import kotlin.time.Instant

/**
 * Keeps exactly one alarm per planned reset. Exact alarms are used when the app may schedule them
 * (it declares USE_EXACT_ALARM); otherwise the alarm may arrive a little late in Doze.
 *
 * A replan can happen after a reset time but before its alarm fired, for example when a sync
 * already sees next week's reset time. Such an overdue alarm is not dropped: it goes to [handOver],
 * which checks it straight away.
 */
internal class AlarmResetScheduler(
    private val context: Context,
    private val handOver: (ResetAlarm) -> Unit,
) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val store = context.getSharedPreferences(STORE, Context.MODE_PRIVATE)

    fun replaceAll(now: Instant, alarms: List<ResetAlarm>) {
        val previous = pendingAlarms().associateBy { it.requestCode }
        val merged = alarms.map { wanted -> merge(previous[wanted.requestCode], wanted, now) }
        val wantedCodes = merged.associateBy { it.requestCode }
        previous.values.forEach { old ->
            val replacement = wantedCodes[old.requestCode]
            if (replacement?.expectedResetAt == old.expectedResetAt) return@forEach
            if (old.expectedResetAt <= now) handOver(old)
            if (replacement == null) cancel(old.requestCode)
        }
        merged.forEach { schedule(it) }
        store.edit {
            clear()
            merged.forEach { putString(it.requestCode.toString(), encode(it)) }
        }
    }

    /**
     * The same reset occurrence planned again keeps the usage that proves the reset later. Before
     * the reset time, usage only grows, so the latest value is the best baseline. After it, a sync
     * may already see the reset usage, so the baseline is frozen.
     */
    private fun merge(old: ResetAlarm?, wanted: ResetAlarm, now: Instant): ResetAlarm =
        when {
            old == null || old.expectedResetAt != wanted.expectedResetAt -> wanted
            old.expectedResetAt <= now -> old
            else -> wanted.copy(usedBefore = maxOf(old.usedBefore, wanted.usedBefore))
        }

    fun pendingAlarms(): List<ResetAlarm> =
        store.all.values.mapNotNull { (it as? String)?.let(::decode) }

    /** Called when an alarm fires, so a later replan does not hand it over a second time. */
    fun markFired(alarm: ResetAlarm) {
        store.edit { remove(alarm.requestCode.toString()) }
    }

    // The manifest declares USE_EXACT_ALARM, which grants exact alarms without the user-toggled
    // SCHEDULE_EXACT_ALARM, and the call is guarded by canScheduleExactAlarms(). Lint only knows
    // about SCHEDULE_EXACT_ALARM.
    @SuppressLint("MissingPermission")
    private fun schedule(alarm: ResetAlarm) {
        val intent = pendingIntent(alarm.requestCode, ResetAlarmReceiver.intent(context, alarm))
        val at = alarm.triggerAt.toEpochMilliseconds()
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
        const val SEPARATOR = "\u001f"
        const val FIELDS = 5

        fun encode(alarm: ResetAlarm): String =
            listOf(
                    alarm.accountId,
                    alarm.windowId,
                    alarm.triggerAt.toEpochMilliseconds().toString(),
                    alarm.expectedResetAt.toEpochMilliseconds().toString(),
                    alarm.usedBefore.toString(),
                )
                .joinToString(SEPARATOR)

        fun decode(raw: String): ResetAlarm? {
            val parts = raw.split(SEPARATOR)
            if (parts.size != FIELDS) return null
            val fields = parts.iterator()
            return ResetAlarm(
                accountId = fields.next(),
                windowId = fields.next(),
                triggerAt =
                    Instant.fromEpochMilliseconds(fields.next().toLongOrNull() ?: return null),
                expectedResetAt =
                    Instant.fromEpochMilliseconds(fields.next().toLongOrNull() ?: return null),
                usedBefore = fields.next().toDoubleOrNull() ?: return null,
            )
        }
    }
}
