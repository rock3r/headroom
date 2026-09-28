package dev.sebastiano.headroom.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import dev.sebastiano.headroom.data.reset.ResetCheckWorker
import dev.sebastiano.headroom.data.reset.ResetChecker
import dev.sebastiano.headroom.model.QuotaRepository
import dev.sebastiano.headroom.model.SyncFrequency

/**
 * Refreshes every account. Runs on the period the user picked in the settings, at least every 15
 * minutes, the shortest period WorkManager allows.
 */
internal class SyncWorker(
    context: Context,
    params: WorkerParameters,
    private val repository: QuotaRepository,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        repository.refresh()
        return Result.success()
    }

    companion object {
        const val UNIQUE_NAME = "quota-sync"

        /**
         * Runs the sync every [frequency]'s period, or cancels it when the user only wants to sync
         * on opening the app. An existing sync is updated in place, so it keeps its schedule when
         * the period does not change.
         */
        fun schedule(context: Context, frequency: SyncFrequency) {
            val workManager = WorkManager.getInstance(context)
            val period = frequency.period
            if (period == null) {
                workManager.cancelUniqueWork(UNIQUE_NAME)
                return
            }
            val request =
                PeriodicWorkRequestBuilder<SyncWorker>(period)
                    .setConstraints(
                        Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                    )
                    .build()
            workManager.enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }
    }
}

internal class HeadroomWorkerFactory(
    private val resetChecker: ResetChecker,
    private val repository: QuotaRepository,
) : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? =
        when (workerClassName) {
            ResetCheckWorker::class.java.name ->
                ResetCheckWorker(appContext, workerParameters, resetChecker)
            SyncWorker::class.java.name -> SyncWorker(appContext, workerParameters, repository)
            else -> null
        }
}
