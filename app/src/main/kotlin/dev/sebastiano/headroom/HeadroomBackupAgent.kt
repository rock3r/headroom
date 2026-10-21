package dev.sebastiano.headroom

import android.app.backup.BackupAgent
import android.app.backup.BackupDataInput
import android.app.backup.BackupDataOutput
import android.os.ParcelFileDescriptor
import dev.sebastiano.headroom.data.DataGraph
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking

/**
 * Tier 1 (background) credential restoration: runs during device setup right after Android finishes
 * restoring the app's files, unlocking the backed-up credential keyset and rescheduling background
 * sync and reset notifications before the user opens the app.
 */
class HeadroomBackupAgent
@JvmOverloads
constructor(
    private val restore: (suspend () -> Unit)? = null,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : BackupAgent() {
    override fun onBackup(
        oldState: ParcelFileDescriptor?,
        data: BackupDataOutput?,
        newState: ParcelFileDescriptor?,
    ) {
        // Full backup is used (android:fullBackupOnly="true").
    }

    override fun onRestore(
        data: BackupDataInput?,
        appVersionCode: Int,
        newState: ParcelFileDescriptor?,
    ) {
        // Full backup calls onRestoreFinished instead of onRestore.
    }

    override fun onRestoreFinished() {
        super.onRestoreFinished()
        runBlocking {
            val action = restore ?: ::restoreFromContext
            action()
        }
    }

    private suspend fun restoreFromContext() {
        val app = applicationContext as? HeadroomApplication
        if (app != null) {
            app.onRestoreFinished()
            return
        }
        // Android Auto Backup runs BackupAgent in restricted mode and instantiates the base
        // Application class rather than HeadroomApplication.
        val job = SupervisorJob()
        val scope = CoroutineScope(job + dispatcher)
        val dataGraph = DataGraph(this, scope)
        try {
            dataGraph.onRestoreFinished()
        } finally {
            dataGraph.close()
            job.cancelAndJoin()
        }
    }
}
