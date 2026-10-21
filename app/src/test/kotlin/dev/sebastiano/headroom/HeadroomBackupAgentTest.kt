package dev.sebastiano.headroom

import android.app.Application
import android.app.backup.BackupAgent
import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.delay
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HeadroomBackupAgentTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `onRestoreFinished runs credential restoration synchronously before returning`() {
        var completedSynchronously = false
        val agent =
            HeadroomBackupAgent(
                restore = {
                    delay(10)
                    completedSynchronously = true
                }
            )

        agent.onRestoreFinished()

        assertTrue(completedSynchronously)
    }

    @Test
    fun `onRestoreFinished initializes restoration from a base Application in restricted backup mode`() {
        // Android Auto Backup runs BackupAgent in restricted mode with the base Application class
        // rather than HeadroomApplication.
        val restrictedApp =
            object : Application() {
                init {
                    attachBaseContext(context)
                }

                override fun getApplicationContext(): Context = this
            }
        val agent =
            HeadroomBackupAgent().apply {
                BackupAgent::class
                    .java
                    .getMethod("attach", Context::class.java)
                    .invoke(
                        this,
                        restrictedApp,
                    )
            }

        assertFalse(agent.applicationContext is HeadroomApplication)

        agent.onRestoreFinished()

        val scheduledSync =
            WorkManager.getInstance(restrictedApp).getWorkInfosForUniqueWork("quota-sync").get()
        assertTrue(scheduledSync.isNotEmpty())
    }

    @Test
    fun `manifest enables full backup with HeadroomBackupAgent`() {
        val appInfo = context.applicationInfo
        assertTrue((appInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP) != 0)
        assertTrue((appInfo.flags and ApplicationInfo.FLAG_FULL_BACKUP_ONLY) != 0)
        assertEquals(HeadroomBackupAgent::class.java.name, appInfo.backupAgentName)
    }
}
