package dev.sebastiano.headroom.data.reset

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters

/** Fires when a reset reminder may be due, and hands the check to [ResetReminderWorker]. */
internal class ResetReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ResetReminderWorker.enqueue(context)
    }
}

/** Refreshes the accounts whose resets expire soon, and posts the day's reminder. */
internal class ResetReminderWorker(
    context: Context,
    params: WorkerParameters,
    private val reminders: ResetReminders,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        reminders.remind()
        return Result.success()
    }

    companion object {
        private const val NAME = "reset-reminder"

        fun enqueue(context: Context) {
            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    NAME,
                    // A check that is already running posts the reminder; a second would find
                    // nothing left to post.
                    ExistingWorkPolicy.KEEP,
                    OneTimeWorkRequestBuilder<ResetReminderWorker>()
                        .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                        .build(),
                )
        }
    }
}
