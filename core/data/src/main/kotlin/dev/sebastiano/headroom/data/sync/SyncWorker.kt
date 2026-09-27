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
import java.time.Duration

/** Refreshes every account. Runs every 15 minutes, the shortest period WorkManager allows. */
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
        private const val UNIQUE_NAME = "quota-sync"
        private val PERIOD: Duration = Duration.ofMinutes(15)

        fun schedulePeriodic(context: Context) {
            val request =
                PeriodicWorkRequestBuilder<SyncWorker>(PERIOD)
                    .setConstraints(
                        Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                    )
                    .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
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
