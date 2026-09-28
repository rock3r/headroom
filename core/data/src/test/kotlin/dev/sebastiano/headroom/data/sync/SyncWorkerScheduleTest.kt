package dev.sebastiano.headroom.data.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import dev.sebastiano.headroom.model.SyncFrequency
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SyncWorkerScheduleTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
    }

    private fun syncWork(): List<WorkInfo> =
        WorkManager.getInstance(context).getWorkInfosForUniqueWork(SyncWorker.UNIQUE_NAME).get()

    private fun activeSyncWork() = syncWork().filterNot { it.state.isFinished }

    @Test
    fun `each frequency schedules the sync with its period`() {
        SyncWorker.schedule(context, SyncFrequency.Hours3)

        val work = activeSyncWork().single()
        assertEquals(
            Duration.ofHours(3).toMillis(),
            work.periodicityInfo?.repeatIntervalMillis,
        )
    }

    @Test
    fun `changing the frequency updates the same periodic work`() {
        SyncWorker.schedule(context, SyncFrequency.Minutes15)
        val before = activeSyncWork().single()

        SyncWorker.schedule(context, SyncFrequency.Hour1)

        val after = activeSyncWork().single()
        assertEquals(before.id, after.id)
        assertEquals(Duration.ofHours(1).toMillis(), after.periodicityInfo?.repeatIntervalMillis)
    }

    @Test
    fun `only when the app opens cancels the periodic sync`() {
        SyncWorker.schedule(context, SyncFrequency.Minutes30)

        SyncWorker.schedule(context, SyncFrequency.OnOpen)

        assertTrue(activeSyncWork().isEmpty())
    }
}
