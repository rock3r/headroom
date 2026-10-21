package dev.sebastiano.headroom

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.test.core.app.ApplicationProvider
import kotlin.test.Test
import kotlin.test.assertEquals
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
    fun `manifest enables full backup with HeadroomBackupAgent`() {
        val appInfo = context.applicationInfo
        assertTrue((appInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP) != 0)
        assertTrue((appInfo.flags and ApplicationInfo.FLAG_FULL_BACKUP_ONLY) != 0)
        assertEquals(HeadroomBackupAgent::class.java.name, appInfo.backupAgentName)
    }
}
