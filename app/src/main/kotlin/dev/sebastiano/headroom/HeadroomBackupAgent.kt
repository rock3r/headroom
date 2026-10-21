package dev.sebastiano.headroom

import android.app.backup.BackupAgent
import android.app.backup.BackupDataInput
import android.app.backup.BackupDataOutput
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.runBlocking

/**
 * Tier 1 (background) credential restoration: runs during device setup right after Android finishes
 * restoring the app's files, unlocking the backed-up credential keyset and rescheduling background
 * sync and reset notifications before the user opens the app.
 */
class HeadroomBackupAgent
@JvmOverloads
constructor(private val restore: (suspend () -> Unit)? = null) : BackupAgent() {
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
            val action =
                restore
                    ?: (applicationContext as? HeadroomApplication)?.let { it::onRestoreFinished }
            action?.invoke()
        }
    }
}
