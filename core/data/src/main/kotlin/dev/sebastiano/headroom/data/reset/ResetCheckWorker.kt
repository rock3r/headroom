package dev.sebastiano.headroom.data.reset

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlin.time.Duration
import kotlin.time.Instant
import kotlin.time.toJavaDuration

/** Checks one reset, and re-enqueues itself with a delay while the reset has not shown up yet. */
internal class ResetCheckWorker(
    context: Context,
    params: WorkerParameters,
    private val checker: ResetChecker,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val alarm = inputData.toAlarm() ?: return Result.failure()
        val attempt = inputData.getInt(KEY_ATTEMPT, 1)
        when (val outcome = checker.check(alarm, attempt)) {
            is ResetCheckOutcome.Retry ->
                enqueue(applicationContext, alarm, attempt + 1, outcome.after)
            ResetCheckOutcome.Notified,
            ResetCheckOutcome.GaveUp -> Unit
        }
        return Result.success()
    }

    companion object {
        private const val KEY_ACCOUNT = "account"
        private const val KEY_WINDOW = "window"
        private const val KEY_TRIGGER = "trigger"
        private const val KEY_EXPECTED = "expected"
        private const val KEY_USED = "used"
        private const val KEY_ATTEMPT = "attempt"

        fun enqueue(
            context: Context,
            alarm: ResetAlarm,
            attempt: Int,
            delay: Duration?,
            policy: ExistingWorkPolicy = ExistingWorkPolicy.REPLACE,
        ) {
            val builder =
                OneTimeWorkRequestBuilder<ResetCheckWorker>()
                    .setInputData(
                        workDataOf(
                            KEY_ACCOUNT to alarm.accountId,
                            KEY_WINDOW to alarm.windowId,
                            KEY_TRIGGER to alarm.triggerAt.toEpochMilliseconds(),
                            KEY_EXPECTED to alarm.expectedResetAt.toEpochMilliseconds(),
                            KEY_USED to alarm.usedBefore,
                            KEY_ATTEMPT to attempt,
                        )
                    )
            if (delay == null) {
                builder.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            } else {
                builder.setInitialDelay(delay.toJavaDuration())
            }
            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    "reset-${alarm.requestCode}",
                    policy,
                    builder.build(),
                )
        }

        private fun Data.toAlarm(): ResetAlarm? {
            val account = getString(KEY_ACCOUNT) ?: return null
            val window = getString(KEY_WINDOW) ?: return null
            return ResetAlarm(
                accountId = account,
                windowId = window,
                triggerAt = Instant.fromEpochMilliseconds(getLong(KEY_TRIGGER, 0)),
                expectedResetAt = Instant.fromEpochMilliseconds(getLong(KEY_EXPECTED, 0)),
                usedBefore = getDouble(KEY_USED, 0.0),
            )
        }
    }
}
