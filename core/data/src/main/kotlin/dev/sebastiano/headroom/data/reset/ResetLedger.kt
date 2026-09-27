package dev.sebastiano.headroom.data.reset

import android.content.Context
import androidx.core.content.edit

/**
 * Remembers which reset occurrences already produced a notification. A reset can be checked twice
 * (an alarm and a hand-over after a replan), and the user must hear about it once.
 */
internal interface ResetLedger {
    fun wasNotified(alarm: ResetAlarm): Boolean

    fun markNotified(alarm: ResetAlarm)
}

internal class SharedPreferencesResetLedger(context: Context) : ResetLedger {
    private val store = context.getSharedPreferences("reset_ledger", Context.MODE_PRIVATE)

    override fun wasNotified(alarm: ResetAlarm): Boolean =
        store.getLong(key(alarm), Long.MIN_VALUE) == alarm.expectedResetAt.toEpochMilli()

    override fun markNotified(alarm: ResetAlarm) {
        store.edit { putLong(key(alarm), alarm.expectedResetAt.toEpochMilli()) }
    }

    private fun key(alarm: ResetAlarm) = "notified:${alarm.requestCode}"
}
