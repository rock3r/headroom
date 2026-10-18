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
import androidx.work.workDataOf
import kotlin.time.Duration
import kotlin.time.toJavaDuration

/** Fires when a reset reminder may be due, and hands the check to [ResetReminderWorker]. */
internal class ResetReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ResetReminderWorker.enqueue(context)
    }
}

/**
 * Refreshes the accounts whose resets expire soon, and posts the day's reminder. It re-enqueues
 * itself with a delay while the refresh cannot reach the provider.
 */
internal class ResetReminderWorker(
    context: Context,
    params: WorkerParameters,
    private val reminders: ResetReminders,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val attempt = inputData.getInt(KEY_ATTEMPT, 1)
        when (val outcome = reminders.remind(attempt)) {
            is ReminderOutcome.Retry -> retry(applicationContext, attempt + 1, outcome.after)
            ReminderOutcome.Done -> Unit
        }
        return Result.success()
    }

    companion object {
        private const val NAME = "reset-reminder"
        private const val KEY_ATTEMPT = "attempt"

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

        private fun retry(context: Context, attempt: Int, delay: Duration) {
            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    NAME,
                    // Runs after this check ends. An alarm that fires meanwhile finds it pending
                    // and keeps it, so the retry count is not lost.
                    ExistingWorkPolicy.APPEND_OR_REPLACE,
                    OneTimeWorkRequestBuilder<ResetReminderWorker>()
                        .setInputData(workDataOf(KEY_ATTEMPT to attempt))
                        .setInitialDelay(delay.toJavaDuration())
                        .build(),
                )
        }
    }
}
